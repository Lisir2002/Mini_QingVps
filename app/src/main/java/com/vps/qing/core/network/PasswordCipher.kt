package com.vps.qing.core.network

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 站点登录密码加密，协议来自 login.js 的 encrypt()：
 *
 * ```
 * const key = CryptoJS.enc.Utf8.parse("idcsmart.finance");  // 16 bytes -> AES-128
 * const iv  = CryptoJS.enc.Utf8.parse("9311019310287172");
 * CryptoJS.AES.encrypt(str, key, { mode: CBC, padding: Pkcs7, iv }).toString();
 * ```
 *
 * key 是 WordArray 而非口令字符串，因此没有 OpenSSL salt 头，输出等价于
 * 标准 `AES/CBC/PKCS5Padding` 密文的 base64。已用站点自带 crypto-js.min.js
 * 对 5 组样本做过逐字节交叉验证（见 PasswordCipherTest）。
 *
 * 注意：只有**登录**的密码要加密；注册 / 找回密码的密码是明文（见 AuthBodies）。
 */
object PasswordCipher {
    private const val KEY = "idcsmart.finance"
    private const val IV = "9311019310287172"

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val key = SecretKeySpec(KEY.toByteArray(Charsets.UTF_8), "AES")
        val iv = IvParameterSpec(IV.toByteArray(Charsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipherText)
    }
}
