package com.vps.qing.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.data.repository.AuthBodies

/**
 * 注册页（v1.4.18 重构）：输入框不再被圆角裁字、密码可显隐、
 * 「获取验证码」接入全局 60s 冷却、加载态内联、错误/成功提示结构化。
 */
@Composable
fun RegisterScreen(
    onSuccess: (Boolean) -> Unit,
    onWebRegister: () -> Unit,
    onBack: () -> Unit
) {
    val vm: AuthViewModel = viewModel(
        factory = simpleViewModelFactory {
            AuthViewModel(AppContainer.authRepository, AppContainer.siteConfigRepository)
        }
    )
    val state by vm.ui.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    LaunchedEffect(state.success) {
        if (state.success) {
            vm.consumeSuccess()
            onSuccess(state.autoLogin)
        }
    }

    val emailOk = email.trim().contains("@")
    val passwordMismatch = confirm.isNotBlank() && password != confirm

    CartoonBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            GlassTopBar(
                title = Strings.REGISTER,
                subtitle = "创建账号，开启你的云服务",
                navIcon = AppIcons.Back,
                onNavClick = onBack
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CartoonKit.CardShape,
                    alpha = UiT.glassCard
                ) {
                    Column(
                        Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "本站仅开放邮箱注册，获取邮箱验证码时需完成图形验证",
                            style = MaterialTheme.typography.bodySmall,
                            color = CartoonColors.InkSub
                        )
                        AuthTextField(
                            value = email, onValueChange = { email = it; vm.clearMessages() },
                            label = Strings.EMAIL, icon = AppIcons.Mail,
                            keyboardType = KeyboardType.Email, imeAction = ImeAction.Next,
                            enabled = !state.loading
                        )
                        AuthTextField(
                            value = password, onValueChange = { password = it; vm.clearMessages() },
                            label = Strings.PASSWORD, icon = AppIcons.Lock,
                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, password = true,
                            enabled = !state.loading
                        )
                        AuthTextField(
                            value = confirm, onValueChange = { confirm = it; vm.clearMessages() },
                            label = Strings.CONFIRM_PASSWORD, icon = AppIcons.Lock,
                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, password = true,
                            enabled = !state.loading,
                            isError = passwordMismatch
                        )
                        if (passwordMismatch) {
                            Text(
                                "两次输入的密码不一致",
                                style = MaterialTheme.typography.labelSmall,
                                color = CartoonColors.Coral
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AuthTextField(
                                value = code, onValueChange = { code = it.filter { c -> c.isDigit() }.take(6); vm.clearMessages() },
                                label = Strings.VERIFY_CODE, icon = AppIcons.Captcha,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                                modifier = Modifier.weight(1f),
                                enabled = !state.loading
                            )
                            SendCodeButton(
                                onClick = {
                                    if (!emailOk) {
                                        vm.showInfo("请先填写正确的邮箱地址")
                                    } else {
                                        vm.openCaptcha(
                                            CaptchaRequest(
                                                purpose = CaptchaPurpose.SEND_CODE,
                                                email = email.trim(),
                                                action = AuthBodies.ACTION_REGISTER
                                            )
                                        )
                                    }
                                },
                                busy = state.sendingCode || state.captchaDialog,
                                enabled = emailOk && !state.loading,
                            )
                        }
                        Text(
                            Strings.CAPTCHA_HINT,
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                        state.error?.let { AuthMessage(it, isError = true) }
                        state.info?.let { AuthMessage(it, isError = false) }
                        CartoonButton(
                            text = if (state.loading) "正在提交…" else Strings.REGISTER,
                            onClick = {
                                when {
                                    !emailOk -> vm.showInfo("请先填写正确的邮箱地址")
                                    password.isBlank() -> vm.showInfo("请填写密码")
                                    password != confirm -> vm.showInfo("两次输入的密码不一致")
                                    code.isBlank() -> vm.showInfo("请先获取并填写邮箱验证码")
                                    else -> vm.submitRegister(email.trim(), password, confirm, code.trim())
                                }
                            },
                            enabled = !state.loading && !state.captchaDialog,
                            icon = if (state.loading) null else AppIcons.Check,
                            modifier = Modifier.fillMaxWidth(),
                            trailingContent = if (state.loading) {
                                {
                                    CircularProgressIndicator(
                                        color = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            } else null
                        )
                    }
                }
                TextButton(onClick = onWebRegister, modifier = Modifier.fillMaxWidth()) {
                    Text(Strings.WEBSITE_REGISTER, color = CartoonColors.InkSub)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (state.captchaDialog) {
        CaptchaDialog(
            challenge = state.captcha,
            loading = state.captchaLoading,
            error = state.captchaError,
            onSubmit = { vm.submitCaptcha(it) },
            onRefresh = { vm.refreshCaptcha() },
            onDismiss = { vm.dismissCaptcha() }
        )
    }
}
