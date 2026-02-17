package com.starter.springkotlintemplate.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class EncryptionService(
    @Value("\${app.encryption-key-base64}") private val keyBase64: String
) {
    private val key = SecretKeySpec(Base64.getDecoder().decode(keyBase64), "AES")

    fun encrypt(plaintext: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray())

        // Format: [IV][CIPHERTEXT]
        return iv + ciphertext
    }

    fun decrypt(payload: ByteArray): String {
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes)
    }
}