package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.safeApiCall
import com.vps.qing.core.network.string
import com.vps.qing.data.api.QingVpsApiService

/**
 * GET /common 的站点开关。2026-11 实测值：
 * captcha_client_login=0、captcha_client_register=1、captcha_client_verify=1、
 * captcha_client_password_reset=1、register_email=1、register_phone=0、
 * login_email_password=1、login_phone_password=0、code_client_email_register=1。
 * 即：本站只开放邮箱注册/登录，注册、找回密码、发送验证码都要图形验证码。
 */
data class SiteConfig(
    val websiteName: String = "QingVPS",
    val currencyPrefix: String = "￥",
    val currencyCode: String = "CNY",
    val captchaOnLogin: Boolean = false,
    val captchaOnRegister: Boolean = true,
    val captchaOnVerifyCode: Boolean = true,
    val captchaOnPasswordReset: Boolean = true,
    val loginEmailPassword: Boolean = true,
    val loginPhonePassword: Boolean = false,
    val loginPhoneVerify: Boolean = false,
    val registerEmail: Boolean = true,
    val registerPhone: Boolean = false,
    val codeOnEmailRegister: Boolean = true,
    val strongPasswordRule: Boolean = true,
    val termsUrl: String = "",
    val privacyUrl: String = ""
)

/** 纯 JVM 解析，便于本地单元测试。开关值可能是 JSON number 或 string，统一按字符串比较。 */
object SiteConfigParser {
    private fun JsonElement?.flag(key: String, default: Boolean): Boolean {
        val raw = this.string(key) ?: return default
        return when (raw.trim().lowercase()) {
            "1", "true", "yes" -> true
            "0", "false", "no", "" -> false
            else -> default
        }
    }

    fun parse(data: JsonElement?): SiteConfig = SiteConfig(
        websiteName = data.string("website_name", "site_name") ?: "QingVPS",
        currencyPrefix = data.string("currency_prefix", "currency") ?: "￥",
        currencyCode = data.string("currency_code") ?: "CNY",
        captchaOnLogin = data.flag("captcha_client_login", false),
        captchaOnRegister = data.flag("captcha_client_register", true),
        captchaOnVerifyCode = data.flag("captcha_client_verify", true),
        captchaOnPasswordReset = data.flag("captcha_client_password_reset", true),
        loginEmailPassword = data.flag("login_email_password", true),
        loginPhonePassword = data.flag("login_phone_password", false),
        loginPhoneVerify = data.flag("login_phone_verify", false),
        registerEmail = data.flag("register_email", true),
        registerPhone = data.flag("register_phone", false),
        codeOnEmailRegister = data.flag("code_client_email_register", true),
        strongPasswordRule = data.flag("client_strong_password_rule", true),
        termsUrl = data.string("terms_service_url").orEmpty(),
        privacyUrl = data.string("terms_privacy_url").orEmpty()
    )
}

class SiteConfigRepository(private val api: QingVpsApiService) {

    @Volatile
    private var cached: SiteConfig? = null

    suspend fun get(force: Boolean = false): SiteConfig {
        if (!force) cached?.let { return it }
        val parsed = when (val result = safeApiCall({ api.common() }) { it }) {
            is ApiResult.Success -> SiteConfigParser.parse(result.data)
            else -> SiteConfig()
        }
        cached = parsed
        return parsed
    }
}
