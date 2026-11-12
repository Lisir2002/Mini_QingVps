package com.vps.qing.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.AuthBodies

/** 注册页：提交时弹图形验证码，验证通过后自动继续注册/发码。 */
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

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.REGISTER,
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
                    alpha = 0.84f
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "本站仅开放邮箱注册，提交时会弹出图形验证码",
                            style = MaterialTheme.typography.bodySmall,
                            color = CartoonColors.InkSub
                        )
                        AuthTextField(
                            value = email, onValueChange = { email = it; vm.clearMessages() },
                            label = Strings.EMAIL, icon = AppIcons.Mail,
                            keyboardType = KeyboardType.Email, imeAction = ImeAction.Next
                        )
                        AuthTextField(
                            value = password, onValueChange = { password = it; vm.clearMessages() },
                            label = Strings.PASSWORD, icon = AppIcons.Lock,
                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, password = true
                        )
                        AuthTextField(
                            value = confirm, onValueChange = { confirm = it; vm.clearMessages() },
                            label = Strings.CONFIRM_PASSWORD, icon = AppIcons.Lock,
                            keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, password = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AuthTextField(
                                value = code, onValueChange = { code = it; vm.clearMessages() },
                                label = Strings.VERIFY_CODE, icon = AppIcons.Captcha,
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                            CartoonButton(
                                text = Strings.SEND_CODE,
                                onClick = {
                                    vm.openCaptcha(
                                        CaptchaRequest(
                                            purpose = CaptchaPurpose.SEND_CODE,
                                            email = email.trim(),
                                            action = AuthBodies.ACTION_REGISTER
                                        )
                                    )
                                },
                                modifier = Modifier.height(52.dp)
                            )
                        }
                        Text(
                            Strings.CAPTCHA_HINT,
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        state.info?.let {
                            Text(it, color = CartoonColors.Mint, style = MaterialTheme.typography.bodySmall)
                        }
                        CartoonButton(
                            text = Strings.REGISTER,
                            onClick = {
                                when {
                                    email.isBlank() -> vm.showInfo("请先填写邮箱")
                                    password != confirm -> vm.showInfo("两次输入的密码不一致")
                                    else -> vm.openCaptcha(
                                        CaptchaRequest(
                                            purpose = CaptchaPurpose.REGISTER,
                                            email = email.trim(),
                                            password = password,
                                            confirmPassword = confirm,
                                            emailCode = code.trim()
                                        )
                                    )
                                }
                            },
                            enabled = !state.loading && !state.captchaDialog,
                            icon = AppIcons.Check,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp).align(Alignment.CenterHorizontally), color = CartoonColors.Brand)
                        }
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
