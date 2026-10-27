// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.ui.common

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import dev.delugelauncher.R

/**
 * Asks for the device lock, then runs [onUnlocked].
 *
 * A phone with biometrics gets the system's own sheet, which takes a fingerprint or the screen
 * lock; older phones get the credential screen. A phone with no secure lock at all opens the
 * app rather than making it unreachable.
 *
 * One prompt at a time: a second tap while a sheet is up is dropped, since handing it the
 * first answer would open the wrong app.
 */
@Composable
fun rememberAppLockRequest(): (label: String, onUnlocked: () -> Unit) -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val keyguard = remember(context) { context.getSystemService(KeyguardManager::class.java) }
    val title = stringResource(R.string.app_lock_title)
    val prompt = stringResource(R.string.app_lock_prompt)

    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var prompting by remember { mutableStateOf(false) }
    val credential = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val action = pending
        pending = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
    }

    return remember(context, activity, keyguard, title, prompt) {
        { label, onUnlocked ->
            val subtitle = prompt.format(label)
            // The credential screen is the floor: it is there on every phone that has a
            // secure lock, and it is also what a failed biometric sheet falls back to.
            fun askForCredential() {
                val intent = runCatching {
                    keyguard?.createConfirmDeviceCredentialIntent(title, subtitle)
                }.getOrNull()
                if (intent == null) {
                    onUnlocked()
                } else {
                    pending = onUnlocked
                    credential.launch(intent)
                }
            }
            when {
                keyguard?.isDeviceSecure != true -> onUnlocked()
                prompting || pending != null -> Unit
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && activity != null -> {
                    prompting = true
                    val shown = runCatching {
                        showBiometric(activity, title, subtitle, onUnlocked) { prompting = false }
                    }.getOrDefault(false)
                    if (!shown) {
                        prompting = false
                        askForCredential()
                    }
                }
                else -> askForCredential()
            }
        }
    }
}

/**
 * The system's own sheet: fingerprint first, screen lock as the way out of a lockout.
 *
 * [onFinished] runs on the main thread before [onUnlocked], so composition state may be
 * touched in either.
 */
@RequiresApi(Build.VERSION_CODES.R)
private fun showBiometric(
    activity: Activity,
    title: String,
    subtitle: String,
    onUnlocked: () -> Unit,
    onFinished: () -> Unit,
): Boolean {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt.Builder(activity)
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    prompt.authenticate(
        CancellationSignal(),
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                onFinished()
                onUnlocked()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                onFinished()
            }

            override fun onAuthenticationFailed() {
                // A finger that did not match: the sheet stays up for another try.
            }
        },
    )
    return true
}

/** The Activity behind a Context, which a Compose `LocalContext` is wrapped inside. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
