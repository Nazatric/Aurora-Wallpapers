package com.auroro.wallpapers.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores user-supplied provider tokens encrypted with an AES-256 key held by AndroidKeyStore.
 * The key is device-bound, never leaves the keystore, and is not included in backups.
 *
 * This protects a user's own token at rest on their device. It does not make a client-side credential
 * suitable for a shared public developer account (which is why Unsplash remains disabled).
 */
class SecureSecretStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("provider_secrets", Context.MODE_PRIVATE)

    @Synchronized
    fun put(name: String, value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(name).commit()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)
        check(prefs.edit().putString(name, encoded).commit()) { "Could not save the encrypted provider key." }
    }

    @Synchronized
    fun get(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= IV_LENGTH) return null
            val iv = packed.copyOfRange(0, IV_LENGTH)
            val encrypted = packed.copyOfRange(IV_LENGTH, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            // The user may have restored app data to a different device; a keystore-bound key is not restorable.
            prefs.edit().remove(name).apply()
            null
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "auroro_provider_secret_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
        const val ABYSS_KEY = "abyss_api_key"
    }
}
