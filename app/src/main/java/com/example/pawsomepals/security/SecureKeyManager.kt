package com.example.pawsomepals.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureKeyManager(private val context: Context) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyAlias = "OpenAIApiKey"
    private val sharedPrefsName = "SecurePrefs"
    private val keyPrefName = "EncryptedApiKey"

    fun saveApiKey(apiKey: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encryptedBytes = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)

        val iv = Base64.encodeToString(cipher.iv, Base64.DEFAULT)

        context.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE).edit()
            .putString(keyPrefName, "$encryptedBase64:$iv")
            .apply()
    }

    fun getApiKey(): String? {
        val encryptedData = context.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE)
            .getString(keyPrefName, null) ?: return null

        val (encryptedBase64, ivBase64) = encryptedData.split(":")
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val iv = Base64.decode(ivBase64, Base64.DEFAULT)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        keyStore.getEntry(keyAlias, null)?.let { return (it as KeyStore.SecretKeyEntry).secretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
}