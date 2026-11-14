package com.vps.qing.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonElement
import com.vps.qing.core.AppContainer
import com.vps.qing.core.CaptchaScenario
import com.vps.qing.core.CaptchaSession
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.HtmlFallbackException
import com.vps.qing.core.network.number
import com.vps.qing.core.network.string
import com.vps.qing.data.repository.AuthBodies
import com.vps.qing.data.repository.AuthRepository
import com.vps.qing.data.repository.CaptchaChallenge
import com.vps.qing.data.repository.CaptchaTicket
import com.vps.qing.data.repository.SiteConfig
import com.vps.qing.data.repository.SiteConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** 图形验证码弹窗是为哪个动作弹出的。 */
enum class CaptchaPurpose { SEND_CODE, LOGIN_RETRY, REGISTER, RESET }

/** 弹窗触发时把表单暂存到这里，验证通过后按 purpose 自动分发，用户不需要再点一次。 */
data class CaptchaRequest(
    val purpose: CaptchaPurpose,
    val email: String = "",
    val action: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val emailCode: String = "",
    val newPassword: String = "",
    /** 登录失败后重试用的账号。 */
    val account: String = ""
)

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val success: Boolean = false,
    val autoLogin: Boolean = false,
    val config: SiteConfig = SiteConfig(),
    /** 以下四个字段由 [CaptchaSession] 同步，供 UI 驱动弹窗与按钮禁用态。 */
    val captchaDialog: Boolean = false,
    val captcha: CaptchaChallenge? = null,
    val captchaLoading: Boolean = false,
    val captchaError: String? = null
)

class AuthViewModel(
    private val auth: AuthRepository,
    private val site: SiteConfigRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    /** 与登录、注册、找回密码、安全中心共用同一条图形验证码链路（v1.4.13）。 */
    private val captchaSession = CaptchaSession(auth, site, viewModelScope)

    init {
        viewModelScope.launch {
            runCatching { _ui.update { it.copy(config = site.get()) } }
        }
        viewModelScope.launch {
            captchaSession.state.collect { s ->
                _ui.update {
                    it.copy(
                        captchaDialog = s.visible,
                        captcha = s.challenge,
                        captchaLoading = s.loading,
                        captchaError = s.error
                    )
                }
            }
        }
    }

    /** 本站只保留这一种登录方式：邮箱 + 密码。 */
    fun loginByPassword(account: String, password: String) {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update {
                it.copy(loading = true, error = null, info = null, success = false)
            }
            when (val result = auth.loginByPassword(account, password)) {
                is ApiResult.Success -> finishLogin()
                is ApiResult.Failure -> {
                    // 站点 captcha_client_login_error=1：连续失败后响应 data.captcha==1，
                    // 此时必须补一次图形验证并带凭证重试，否则会一直登录失败。
                    if (result.data.needsCaptcha()) {
                        _ui.update {
                            it.copy(loading = false, error = "密码错误次数过多，请完成图形验证后重试")
                        }
                        captchaSession.request(CaptchaScenario.ALWAYS) { ticket ->
                            viewModelScope.launch {
                                retryLogin(account, password, ticket)
                            }
                        }
                    } else {
                        _ui.update {
                            it.copy(loading = false, error = "[${result.code}] ${result.msg}")
                        }
                    }
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(loading = false, error = friendly(result.throwable))
                }
            }
        }
    }

    private suspend fun retryLogin(account: String, password: String, ticket: CaptchaTicket) {
        _ui.update { it.copy(loading = true, error = null) }
        when (val result = auth.loginByPassword(account, password, ticket.captcha, ticket.token)) {
            is ApiResult.Success -> finishLogin()
            is ApiResult.Failure -> _ui.update {
                it.copy(loading = false, error = "[${result.code}] ${result.msg}")
            }
            is ApiResult.Error -> _ui.update {
                it.copy(loading = false, error = friendly(result.throwable))
            }
        }
    }

    private fun finishLogin() {
        _ui.update {
            it.copy(loading = false, success = true, autoLogin = AppContainer.isLoggedIn())
        }
    }

    /** 服务端在 data.captcha 里用 1 / "1" 标记「需要图形验证码」。 */
    private fun JsonElement?.needsCaptcha(): Boolean =
        this.string("captcha") == "1" || (this.number("captcha") ?: 0.0) == 1.0

    // ---- 图形验证码弹窗 ----

    /**
     * 点击「获取验证码」或登录失败重试时调用。按 /common 对应开关决定是否弹窗：
     * 注册发码看 captcha_client_register，找回发码看 captcha_client_password_reset。
     */
    fun openCaptcha(request: CaptchaRequest) {
        val scenario = when (request.purpose) {
            CaptchaPurpose.SEND_CODE -> when (request.action) {
                AuthBodies.ACTION_REGISTER -> CaptchaScenario.REGISTER_CODE
                AuthBodies.ACTION_PASSWORD_RESET -> CaptchaScenario.PASSWORD_RESET_CODE
                else -> CaptchaScenario.ALWAYS
            }
            CaptchaPurpose.LOGIN_RETRY -> CaptchaScenario.ALWAYS
            // 站点 regist.js / forget.js：注册与找回的「提交表单」请求体不带 captcha/token，
            // 图形验证只作用于发码，因此这里直接执行，不再多弹一次无意义的窗。
            CaptchaPurpose.REGISTER, CaptchaPurpose.RESET -> null
        }
        if (scenario == null) {
            dispatch(request, CaptchaTicket("", ""))
            return
        }
        captchaSession.request(scenario) { ticket -> dispatch(request, ticket) }
    }

    fun dismissCaptcha() = captchaSession.dismiss()
    fun refreshCaptcha() = captchaSession.refresh()
    fun submitCaptcha(input: String) = captchaSession.submit(input)

    private fun dispatch(request: CaptchaRequest, ticket: CaptchaTicket) {
        when (request.purpose) {
            CaptchaPurpose.SEND_CODE -> sendEmailCodeInternal(request.email, request.action, ticket)
            CaptchaPurpose.LOGIN_RETRY -> viewModelScope.launch {
                retryLogin(request.account, request.password, ticket)
            }
            CaptchaPurpose.REGISTER -> submitRegister(
                request.email, request.password, request.confirmPassword, request.emailCode
            )
            CaptchaPurpose.RESET -> submitReset(request.email, request.emailCode, request.newPassword)
        }
    }

    /** 图形验证码凭证是一次性的，发送后即丢弃，下次要重新验证。 */
    private fun sendEmailCodeInternal(email: String, action: String, ticket: CaptchaTicket) {
        viewModelScope.launch {
            _ui.update { it.copy(info = null, error = null) }
            when (val r = auth.sendEmailCode(email, action, ticket.captcha, ticket.token)) {
                is ApiResult.Success -> _ui.update { it.copy(info = "验证码已发送，请查收邮箱") }
                is ApiResult.Failure -> _ui.update { it.copy(info = "[${r.code}] ${r.msg}") }
                is ApiResult.Error -> _ui.update { it.copy(info = friendly(r.throwable)) }
            }
        }
    }

    /** 注册提交：站点请求体不含 captcha/token，无需弹窗。 */
    fun submitRegister(email: String, password: String, confirmPassword: String, emailCode: String) =
        runAuth { auth.register(email, password, confirmPassword, emailCode) }

    /** 找回密码提交：同上，发码时才需要图形验证。 */
    fun submitReset(email: String, emailCode: String, newPassword: String) =
        runAuth { auth.resetPassword(email, emailCode, newPassword) }

    fun showInfo(message: String) = _ui.update { it.copy(info = message) }
    fun clearMessages() = _ui.update { it.copy(error = null, info = null) }
    fun consumeSuccess() = _ui.update { it.copy(success = false) }

    private fun runAuth(block: suspend () -> ApiResult<Unit>) {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, info = null, success = false) }
            when (val result = block()) {
                is ApiResult.Success -> finishLogin()
                is ApiResult.Failure -> _ui.update {
                    it.copy(loading = false, error = "[${result.code}] ${result.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(loading = false, error = friendly(result.throwable))
                }
            }
        }
    }

    private fun friendly(t: Throwable): String = when (t) {
        is HtmlFallbackException -> "接口返回了网页而不是 JSON，可能是该功能未启用或站点升级了"
        is IOException -> "网络连接失败，请检查网络（站点提示不要使用 VPN 访问）"
        else -> t.message ?: "未知错误"
    }
}
