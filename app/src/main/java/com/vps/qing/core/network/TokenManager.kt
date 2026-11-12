package com.vps.qing.core.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secureStore: DataStore<Preferences> by preferencesDataStore(name = "qingvps_secure")

/**
 * JWT 存储：AndroidKeyStore AES/GCM 加密后写入 DataStore，不落明文。
 * cachedToken 供 OkHttp 拦截器同步读取；401 时同步清空内存并异步擦除磁盘。
 */
class TokenManager(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "qingvps_jwt_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
        private val TOKEN_KEY = stringPreferencesKey("jwt_enc")
    }

    @Volatile
    var cachedToken: String? = null
        private set

    init {
        // 启动阶段同步读一次，保证首个请求拦截器就能带上 token
        runBlocking {
            val encrypted = context.secureStore.data.first()[TOKEN_KEY]
            cachedToken = encrypted?.let { decrypt(it) }
        }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String? = runCatching {
        val raw = Base64.decode(payload, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(raw.copyOfRange(IV_SIZE, raw.size)), Charsets.UTF_8)
    }.getOrNull()

    suspend fun save(token: String) {
        cachedToken = token
        context.secureStore.edit { it[TOKEN_KEY] = encrypt(token) }
    }

    suspend fun clear() {
        cachedToken = null
        context.secureStore.edit { it.remove(TOKEN_KEY) }
    }

    /** 供 OkHttp 拦截器（非协程上下文）调用的同步清理。 */
    fun clearImmediate() {
        cachedToken = null
        runBlocking { context.secureStore.edit { it.remove(TOKEN_KEY) } }
    }
}
