package com.vps.qing.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.vps.qing.core.ui.topInsetPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
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
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors

/**
 * 登录页（v1.4.18 布局与交互重做）。
 *
 *  - 输入框统一换成 [AuthTextField]：placeholder 常驻框内、18dp 圆角、密码可显隐，
 *    根除浮动 label 被圆角裁到框外的问题；
 *  - 焦点链：邮箱「下一步」直接跳密码，密码「完成」直接提交并收起键盘；
 *  - 加载态内联进按钮（不再在按钮下方孤立转圈），错误/提示用带图标的提示条；
 *  - topInsetPadding + imePadding：作为顶层导航页自行避开状态栏，键盘弹起不遮挡输入框与操作按钮。
 */
@Composable
fun LoginScreen(
    onSuccess: (Boolean) -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit
) {
    val vm: AuthViewModel = viewModel(
        factory = simpleViewModelFactory {
            AuthViewModel(AppContainer.authRepository, AppContainer.siteConfigRepository)
        }
    )
    val state by vm.ui.collectAsState()

    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.success) {
        if (state.success) {
            vm.consumeSuccess()
            onSuccess(state.autoLogin)
        }
    }

    val canSubmit = !state.loading &&
        account.trim().contains("@") && password.isNotBlank()

    fun doLogin() {
        if (!canSubmit) return
        keyboard?.hide()
        focusManager.clearFocus()
        vm.loginByPassword(account.trim(), password)
    }

    CartoonBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .topInsetPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 品牌区
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CartoonKit.CardShape)
                    .background(CartoonColors.BrandSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Cloud, null, tint = CartoonColors.Brand, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                Strings.APP_NAME,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = CartoonColors.Ink
            )
            Text(
                state.config.websiteName,
                style = MaterialTheme.typography.bodySmall,
                color = CartoonColors.InkSub
            )
            Spacer(Modifier.height(14.dp))

            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = CartoonKit.CardShape,
                alpha = UiT.glassCard,
                elevation = 12.dp
            ) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        Strings.PASSWORD_LOGIN,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = CartoonColors.Ink
                    )
                    Text(
                        Strings.LOGIN_ONLY_EMAIL,
                        style = MaterialTheme.typography.bodySmall,
                        color = CartoonColors.InkSub
                    )
                    Spacer(Modifier.height(6.dp))

                    AuthTextField(
                        value = account,
                        onValueChange = { account = it; vm.clearMessages() },
                        label = Strings.EMAIL,
                        icon = AppIcons.Mail,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        enabled = !state.loading,
                        isError = state.error != null,
                        onImeAction = { passwordFocus.requestFocus() }
                    )
                    AuthTextField(
                        value = password,
                        onValueChange = { password = it; vm.clearMessages() },
                        label = Strings.PASSWORD,
                        icon = AppIcons.Lock,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        password = true,
                        enabled = !state.loading,
                        isError = state.error != null,
                        focusRequester = passwordFocus,
                        onImeAction = { doLogin() }
                    )

                    state.error?.let { AuthMessage(it, isError = true) }
                    state.info?.let { AuthMessage(it, isError = false) }

                    Spacer(Modifier.height(2.dp))
                    CartoonButton(
                        text = if (state.loading) "正在登录…" else Strings.LOGIN,
                        onClick = { doLogin() },
                        enabled = canSubmit,
                        icon = if (state.loading) null else AppIcons.Forward,
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onRegister) {
                            Text(Strings.NO_ACCOUNT, color = CartoonColors.Brand)
                        }
                        TextButton(onClick = onForgot) {
                            Text(Strings.FORGOT_PASSWORD, color = CartoonColors.InkSub)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // 站点 captcha_client_login_error=1：连续登录失败后服务端要求图形验证
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
