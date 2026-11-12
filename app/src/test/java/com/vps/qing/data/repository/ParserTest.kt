package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

    @Test
    fun `captcha parser extracts token and image from real site html`() {
        val html = javaClass.classLoader!!.getResourceAsStream("captcha_sample.html")!!
            .bufferedReader().use { it.readText() }
        val challenge = CaptchaParser.parseHtml(html)
        assertNotNull(challenge)
        assertEquals(32, challenge!!.token.length)
        assertTrue(challenge.imageBase64.length > 500)
        assertTrue(challenge.imageBase64.matches(Regex("[A-Za-z0-9+/=]+")))
    }

    @Test
    fun `captcha parser rejects incomplete html`() {
        assertNull(CaptchaParser.parseHtml("<div>no captcha here</div>"))
        assertNull(CaptchaParser.parseHtml("<img src=\"data:image/png;base64,AAAA\">")) // 缺 token
    }

    @Test
    fun `strip data uri`() {
        assertEquals("AAAA", CaptchaParser.stripDataUri("data:image/png;base64,AAAA"))
        assertNull(CaptchaParser.stripDataUri("data:image/png;base64,"))
        assertNull(CaptchaParser.stripDataUri(null))
    }

    @Test
    fun `site config parses real common switches`() {
        val json = JsonParser.parseString(
            """
            {
              "website_name": "QingVPS",
              "currency_code": "CNY",
              "captcha_client_login": 0,
              "captcha_client_register": 1,
              "captcha_client_verify": 1,
              "captcha_client_password_reset": 1,
              "register_email": 1,
              "register_phone": 0,
              "login_email_password": 1,
              "login_phone_password": 0,
              "login_phone_verify": 0,
              "code_client_email_register": 1,
              "client_strong_password_rule": 1
            }
            """.trimIndent()
        )
        val c = SiteConfigParser.parse(json)
        assertEquals("QingVPS", c.websiteName)
        assertEquals("CNY", c.currencyCode)
        assertEquals(false, c.captchaOnLogin)
        assertEquals(true, c.captchaOnRegister)
        assertEquals(true, c.captchaOnVerifyCode)
        assertEquals(true, c.captchaOnPasswordReset)
        assertEquals(true, c.loginEmailPassword)
        assertEquals(false, c.loginPhonePassword)
        assertEquals(true, c.registerEmail)
        assertEquals(false, c.registerPhone)
        assertEquals(true, c.codeOnEmailRegister)
    }

    @Test
    fun `site config handles string flags and missing fields safely`() {
        val json = JsonParser.parseString("""{"captcha_client_login":"1","register_phone":0}""")
        val c = SiteConfigParser.parse(json)
        assertEquals(true, c.captchaOnLogin)
        assertEquals(false, c.registerPhone)
        assertEquals("QingVPS", c.websiteName) // 默认值
        val defaults = SiteConfigParser.parse(null)
        assertEquals(true, defaults.captchaOnRegister)
        assertEquals(false, defaults.captchaOnLogin)
    }

    @Test
    fun `account parser is lenient on unknown shape`() {
        val json = JsonParser.parseString(
            """{"account":{"id":7,"username":"neo","email":"n@x.com","credit":"12.50"}}"""
        )
        val a = AccountParser.parse(json)
        assertEquals("7", a.id)
        assertEquals("neo", a.username)
        assertEquals("n@x.com", a.email)
        assertEquals("12.50", a.balance)
        assertNull(a.phone)
    }

    @Test
    fun `account parser tolerates null and flat data`() {
        assertNull(AccountParser.parse(null).username)
        assertEquals("--", AccountParser.parse(null).username ?: "--")
        val flat = JsonParser.parseString("""{"username":"flat","balance":3.5}""")
        assertEquals("flat", AccountParser.parse(flat).username)
        assertEquals("3.5", AccountParser.parse(flat).balance)
    }
}
