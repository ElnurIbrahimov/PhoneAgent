package com.phoneagent.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreSecretStore(context: Context) : SecretStore {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    init {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey()
        }
    }

    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)

        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey {
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    override suspend fun storeSecret(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        prefs.edit().apply {
            putString("$key.iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            putString("$key.data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            apply()
        }
    }

    override suspend fun getSecret(key: String): String? {
        val ivBase64 = prefs.getString("$key.iv", null) ?: return null
        val dataBase64 = prefs.getString("$key.data", null) ?: return null

        return try {
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val encrypted = Base64.decode(dataBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (_: Exception) {
            deleteStoredValue(key)
            null
        }
    }

    override suspend fun deleteSecret(key: String) {
        deleteStoredValue(key)
    }

    private fun deleteStoredValue(key: String) {
        prefs.edit().apply {
            remove("$key.iv")
            remove("$key.data")
            apply()
        }
    }

    override suspend fun hasSecret(key: String): Boolean {
        return prefs.contains("$key.iv") && prefs.contains("$key.data")
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "phoneagent_secret_key"
        private const val PREFS_NAME = "phoneagent_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
