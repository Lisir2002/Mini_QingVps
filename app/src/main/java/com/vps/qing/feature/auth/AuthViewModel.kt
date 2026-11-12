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

/** 图形验证码弹窗是为哪个动作弹出的。 */
enum class CaptchaPurpose { SEND_CODE, REGISTER, RESET }

/** 弹窗触发时把表单暂存到这里，验证通过后按 purpose 自动分发，用户不需要再点一次。 */
data class CaptchaRequest(
    val purpose: CaptchaPurpose,
    val email: String = "",
    val action: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val emailCode: String = "",
    val newPassword: String = ""
)

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val success: Boolean = false,
    val autoLogin: Boolean = false,
    val config: SiteConfig = SiteConfig(),
    /** 是否显示图形验证码弹窗（点注册 / 获取验证码 / 重置密码时才为 true）。 */
    val captchaDialog: Boolean = false,
    val captcha: CaptchaChallenge? = null,
    val captchaLoading: Boolean = false,
    val captchaError: String? = null,
    val pending: CaptchaRequest? = null
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

    /** 本站只保留这一种登录方式：邮箱 + 密码。 */
    fun loginByPassword(account: String, password: String) =
        runAuth { auth.loginByPassword(account, password) }

    // ---- 图形验证码弹窗 ----

    /** 点击「注册」「获取验证码」「提交找回」时调用：弹窗 + 拉取验证码。 */
    fun openCaptcha(request: CaptchaRequest) {
        _ui.update {
            it.copy(
                captchaDialog = true,
                pending = request,
                captcha = null,
                captchaError = null,
                captchaLoading = false
            )
        }
        loadCaptcha()
    }

    fun dismissCaptcha() {
        _ui.update {
            it.copy(
                captchaDialog = false,
                pending = null,
                captcha = null,
                captchaError = null,
                captchaLoading = false
            )
        }
    }

    private fun loadCaptcha() {
        if (_ui.value.captchaLoading) return
        viewModelScope.launch {
            _ui.update { it.copy(captchaLoading = true, captchaError = null) }
            when (val r = auth.captchaChallenge()) {
                is ApiResult.Success -> _ui.update {
                    it.copy(captcha = r.data, captchaLoading = false)
                }
                else -> _ui.update {
                    it.copy(captchaLoading = false, captchaError = "图形验证码加载失败，请点「换一张」重试")
                }
            }
        }
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            _ui.update { it.copy(captchaLoading = true, captchaError = null) }
            when (val r = auth.refreshCaptcha()) {
                is ApiResult.Success -> _ui.update {
                    it.copy(captcha = r.data, captchaLoading = false)
                }
                else -> _ui.update {
                    it.copy(captchaLoading = false, captchaError = "刷新失败，请重试")
                }
            }
        }
    }

    /** 弹窗里输入后校验；通过后立即执行 pending 的动作（注册 / 发码 / 重置）。 */
    fun submitCaptcha(input: String) {
        val token = _ui.value.captcha?.token
        if (input.isBlank() || token.isNullOrBlank()) {
            _ui.update { it.copy(captchaError = "请输入图形验证码") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(captchaLoading = true, captchaError = null) }
            when (val r = auth.verifyCaptcha(input.trim(), token)) {
                is ApiResult.Success -> {
                    val request = _ui.value.pending
                    _ui.update {
                        it.copy(
                            captchaDialog = false,
                            pending = null,
                            captcha = null,
                            captchaLoading = false,
                            captchaError = null
                        )
                    }
                    if (request != null) dispatch(request, r.data)
                }
                is ApiResult.Failure -> {
                    _ui.update { it.copy(captchaLoading = false, captchaError = "[${r.code}] ${r.msg}") }
                    refreshCaptcha()
                }
                is ApiResult.Error -> {
                    _ui.update { it.copy(captchaLoading = false, captchaError = friendly(r.throwable)) }
                    refreshCaptcha()
                }
            }
        }
    }

    private fun dispatch(request: CaptchaRequest, ticket: CaptchaTicket) {
        when (request.purpose) {
            // 发送邮箱验证码需要把图形验证码凭证带上（captcha_client_verify=1）
            CaptchaPurpose.SEND_CODE -> sendEmailCodeInternal(request.email, request.action, ticket)
            // 站点注册体 / 找回密码体本身不含 captcha 字段，图形验证只是提交前的人机校验
            CaptchaPurpose.REGISTER -> runAuth {
                auth.register(request.email, request.password, request.confirmPassword, request.emailCode)
            }
            CaptchaPurpose.RESET -> runAuth {
                auth.resetPassword(request.email, request.emailCode, request.newPassword)
            }
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
