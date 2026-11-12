package com.vps.qing.data.repository

import com.vps.qing.core.network.PasswordCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 请求体字段名/加密规则来自站点 login.js、regist.js、forget.js 实测。 */
class AuthBodiesTest {

    @Test
    fun `password login body uses account plus encrypted password`() {
        val body = AuthBodies.login("user@example.com", password = "Test@1234")
        assertEquals("password", body.get("type").asString)
        assertEquals("user@example.com", body.get("account").asString)
        assertEquals("86", body.get("phone_code").asString)
        assertEquals("", body.get("code").asString)
        assertEquals("FRl4Z8CEe2oVofUOQqv2+Q==", body.get("password").asString)
        assertEquals("0", body.get("remember_password").asString)
        assertEquals("", body.get("captcha").asString)
        assertEquals("", body.get("token").asString)
    }

    @Test
    fun `code login body leaves password empty`() {
        val body = AuthBodies.login("user@example.com", code = "123456")
        assertEquals("code", body.get("type").asString)
        assertEquals("123456", body.get("code").asString)
        assertEquals("", body.get("password").asString)
    }

    @Test
    fun `remember flag is string one`() {
        assertEquals("1", AuthBodies.login("a@b.com", password = "pw", remember = true)
            .get("remember_password").asString)
    }

    @Test
    fun `login never contains guessed legacy keys`() {
        val body = AuthBodies.login("a@b.com", password = "pw")
        assertFalse(body.has("email"))
        assertFalse(body.has("username"))
        assertFalse(body.has("email_code"))
        assertFalse(body.has("scene"))
    }

    @Test
    fun `register body uses plaintext password and re_password`() {
        val body = AuthBodies.register("a@b.com", "Secret123", "Secret123", "998877")
        assertEquals("email", body.get("type").asString)
        assertEquals("a@b.com", body.get("account").asString)
        assertEquals("998877", body.get("code").asString)
        assertEquals("Secret123", body.get("password").asString)
        assertEquals("Secret123", body.get("re_password").asString)
        assertEquals("", body.get("username").asString)
        assertTrue(body.get("customfield").isJsonObject)
        // 注册密码是明文，不能是登录用的密文
        assertFalse(body.get("password").asString.contains("=="))
        assertFalse(body.has("captcha"))
        assertFalse(body.has("token"))
    }

    @Test
    fun `register password differs from login cipher`() {
        val login = AuthBodies.login("a@b.com", password = "Secret123").get("password").asString
        val register = AuthBodies.register("a@b.com", "Secret123", "Secret123", "1")
            .get("password").asString
        assertEquals(PasswordCipher.encrypt("Secret123"), login)
        assertEquals("Secret123", register)
    }

    @Test
    fun `email code carries action and captcha ticket`() {
        val body = AuthBodies.emailCode("a@b.com", AuthBodies.ACTION_REGISTER, "A1B2", "tk-123")
        assertEquals("register", body.get("action").asString)
        assertEquals("a@b.com", body.get("email").asString)
        assertEquals("A1B2", body.get("captcha").asString)
        assertEquals("tk-123", body.get("token").asString)
        assertFalse(body.has("scene"))
        assertFalse(body.has("type"))
    }

    @Test
    fun `phone code uses phone_code as country code`() {
        val body = AuthBodies.phoneCode("13800000000", AuthBodies.ACTION_LOGIN, "86", "abcd", "tk")
        assertEquals("login", body.get("action").asString)
        assertEquals("86", body.get("phone_code").asString)
        assertEquals("13800000000", body.get("phone").asString)
    }

    @Test
    fun `reset password body matches forget js`() {
        val body = AuthBodies.resetPassword("a@b.com", "654321", "NewPass123")
        assertEquals("email", body.get("type").asString)
        assertEquals("a@b.com", body.get("account").asString)
        assertEquals("654321", body.get("code").asString)
        assertEquals("NewPass123", body.get("password").asString)
        assertEquals("NewPass123", body.get("re_password").asString)
        assertFalse(body.has("email_code"))
    }

    @Test
    fun `actions are the three values accepted by the site`() {
        assertEquals("login", AuthBodies.ACTION_LOGIN)
        assertEquals("register", AuthBodies.ACTION_REGISTER)
        assertEquals("password_reset", AuthBodies.ACTION_PASSWORD_RESET)
    }
}
