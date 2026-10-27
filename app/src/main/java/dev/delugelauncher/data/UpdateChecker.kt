// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Why a check came back with no answer. One per thing that actually goes wrong, because
 * "couldn't check" on its own is a dead end for whoever reads it.
 */
/**
 * The answer to "may the launcher look at the release page on its own?", asked once on first
 * launch. [UNASKED] is the only state that shows the question; nothing is requested before it is
 * answered.
 */
enum class UpdateConsent { UNASKED, ALLOWED, DENIED }

enum class UpdateError { DNS, SOCKET, TIMEOUT, TLS, SERVER, UNEXPECTED }

/** What one look at the release page found. */
sealed interface UpdateResult {
    /** A release newer than the one running. */
    data class Available(val version: Int, val tag: String) : UpdateResult

    /** The release page holds nothing newer. */
    data class UpToDate(val version: Int) : UpdateResult

    /**
     * @param code the HTTP status, meaningful for [UpdateError.SERVER] and 0 otherwise.
     * @param blockedBySystem the phone refused the connection itself rather than the network
     *   being out of reach — see [UpdateChecker.isRestrictedNetworkingMode]. Worth saying
     *   apart: nothing about the request, the app or the connection would explain this one.
     */
    data class Failed(
        val reason: UpdateError,
        val code: Int = 0,
        val blockedBySystem: Boolean = false,
    ) : UpdateResult
}

/**
 * The version check: one read of the project's release page, and nothing else.
 *
 * This is the only place in the app that touches the network, which is why it is one small
 * file rather than a dependency. It reads; it never sends. It also never throws: a check that
 * fails is an answer too, and one the screen can explain.
 */
object UpdateChecker {
    /** Where releases are published. Opened by the browser, never fetched by us. */
    const val RELEASES_URL = "https://github.com/busybin/DelugeLauncher/releases/latest"

    private const val API_URL =
        "https://api.github.com/repos/busybin/DelugeLauncher/releases/latest"

    /** Both ends of one request. A check still running after this is not worth waiting for. */
    private const val TIMEOUT_MS = 15_000

    /** How long to wait before the one second try. */
    private const val RETRY_DELAY_MS = 1_200L

    /** Tag on the one line this writes when it fails, for a bug report to carry. */
    private const val TAG = "DelugeUpdate"

    /**
     * Asks the release page for the newest release and reads it against [currentVersion].
     *
     * The first attempt is tried twice over for the things that a second one can fix — a
     * handshake that took too long, a resolver that missed once. Not for an answer the phone
     * itself refused, which will be refused just as firmly a second time.
     *
     * @param userAgent must name the app — GitHub refuses a request that does not say who made it.
     */
    suspend fun check(context: Context, currentVersion: Int, userAgent: String): UpdateResult {
        val first = attempt(context, currentVersion, userAgent)
        val worthRetrying = first is UpdateResult.Failed &&
            !first.blockedBySystem && first.reason != UpdateError.SERVER
        if (!worthRetrying) return first
        delay(RETRY_DELAY_MS)
        return attempt(context, currentVersion, userAgent)
    }

    private suspend fun attempt(
        context: Context,
        currentVersion: Int,
        userAgent: String,
    ): UpdateResult = withContext(Dispatchers.IO) {
        val connection = runCatching { URL(API_URL).openConnection() as HttpURLConnection }
            .getOrElse { return@withContext fail(UpdateError.UNEXPECTED, 0, context) }
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", userAgent)
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext fail(UpdateError.SERVER, code, context)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(body, currentVersion)
        } catch (e: SocketTimeoutException) {
            fail(UpdateError.TIMEOUT, 0, context, e)
        } catch (e: UnknownHostException) {
            // The resolver itself came back with nothing. First thing a blocked socket looks
            // like, which is why it is asked about below rather than believed on its own.
            fail(UpdateError.DNS, 0, context, e)
        } catch (e: SSLException) {
            fail(UpdateError.TLS, 0, context, e)
        } catch (e: SocketException) {
            fail(UpdateError.SOCKET, 0, context, e)
        } catch (e: IOException) {
            fail(UpdateError.SOCKET, 0, context, e)
        } catch (e: Exception) {
            fail(UpdateError.UNEXPECTED, 0, context, e)
        } finally {
            connection.disconnect()
        }
    }

    private fun fail(
        reason: UpdateError,
        code: Int,
        context: Context,
        cause: Throwable? = null,
    ): UpdateResult {
        // Only the two that a refused socket produces can be one: a timeout is a timeout, and
        // an HTTP status came from a server that was plainly reachable.
        val blocked = reason == UpdateError.DNS || reason == UpdateError.SOCKET
        val blockedBySystem = blocked && isRestrictedNetworkingMode(context)
        if (cause != null) {
            Log.w(TAG, "release check failed: $reason http=$code blocked=$blockedBySystem " +
                "${cause.javaClass.simpleName}: ${cause.message}")
        } else {
            Log.w(TAG, "release check failed: $reason http=$code blocked=$blockedBySystem")
        }
        return UpdateResult.Failed(reason, code, blockedBySystem)
    }

    /**
     * Whether this phone has Android's "restricted networking mode" switched on.
     *
     * A device-wide switch, off on any ordinary phone and settable only over adb or by the
     * platform. While it is on, the system keeps a list of the apps it considers network users
     * — built from the apps that were installed *with* `INTERNET` — and refuses every socket
     * from any other app, in the background and in the foreground alike, DNS included. An app
     * that gained `INTERNET` in an update rather than a fresh install is exactly the app that
     * list misses, and the symptom is nothing but "this one app cannot connect" while the rest
     * of the phone is fine. Reading the setting is open to any app; writing it is not.
     */
    fun isRestrictedNetworkingMode(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, "restricted_networking_mode", 0) != 0
    }.getOrDefault(false)

    /**
     * The answer in [body], read without a network so the parsing can be tested on its own.
     *
     * A body this cannot make sense of is [UpdateError.UNEXPECTED] rather than an exception:
     * GitHub answering something new is not a reason for the launcher to fall over.
     */
    fun parse(body: String, currentVersion: Int): UpdateResult = runCatching {
        val tag = JSONObject(body).optString("tag_name")
        val version = versionFromTag(tag)
            ?: return@runCatching UpdateResult.Failed(UpdateError.UNEXPECTED)
        if (version > currentVersion) UpdateResult.Available(version, tag)
        else UpdateResult.UpToDate(version)
    }.getOrElse { UpdateResult.Failed(UpdateError.UNEXPECTED) }

    /** "v22" and "22" both read as 22; anything else is no version at all. */
    fun versionFromTag(tag: String): Int? =
        tag.trim().removePrefix("v").removePrefix("V").toIntOrNull()
}
