package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 安全中心发码请求体：站点 js/account.js 的 sendPhoneCode/sendEmailCode，
 * 并用匿名 POST 实测过缺少 captcha/token 会返回 400「请输入图形验证码」。
 */
class SecurityBodiesTest {

    @Test
    fun `phone code body carries captcha ticket and numeric country code`() {
        val body = SecurityBodies.code(
            target = "phone",
            action = SecurityBodies.ACTION_UPDATE,
            value = "13862188657",
            captcha = "A1B2",
            captchaToken = "tk-123"
        )
        assertEquals("update", body.get("action").asString)
        assertEquals("13862188657", body.get("phone").asString)
        assertEquals(86, body.get("phone_code").asInt)
        assertTrue(body.get("phone_code").isJsonPrimitive)
        assertEquals("A1B2", body.get("captcha").asString)
        assertEquals("tk-123", body.get("token").asString)
        assertFalse(body.has("email"))
        assertFalse(body.has("scene"))
    }

    @Test
    fun `email code body carries captcha ticket`() {
        val body = SecurityBodies.code(
            target = "email",
            action = SecurityBodies.ACTION_VERIFY,
            value = "user@example.com",
            captcha = "9Z",
            captchaToken = "tk-9"
        )
        assertEquals("verify", body.get("action").asString)
        assertEquals("user@example.com", body.get("email").asString)
        assertEquals("9Z", body.get("captcha").asString)
        assertEquals("tk-9", body.get("token").asString)
        assertFalse(body.has("phone"))
        assertFalse(body.has("phone_code"))
    }

    @Test
    fun `site disabled captcha still sends empty fields`() {
        // 站点关闭开关时，凭证为空串，服务端同样不会校验；字段必须存在以对齐协议
        val body = SecurityBodies.code("phone", SecurityBodies.ACTION_VERIFY, "13800000000")
        assertTrue(body.has("captcha"))
        assertTrue(body.has("token"))
        assertEquals("", body.get("captcha").asString)
        assertEquals("", body.get("token").asString)
    }

    @Test
    fun `actions match site account js`() {
        assertEquals("verify", SecurityBodies.ACTION_VERIFY)
        assertEquals("update", SecurityBodies.ACTION_UPDATE)
    }

    @Test
    fun `captcha ticket survives json round trip`() {
        val body = SecurityBodies.code("phone", "update", "13800000000", "86", "abcd", "tk")
        val parsed = JsonParser.parseString(body.toString()).asJsonObject
        assertEquals("abcd", parsed.get("captcha").asString)
        assertEquals("tk", parsed.get("token").asString)
    }
}
