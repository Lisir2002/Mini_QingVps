package com.vps.qing.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 加密向量来自站点自带 crypto-js.min.js 的 login.js encrypt() 实测输出，
 * 用于保证 App 侧 AES/CBC/PKCS5Padding 与站点逐字节一致。
 */
class PasswordCipherTest {

    @Test
    fun `matches site crypto-js vectors`() {
        val vectors = mapOf(
            "Test@1234" to "FRl4Z8CEe2oVofUOQqv2+Q==",
            "probe" to "WBK3b76XblcVhBS7RCFb6w==",
            "Abc12345!" to "uNOtaW7qxzgnCTpwjvIBjg==",
            "123456" to "okOPBcgQfg0vyigr5xkGrA==",
            "QingVPS#2026" to "fI64+Ctz5PrcLeq52F4/KA=="
        )
        vectors.forEach { (plain, expected) ->
            assertEquals("加密结果不一致: $plain", expected, PasswordCipher.encrypt(plain))
        }
    }

    @Test
    fun `output is deterministic and hides plaintext`() {
        val a = PasswordCipher.encrypt("hunter2")
        val b = PasswordCipher.encrypt("hunter2")
        assertEquals(a, b)
        assertNotEquals("hunter2", a)
    }

    @Test
    fun `chinese and empty input do not crash`() {
        assertEquals(true, PasswordCipher.encrypt("中文密码").isNotBlank())
        assertEquals(true, PasswordCipher.encrypt("").isNotBlank())
    }
}
