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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.AuthBodies

@Composable
fun LoginScreen(
    onSuccess: (Boolean) -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
    onWebLogin: () -> Unit
) {
    val vm: AuthViewModel = viewModel(
        factory = simpleViewModelFactory {
            AuthViewModel(AppContainer.authRepository, AppContainer.siteConfigRepository)
        }
    )
    val state by vm.ui.collectAsState()

    var tab by remember { mutableStateOf(0) }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    LaunchedEffect(state.success) {
        if (state.success) {
            vm.consumeSuccess()
            onSuccess(state.autoLogin)
        }
    }
    // 验证码登录要先完成图形验证码才能发邮箱验证码（captcha_client_verify=1）
    LaunchedEffect(tab) { if (tab == 1 && state.captcha == null) vm.loadCaptcha() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(Strings.APP_NAME, style = MaterialTheme.typography.headlineMedium)
            Text(
                state.config.websiteName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(Strings.PASSWORD_LOGIN) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(Strings.CODE_LOGIN) })
            }

            OutlinedTextField(
                value = account,
                onValueChange = { account = it; vm.clearMessages() },
                label = { Text(Strings.EMAIL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            if (tab == 0) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; vm.clearMessages() },
                    label = { Text(Strings.PASSWORD) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
            } else {
                CaptchaField(
                    challenge = state.captcha,
                    ticket = state.captchaTicket,
                    loading = state.captchaLoading,
                    onSubmit = { vm.submitCaptcha(it) },
                    onRefresh = { vm.refreshCaptcha() },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it; vm.clearMessages() },
                        label = { Text(Strings.VERIFY_CODE) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        visualTransformation = VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(onClick = { vm.sendEmailCode(account.trim(), AuthBodies.ACTION_LOGIN) }) {
                        Text(Strings.SEND_CODE)
                    }
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.info?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    if (tab == 0) vm.loginByPassword(account.trim(), password)
                    else vm.loginByCode(account.trim(), code.trim())
                },
                enabled = !state.loading && account.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(Strings.LOGIN)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onRegister) { Text(Strings.NO_ACCOUNT) }
                TextButton(onClick = onForgot) { Text(Strings.FORGOT_PASSWORD) }
            }
            TextButton(onClick = onWebLogin) { Text(Strings.WEBSITE_LOGIN) }
        }
    }
}
