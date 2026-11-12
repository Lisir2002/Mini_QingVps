package com.vps.qing.data.repository

import com.google.gson.JsonObject
import com.vps.qing.core.AppContainer
import com.vps.qing.core.network.ApiEnvelope
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.TokenExtractor
import com.vps.qing.core.network.TokenManager
import com.vps.qing.core.network.safeApiCall
import com.vps.qing.core.network.string
import com.vps.qing.data.api.QingVpsApiService

/**
 * 登录 / 注册 / 验证码 / 找回密码 / 图形验证码。
 *
 * 请求体全部由 [AuthBodies] 构造（纯 JVM，已做单元测试）；字段名、密码加密方式、
 * JWT 位置（`data.jwt`）均来自站点前端 JS 与匿名实测，不再使用猜测的多候选键。
 */
class AuthRepository(
    private val api: QingVpsApiService,
    private val tokens: TokenManager
) {

    private suspend fun loginWith(body: JsonObject): ApiResult<Unit> {
        val result = safeApiCall({ api.login(body) }) { TokenExtractor.extract(it) }
        return when (result) {
            is ApiResult.Success -> {
                val jwt = result.data
                if (jwt.isNullOrBlank()) {
                    ApiResult.Failure(-1, "登录成功但响应里没有 data.jwt，请确认站点版本")
                } else {
                    tokens.save(jwt)
                    ApiResult.Success(Unit)
                }
            }
            is ApiResult.Failure -> result
            is ApiResult.Error -> result
        }
    }

    suspend fun loginByPassword(account: String, password: String): ApiResult<Unit> =
        loginWith(AuthBodies.login(account = account, password = password))

    suspend fun loginByCode(account: String, code: String): ApiResult<Unit> =
        loginWith(AuthBodies.login(account = account, code = code))

    suspend fun register(
        email: String,
        password: String,
        confirmPassword: String,
        emailCode: String
    ): ApiResult<Unit> {
        val result = safeApiCall(
            { api.register(AuthBodies.register(email, password, confirmPassword, emailCode)) }
        ) { TokenExtractor.extract(it) }
        return when (result) {
            is ApiResult.Success -> {
                // 站点 regist.js 注册成功后直接 localStorage.setItem("jwt", res.data.data.jwt)
                if (!result.data.isNullOrBlank()) tokens.save(result.data!!)
                ApiResult.Success(Unit)
            }
            is ApiResult.Failure -> result
            is ApiResult.Error -> result
        }
    }

    suspend fun sendEmailCode(
        email: String,
        action: String = AuthBodies.ACTION_REGISTER,
        captcha: String = "",
        captchaToken: String = ""
    ): ApiResult<Unit> = simpleCall {
        api.emailCode(AuthBodies.emailCode(email, action, captcha, captchaToken))
    }

    suspend fun sendPhoneCode(
        phone: String,
        countryCode: String = "86",
        action: String = AuthBodies.ACTION_REGISTER,
        captcha: String = "",
        captchaToken: String = ""
    ): ApiResult<Unit> = simpleCall {
        api.phoneCode(AuthBodies.phoneCode(phone, action, countryCode, captcha, captchaToken))
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String): ApiResult<Unit> =
        simpleCall { api.resetPassword(AuthBodies.resetPassword(email, code, newPassword)) }

    // ---- 图形验证码：拿 HTML → 解析 token+图片 → 校验后得到 (captcha, token) ----

    suspend fun captchaChallenge(): ApiResult<CaptchaChallenge> =
        when (val r = safeApiCall({ api.captcha() }) { it.string("html").orEmpty() }) {
            is ApiResult.Success -> CaptchaParser.parseHtml(r.data)?.let { ApiResult.Success(it) }
                ?: ApiResult.Error(IllegalStateException("图形验证码 HTML 解析失败"))
            is ApiResult.Failure -> r
            is ApiResult.Error -> r
        }

    suspend fun refreshCaptcha(): ApiResult<CaptchaChallenge> = try {
        val r = api.refreshCaptcha(AppContainer.CAPTCHA_REFRESH_URL)
        val image = CaptchaParser.stripDataUri(r.base64)
        val token = r.token
        if (image.isNullOrBlank() || token.isNullOrBlank()) {
            ApiResult.Error(IllegalStateException("刷新图形验证码返回数据不完整"))
        } else {
            ApiResult.Success(CaptchaChallenge(token, image))
        }
    } catch (e: Exception) {
        ApiResult.Error(e)
    }

    /** 校验通过后返回可随业务请求提交的凭证。 */
    suspend fun verifyCaptcha(captcha: String, token: String): ApiResult<CaptchaTicket> =
        when (val r = safeApiCall({
            api.verifyCaptcha(AppContainer.CAPTCHA_VERIFY_URL, captcha, token)
        }) { Unit }) {
            is ApiResult.Success -> ApiResult.Success(CaptchaTicket(captcha, token))
            is ApiResult.Failure -> r
            is ApiResult.Error -> r
        }

    private suspend fun simpleCall(block: suspend () -> ApiEnvelope): ApiResult<Unit> =
        when (val result = safeApiCall(block) { Unit }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
            is ApiResult.Error -> result
        }
}
