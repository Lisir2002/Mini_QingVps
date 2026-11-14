package com.vps.qing.core

import com.google.gson.JsonParser
import com.vps.qing.data.repository.SiteConfig
import com.vps.qing.data.repository.SiteConfigParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GET /common 实测开关（2026-11）：注册/找回发码、安全中心验证原绑定与换绑
 * 都要图形验证码；首次登录不要，但登录失败后服务端会返回 data.captcha==1。
 */
class CaptchaPolicyTest {

    private val common = JsonParser.parseString(
        """
        {
          "captcha_client_login": "0",
          "captcha_client_login_error": "1",
          "captcha_client_register": "1",
          "captcha_client_verify": "1",
          "captcha_client_update": "1",
          "captcha_client_password_reset": "1",
          "captcha_client_security_verify": "0",
          "register_email": "1"
        }
        """.trimIndent()
    )

    @Test
    fun `parse all captcha switches including update and login error`() {
        val cfg = SiteConfigParser.parse(common)
        assertFalse(cfg.captchaOnLogin)
        assertTrue(cfg.captchaOnLoginError)
        assertTrue(cfg.captchaOnRegister)
        assertTrue(cfg.captchaOnVerifyCode)
        assertTrue(cfg.captchaOnUpdate)
        assertTrue(cfg.captchaOnPasswordReset)
    }

    @Test
    fun `scenario mapping follows site js`() {
        val cfg = SiteConfigParser.parse(common)
        assertFalse(cfg.requiresCaptcha(CaptchaScenario.LOGIN))
        assertTrue(cfg.requiresCaptcha(CaptchaScenario.REGISTER_CODE))
        assertTrue(cfg.requiresCaptcha(CaptchaScenario.PASSWORD_RESET_CODE))
        assertTrue(cfg.requiresCaptcha(CaptchaScenario.VERIFY_OLD_CODE))
        assertTrue(cfg.requiresCaptcha(CaptchaScenario.UPDATE_NEW_CODE))
        assertTrue(cfg.requiresCaptcha(CaptchaScenario.ALWAYS))
    }

    @Test
    fun `site disabled all captcha means no dialog`() {
        val off = JsonParser.parseString(
            """
            {
              "captcha_client_login": 0,
              "captcha_client_register": 0,
              "captcha_client_verify": 0,
              "captcha_client_update": 0,
              "captcha_client_password_reset": 0
            }
            """.trimIndent()
        )
        val cfg = SiteConfigParser.parse(off)
        CaptchaScenario.values().filter { it != CaptchaScenario.ALWAYS }.forEach {
            assertFalse("$it should not require captcha", cfg.requiresCaptcha(it))
        }
        // 服务端明确要求（登录失败）时无视开关
        assertTrue(cfg.requiresCaptcha(CaptchaScenario.ALWAYS))
    }

    @Test
    fun `defaults are safe when common request fails`() {
        val cfg = SiteConfig()
        assertFalse(cfg.captchaOnLogin)
        assertTrue(cfg.captchaOnRegister)
        assertTrue(cfg.captchaOnVerifyCode)
        assertTrue(cfg.captchaOnUpdate)
        assertTrue(cfg.captchaOnPasswordReset)
    }
}
