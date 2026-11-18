package com.termius.clone.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyVaultManager(private val context: Context? = null) {

    fun encrypt(plainText: String): String = Companion.encrypt(plainText)

    fun decrypt(cipherText: String): String = Companion.decrypt(cipherText)

    companion object {
        const val PREFIX = "ENC:v1:"
        private const val KEY_ALIAS = "TermiusMasterKeyAlias"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private val keyLock = Any()

        private fun loadKeyStoreLocked(): KeyStore {
            return KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        }

        private fun generateKeyLocked() {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }

        private fun ensureKeyExists(keyStore: KeyStore): SecretKey {
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKeyLocked()
            }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            runCatching { keyStore.deleteEntry(KEY_ALIAS) }
            generateKeyLocked()
            return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                ?: throw IllegalStateException("无法创建 AndroidKeyStore 主密钥")
        }

        private fun recreateKeyLocked(keyStore: KeyStore): SecretKey {
            runCatching { keyStore.deleteEntry(KEY_ALIAS) }
            generateKeyLocked()
            return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                ?: throw IllegalStateException("重建 AndroidKeyStore 主密钥失败")
        }

        fun encrypt(plainText: String): String {
            if (plainText.isEmpty()) return ""
            if (plainText.startsWith(PREFIX)) return plainText

            synchronized(keyLock) {
                try {
                    val keyStore = loadKeyStoreLocked()
                    var secretKey = ensureKeyExists(keyStore)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    try {
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    } catch (e: KeyPermanentlyInvalidatedException) {
                        secretKey = recreateKeyLocked(keyStore)
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    }
                    val iv = cipher.iv
                    if (iv == null || iv.isEmpty() || iv.size > 255) {
                        throw IllegalStateException("生成的 GCM IV 长度异常")
                    }
                    val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                    val combined = ByteArray(1 + iv.size + cipherBytes.size)
                    combined[0] = iv.size.toByte()
                    System.arraycopy(iv, 0, combined, 1, iv.size)
                    System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
                    return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
                } catch (e: IllegalStateException) {
                    throw e
                } catch (e: Exception) {
                    throw IllegalStateException("凭据加密失败: ${e.message}", e)
                }
            }
        }

        fun decrypt(cipherText: String): String {
            if (cipherText.isEmpty()) return ""
            if (!cipherText.startsWith(PREFIX)) return cipherText

            synchronized(keyLock) {
                try {
                    val payload = cipherText.removePrefix(PREFIX)
                    val combined = Base64.decode(payload, Base64.NO_WRAP)
                    if (combined.size < 2) {
                        throw IllegalStateException("加密凭据格式损坏：载荷长度不足")
                    }
                    val ivLen = combined[0].toInt() and 0xFF
                    if (ivLen <= 0 || combined.size <= 1 + ivLen) {
                        throw IllegalStateException("加密凭据格式损坏：IV 长度非法 ($ivLen)")
                    }
                    val iv = ByteArray(ivLen)
                    System.arraycopy(combined, 1, iv, 0, ivLen)
                    val cipherBytes = ByteArray(combined.size - 1 - ivLen)
                    System.arraycopy(combined, 1 + ivLen, cipherBytes, 0, cipherBytes.size)

                    val keyStore = loadKeyStoreLocked()
                    val secretKey = ensureKeyExists(keyStore)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    val spec = GCMParameterSpec(128, iv)
                    try {
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    } catch (e: KeyPermanentlyInvalidatedException) {
                        recreateKeyLocked(keyStore)
                        throw IllegalStateException("主密钥已失效并已自动重建，原密文无法解密，请重新录入凭据", e)
                    }
                    val plainBytes = cipher.doFinal(cipherBytes)
                    return String(plainBytes, Charsets.UTF_8)
                } catch (e: IllegalStateException) {
                    throw e
                } catch (e: Exception) {
                    throw IllegalStateException("凭据解密失败: ${e.message}", e)
                }
            }
        }

        fun decryptForUi(cipherText: String): String {
            return try {
                decrypt(cipherText)
            } catch (_: Exception) {
                ""
            }
        }
    }
}
