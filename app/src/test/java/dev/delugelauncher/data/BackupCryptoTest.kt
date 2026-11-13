// SPDX-License-Identifier: GPL-3.0-or-later
package dev.delugelauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupCryptoTest {

    private val password = "correct horse".toCharArray()

    @Test
    fun `an envelope opens with its own password`() {
        val plain = """{"favorites":["com.example.notes/com.example.notes.Main"]}"""
        val sealed = BackupCrypto.encrypt(plain, password)
        assertTrue(BackupCrypto.isEncrypted(sealed))
        assertEquals(plain, BackupCrypto.decrypt(sealed, password))
    }

    @Test
    fun `a wrong password opens nothing`() {
        val sealed = BackupCrypto.encrypt("{}", password)
        assertNull(BackupCrypto.decrypt(sealed, "wrong".toCharArray()))
    }

    @Test
    fun `a changed byte opens nothing`() {
        val sealed = BackupCrypto.encrypt("{}", password)
        val tampered = sealed.dropLast(2) + if (sealed.endsWith("AA")) "BB" else "AA"
        assertNull(BackupCrypto.decrypt(tampered, password))
    }

    @Test
    fun `plain text is not mistaken for an envelope`() {
        assertFalse(BackupCrypto.isEncrypted("{}"))
    }

    @Test
    fun `new exports name their round count`() {
        // The parameter is inside the envelope so a future bump can still read this file.
        assertTrue(BackupCrypto.encrypt("{}", password).startsWith("deluge2:600000:"))
    }

    @Test
    fun `a deluge1 file still opens`() {
        val plain = """{"favorites":["com.example.notes/com.example.notes.Main"]}"""
        assertEquals(plain, BackupCrypto.decrypt(legacyEnvelope(plain, password), password))
    }

    /** The format before the parameter field existed: 120,000 rounds and four parts. */
    private fun legacyEnvelope(plain: String, password: CharArray): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val spec = PBEKeySpec(password, salt, 120_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, iv),
        )
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return listOf(
            "deluge1",
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(sealed),
        ).joinToString(":")
    }
}
