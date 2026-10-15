package com.noxxx.guard.policy

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted PBKDF2 hashes for the partner password and app PIN. Format: "salt:hash" (base64). */
object Secret {
    private const val ITERATIONS = 60_000
    private const val KEY_BITS = 256

    fun hash(secret: String): String {
        require(secret.isNotEmpty()) { "secret must not be empty" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val enc = Base64.getEncoder()
        return enc.encodeToString(salt) + ":" + enc.encodeToString(derive(secret, salt))
    }

    fun verify(secret: String, stored: String?): Boolean {
        // Android's PBKDF2 rejects empty passwords with an exception; an empty entry is simply wrong.
        if (stored == null || secret.isEmpty()) return false
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val dec = Base64.getDecoder()
        val salt = runCatching { dec.decode(parts[0]) }.getOrNull() ?: return false
        val expected = runCatching { dec.decode(parts[1]) }.getOrNull() ?: return false
        val actual = runCatching { derive(secret, salt) }.getOrNull() ?: return false
        return MessageDigest.isEqual(actual, expected)
    }

    private fun derive(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
