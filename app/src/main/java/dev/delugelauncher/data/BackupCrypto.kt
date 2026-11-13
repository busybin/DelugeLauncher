// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The password envelope around an exported settings file.
 *
 * An export names every app the owner arranged, which apps are hidden and which are locked;
 * on its own it is a plain text map of the phone. This turns it into one opaque string with a
 * password, and refuses to open it with anything else or after a byte changed.
 *
 * The format is `deluge2:<iterations>:<salt>:<iv>:<ciphertext>`, all base64. The version is
 * first so a later change can refuse an old envelope instead of misreading it. Older
 * `deluge1` files still open with their fixed iteration count.
 */
object BackupCrypto {
    private const val PREFIX = "deluge2"
    private const val LEGACY_PREFIX = "deluge1"
    private const val ITERATIONS = 600_000
    private const val LEGACY_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun isEncrypted(text: String): Boolean =
        text.startsWith("$PREFIX:") || text.startsWith("$LEGACY_PREFIX:")

    /** Encrypts [plain] into one line that can sit in a file or a share sheet. */
    fun encrypt(plain: String, password: CharArray, random: SecureRandom = SecureRandom()): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(derive(password, salt), "AES"),
            GCMParameterSpec(TAG_BITS, iv),
        )
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return listOf(
            PREFIX,
            ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(sealed),
        ).joinToString(":")
    }

    /** The plain text, or null when the password is wrong or the envelope was altered. */
    fun decrypt(envelope: String, password: CharArray): String? {
        val parts = envelope.trim().split(':')
        val iterations: Int
        val saltPart: String
        val ivPart: String
        val sealedPart: String
        when {
            parts.size == 5 && parts[0] == PREFIX -> {
                iterations = parts[1].toIntOrNull()?.takeIf { it in 10_000..2_000_000 }
                    ?: return null
                saltPart = parts[2]
                ivPart = parts[3]
                sealedPart = parts[4]
            }
            // The old envelope had no parameter field; it always used 120,000 rounds.
            parts.size == 4 && parts[0] == LEGACY_PREFIX -> {
                iterations = LEGACY_ITERATIONS
                saltPart = parts[1]
                ivPart = parts[2]
                sealedPart = parts[3]
            }
            else -> return null
        }
        return runCatching {
            val salt = Base64.getDecoder().decode(saltPart)
            val iv = Base64.getDecoder().decode(ivPart)
            val sealed = Base64.getDecoder().decode(sealedPart)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(derive(password, salt, iterations), "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        }.getOrNull()
    }

    internal fun derive(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password, salt, iterations, KEY_BITS))
            .encoded
}
