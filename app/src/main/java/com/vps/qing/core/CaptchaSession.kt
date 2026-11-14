package com.vps.qing.core

import com.vps.qing.core.network.ApiResult
import com.vps.qing.data.repository.AuthRepository
import com.vps.qing.data.repository.CaptchaChallenge
import com.vps.qing.data.repository.CaptchaTicket
import com.vps.qing.data.repository.SiteConfig
import com.vps.qing.data.repository.SiteConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 站点把「是否需要图形验证码」拆成多个独立开关（GET /common，2026-11 实测值）：
 * captcha_client_login=0、captcha_client_login_error=1、captcha_client_register=1、
 * captcha_client_verify=1、captcha_client_update=1、captcha_client_password_reset=1。
 *
 * 场景与开关的对应关系来自站点前端 login.js / regist.js / forget.js / js/account.js：
 * - 注册、找回密码：只有「发送验证码」需要，提交表单的请求体本身不带 captcha/token；
 * - 安全中心：验证原绑定（action=verify）看 captcha_client_verify，
 *   换绑新值（action=update）看 captcha_client_update；
 * - 登录：captcha_client_login 控制首次登录；失败后服务端返回 data.captcha==1，
 *   此时无论开关如何都必须补一次图形验证。
 */
enum class CaptchaScenario {
    LOGIN,
    REGISTER_CODE,
    PASSWORD_RESET_CODE,
    VERIFY_OLD_CODE,
    UPDATE_NEW_CODE,

    /** 服务端已明确要求（登录失败 data.captcha==1、发码返回 400），忽略开关强制验证。 */
    ALWAYS
}

fun SiteConfig.requiresCaptcha(scenario: CaptchaScenario): Boolean = when (scenario) {
    CaptchaScenario.LOGIN -> captchaOnLogin
    CaptchaScenario.REGISTER_CODE -> captchaOnRegister
    CaptchaScenario.PASSWORD_RESET_CODE -> captchaOnPasswordReset
    CaptchaScenario.VERIFY_OLD_CODE -> captchaOnVerifyCode
    CaptchaScenario.UPDATE_NEW_CODE -> captchaOnUpdate
    CaptchaScenario.ALWAYS -> true
}

/**
 * 全 App 共用的图形验证码会话：拉图 → 校验 → 把一次性凭证交给业务动作。
 *
 * 之所以统一在这里，是因为登录、注册、找回密码、安全中心换绑都要走同一条链路，
 * v1.4.12 里安全中心漏接这条链路，导致 /phone/code、/email/code 直接返回
 * 400「请输入图形验证码」。现在所有发码/登录动作都必须经过 [request]。
 *
 * 站点未开启对应开关时不弹窗，直接回传空凭证（服务端同样不会校验）。
 */
class CaptchaSession(
    private val auth: AuthRepository,
    private val site: SiteConfigRepository,
    private val scope: CoroutineScope
) {
    data class UiState(
        val visible: Boolean = false,
        val challenge: CaptchaChallenge? = null,
        val loading: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 验证通过后要继续执行的动作；凭证是一次性的，用完即清空。 */
    private var onVerified: ((CaptchaTicket) -> Unit)? = null

    /** 点击业务按钮时调用；验证通过（或站点未开启）后自动执行 [onVerified]。 */
    fun request(scenario: CaptchaScenario, onVerified: (CaptchaTicket) -> Unit) {
        this.onVerified = onVerified
        scope.launch {
            val config = runCatching { site.get() }.getOrDefault(SiteConfig())
            if (!config.requiresCaptcha(scenario)) {
                dismiss()
                onVerified(CaptchaTicket("", ""))
                return@launch
            }
            _state.update { UiState(visible = true, loading = true) }
            loadChallenge()
        }
    }

    private suspend fun loadChallenge() {
        when (val r = auth.captchaChallenge()) {
            is ApiResult.Success -> _state.update {
                it.copy(challenge = r.data, loading = false, error = null)
            }
            is ApiResult.Failure -> _state.update {
                it.copy(loading = false, error = "[${r.code}] ${r.msg}")
            }
            is ApiResult.Error -> _state.update {
                it.copy(loading = false, error = r.throwable.message ?: "图形验证码加载失败")
            }
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = auth.refreshCaptcha()) {
                is ApiResult.Success -> _state.update {
                    it.copy(challenge = r.data, loading = false, error = null)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = "[${r.code}] ${r.msg}")
                }
                is ApiResult.Error -> _state.update {
                    it.copy(loading = false, error = r.throwable.message ?: "刷新失败")
                }
            }
        }
    }

    /** 弹窗内输入后校验；成功后关闭弹窗并继续原动作，失败自动换一张。 */
    fun submit(input: String) {
        val token = _state.value.challenge?.token
        if (input.isBlank() || token.isNullOrBlank()) {
            _state.update { it.copy(error = "请输入图形验证码") }
            return
        }
        scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = auth.verifyCaptcha(input.trim(), token)) {
                is ApiResult.Success -> {
                    val action = onVerified
                    dismiss()
                    action?.invoke(r.data)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(loading = false, error = "[${r.code}] ${r.msg}") }
                    refresh()
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(loading = false, error = r.throwable.message ?: "验证失败") }
                    refresh()
                }
            }
        }
    }

    fun dismiss() {
        onVerified = null
        _state.value = UiState()
    }
}
