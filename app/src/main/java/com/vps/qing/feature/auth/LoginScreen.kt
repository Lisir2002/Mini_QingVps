package com.vps.qing.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.vps.qing.core.ui.Strings

/** 登录页：只保留「邮箱 + 密码」，纯白卡通 + 磨砂玻璃。 */
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

    LaunchedEffect(state.success) {
        if (state.success) {
            vm.consumeSuccess()
            onSuccess(state.autoLogin)
        }
    }

    CartoonBackground {
        // 边到边（targetSdk 35）：内容避开状态栏
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 卡通 Logo
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CartoonKit.CardShape)
                    .background(CartoonColors.BrandSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Cloud, null, tint = CartoonColors.Brand, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(16.dp))

            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = CartoonKit.CardShape,
                alpha = 0.82f,
                elevation = 12.dp
            ) {
                Column(
                    Modifier.padding(20.dp),
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
                    Spacer(Modifier.height(4.dp))

                    AuthTextField(
                        value = account,
                        onValueChange = { account = it; vm.clearMessages() },
                        label = Strings.EMAIL,
                        icon = AppIcons.Mail,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                    AuthTextField(
                        value = password,
                        onValueChange = { password = it; vm.clearMessages() },
                        label = Strings.PASSWORD,
                        icon = AppIcons.Lock,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        password = true
                    )

                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    state.info?.let {
                        Text(it, color = CartoonColors.Mint, style = MaterialTheme.typography.bodySmall)
                    }

                    CartoonButton(
                        text = Strings.LOGIN,
                        onClick = { vm.loginByPassword(account.trim(), password) },
                        enabled = !state.loading && account.isNotBlank() && password.isNotBlank(),
                        icon = if (state.loading) null else AppIcons.Forward,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = CartoonColors.Brand)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onRegister) { Text(Strings.NO_ACCOUNT, color = CartoonColors.Brand) }
                        TextButton(onClick = onForgot) { Text(Strings.FORGOT_PASSWORD, color = CartoonColors.InkSub) }
                    }
                }
            }
        }
    }
}

/** 认证页通用输入框（磨砂玻璃背景 + 自绘图标）。 */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = CartoonColors.Brand, modifier = Modifier.size(20.dp)) },
        singleLine = true,
        shape = CartoonKit.PillShape,
        modifier = modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CartoonColors.Brand,
            unfocusedBorderColor = CartoonColors.Line,
            focusedContainerColor = CartoonColors.White,
            unfocusedContainerColor = CartoonColors.White.copy(alpha = 0.8f),
            focusedLabelColor = CartoonColors.Brand,
            cursorColor = CartoonColors.Brand
        )
    )
}
