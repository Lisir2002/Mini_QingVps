package com.vps.qing.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vps.qing.core.AppContainer
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.HtmlFallbackException
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

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val success: Boolean = false,
    val autoLogin: Boolean = false,
    val config: SiteConfig = SiteConfig(),
    /** 当前待校验的图形验证码（token + 图片 base64）。 */
    val captcha: CaptchaChallenge? = null,
    /** 已校验通过的凭证，用于「发送邮箱验证码」。 */
    val captchaTicket: CaptchaTicket? = null,
    val captchaLoading: Boolean = false
)

class AuthViewModel(
    private val auth: AuthRepository,
    private val site: SiteConfigRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { _ui.update { it.copy(config = site.get()) } }
        }
    }

    fun loginByPassword(account: String, password: String) =
        runAuth { auth.loginByPassword(account, password) }

    fun loginByCode(account: String, code: String) =
        runAuth { auth.loginByCode(account, code) }

    fun register(email: String, password: String, confirmPassword: String, emailCode: String) =
        runAuth { auth.register(email, password, confirmPassword, emailCode) }

    fun resetPassword(email: String, code: String, newPassword: String) =
        runAuth { auth.resetPassword(email, code, newPassword) }

    // ---- 图形验证码 ----

    fun loadCaptcha() {
        if (_ui.value.captchaLoading) return
        viewModelScope.launch {
            _ui.update { it.copy(captchaLoading = true) }
            when (val r = auth.captchaChallenge()) {
                is ApiResult.Success -> _ui.update {
                    it.copy(captcha = r.data, captchaTicket = null, captchaLoading = false)
                }
                else -> _ui.update {
                    it.copy(captchaLoading = false, info = "图形验证码加载失败，可用网页注册兜底")
                }
            }
        }
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            when (val r = auth.refreshCaptcha()) {
                is ApiResult.Success -> _ui.update {
                    it.copy(captcha = r.data, captchaTicket = null, info = null)
                }
                else -> _ui.update { it.copy(info = "刷新验证码失败，请重试") }
            }
        }
    }

    /** 用户输入图形验证码后校验，成功后凭证存进 state。 */
    fun submitCaptcha(input: String) {
        val token = _ui.value.captcha?.token
        if (input.isBlank() || token.isNullOrBlank()) {
            _ui.update { it.copy(info = "请输入图形验证码") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(captchaLoading = true, error = null, info = null) }
            when (val r = auth.verifyCaptcha(input.trim(), token)) {
                is ApiResult.Success -> _ui.update {
                    it.copy(captchaTicket = r.data, captchaLoading = false, info = "图形验证通过，可以获取邮箱验证码了")
                }
                is ApiResult.Failure -> {
                    _ui.update { it.copy(captchaLoading = false, error = "[${r.code}] ${r.msg}") }
                    refreshCaptcha()
                }
                is ApiResult.Error -> {
                    _ui.update { it.copy(captchaLoading = false, error = friendly(r.throwable)) }
                    refreshCaptcha()
                }
            }
        }
    }

    /**
     * 发送邮箱验证码。本站 captcha_client_verify=1，必须先完成图形验证码；
     * 凭证是一次性的，发送成功后清空，下次需重新验证。
     */
    fun sendEmailCode(email: String, action: String) {
        if (email.isBlank()) {
            _ui.update { it.copy(info = "请先填写邮箱") }
            return
        }
        val ticket = _ui.value.captchaTicket
        if (_ui.value.config.captchaOnVerifyCode && ticket == null) {
            _ui.update { it.copy(info = "请先完成上方图形验证码") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(info = null, error = null) }
            when (val r = auth.sendEmailCode(
                email, action,
                ticket?.captcha.orEmpty(), ticket?.token.orEmpty()
            )) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(info = "验证码已发送，请查收邮箱", captchaTicket = null) }
                    loadCaptcha()
                }
                is ApiResult.Failure -> {
                    _ui.update { it.copy(info = "[${r.code}] ${r.msg}", captchaTicket = null) }
                    loadCaptcha()
                }
                is ApiResult.Error -> {
                    _ui.update { it.copy(info = friendly(r.throwable), captchaTicket = null) }
                    loadCaptcha()
                }
            }
        }
    }

    fun showInfo(message: String) = _ui.update { it.copy(info = message) }
    fun clearMessages() = _ui.update { it.copy(error = null, info = null) }
    fun consumeSuccess() = _ui.update { it.copy(success = false) }

    private fun runAuth(block: suspend () -> ApiResult<Unit>) {
        if (_ui.value.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, info = null, success = false) }
            when (val result = block()) {
                is ApiResult.Success -> _ui.update {
                    it.copy(loading = false, success = true, autoLogin = AppContainer.isLoggedIn())
                }
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
