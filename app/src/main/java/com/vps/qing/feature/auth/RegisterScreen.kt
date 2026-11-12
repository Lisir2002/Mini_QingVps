package com.vps.qing.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.AuthBodies

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

    LaunchedEffect(Unit) { vm.loadCaptcha() }
    LaunchedEffect(state.success) {
        if (state.success) {
            vm.consumeSuccess()
            onSuccess(state.autoLogin)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(Strings.REGISTER, style = MaterialTheme.typography.headlineMedium)
            Text(
                "本站仅开放邮箱注册，需先完成图形验证码再获取邮箱验证码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; vm.clearMessages() },
                label = { Text(Strings.EMAIL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; vm.clearMessages() },
                label = { Text(Strings.PASSWORD) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it; vm.clearMessages() },
                label = { Text(Strings.CONFIRM_PASSWORD) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )

            Text(
                Strings.CAPTCHA_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(onClick = { vm.sendEmailCode(email.trim(), AuthBodies.ACTION_REGISTER) }) {
                    Text(Strings.SEND_CODE)
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
                    if (password != confirm) {
                        vm.showInfo("两次输入的密码不一致")
                    } else {
                        vm.register(email.trim(), password, confirm, code.trim())
                    }
                },
                enabled = !state.loading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(Strings.REGISTER)
                }
            }

            TextButton(onClick = onWebRegister, modifier = Modifier.fillMaxWidth()) {
                Text(Strings.WEBSITE_REGISTER)
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(Strings.HAS_ACCOUNT)
            }
        }
    }
}
