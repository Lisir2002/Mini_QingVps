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
 * 找回密码（v1.4.18 重构）：与注册页一致的输入框、全局发码冷却、加载内联与结构化提示。
 */
@Composable
fun ForgotPasswordScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val vm: AuthViewModel = viewModel(
        factory = simpleViewModelFactory {
            AuthViewModel(AppContainer.authRepository, AppContainer.siteConfigRepository)
        }
    )
    val state by vm.ui.collectAsState()

    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    LaunchedEffect(state.success) {
        if (state.success) {
            vm.consumeSuccess()
            onDone()
        }
    }

    val emailOk = email.trim().contains("@")

    CartoonBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            GlassTopBar(
                title = Strings.FORGOT_PASSWORD,
                subtitle = "通过邮箱验证码重置登录密码",
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
                            Strings.CAPTCHA_HINT,
                            style = MaterialTheme.typography.bodySmall,
                            color = CartoonColors.InkSub
                        )
                        AuthTextField(
                            value = email, onValueChange = { email = it; vm.clearMessages() },
                            label = Strings.EMAIL, icon = AppIcons.Mail,
                            keyboardType = KeyboardType.Email, imeAction = ImeAction.Next,
                            enabled = !state.loading
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AuthTextField(
                                value = code, onValueChange = { code = it.filter { c -> c.isDigit() }.take(6); vm.clearMessages() },
                                label = Strings.VERIFY_CODE, icon = AppIcons.Captcha,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
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
                                                action = AuthBodies.ACTION_PASSWORD_RESET
                                            )
                                        )
                                    }
                                },
                                busy = state.sendingCode || state.captchaDialog,
                                enabled = emailOk && !state.loading,
                            )
                        }
                        AuthTextField(
                            value = newPassword, onValueChange = { newPassword = it; vm.clearMessages() },
                            label = Strings.NEW_PASSWORD, icon = AppIcons.Lock,
                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, password = true,
                            enabled = !state.loading
                        )
                        state.error?.let { AuthMessage(it, isError = true) }
                        state.info?.let { AuthMessage(it, isError = false) }
                        CartoonButton(
                            text = if (state.loading) "正在提交…" else Strings.SUBMIT,
                            onClick = {
                                when {
                                    !emailOk -> vm.showInfo("请先填写正确的邮箱地址")
                                    code.isBlank() -> vm.showInfo("请先获取并填写邮箱验证码")
                                    newPassword.length !in 6..32 -> vm.showInfo("新密码需 6–32 位")
                                    else -> vm.submitReset(email.trim(), code.trim(), newPassword)
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
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(Strings.BACK, color = CartoonColors.InkSub)
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
