package com.vps.qing.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vps.qing.core.EmailCodeCooldown
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.glassField
import com.vps.qing.core.ui.theme.CartoonColors

/**
 * 认证页通用输入框（v1.4.18 重写）。
 *
 * 旧实现用 Material3 `OutlinedTextField` 的浮动 `label` + 50% 全圆角（`PillShape`）：
 * 聚焦时 label 会浮到轮廓缺口处，配合大圆角直接溢出到框外被裁（截图里「密码」
 * 跑到框外上方、锁图标孤立在框内），输入文字左侧也贴着圆弧。
 *
 * 现在改为 `BasicTextField` + 自绘 `decorationBox`：
 *  - 用 **placeholder 代替 label**，提示文字永远在框内，不存在浮动错位；
 *  - 圆角收敛为 [CartoonKit.FieldShape]（18dp），文字/图标有 16dp 水平内边距；
 *  - 图标、placeholder、输入区、密码显隐按钮同一行垂直居中，互不重叠；
 *  - 聚焦 / 错误态用描边给明确反馈，玻璃磨砂质感保持不变。
 */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    password: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    focusRequester: FocusRequester? = null,
    onImeAction: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val shape = CartoonKit.FieldShape
    val stroke = when {
        isError -> CartoonColors.Coral
        focused -> CartoonColors.Brand
        else -> CartoonColors.Brand.copy(alpha = 0.14f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .glassField(shape)
            .border(width = if (focused || isError) 1.5.dp else 1.dp, color = stroke, shape = shape)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = CartoonColors.Ink),
            visualTransformation = if (password && !passwordVisible)
                PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            cursorBrush = SolidColor(CartoonColors.Brand),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon, null,
                        tint = if (focused) CartoonColors.Brand else CartoonColors.InkSub,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CartoonColors.InkSub,
                                maxLines = 1
                            )
                        }
                        inner()
                    }
                    if (password) {
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (passwordVisible) AppIcons.Eye else AppIcons.EyeClosed,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                tint = CartoonColors.InkSub,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

/**
 * v1.4.18：全 App 统一的「获取验证码」按钮。
 *
 * 冷却状态来自 [EmailCodeCooldown]（站点全局 60s 限频 + 持久化时间戳），
 * 点击发送成功后所有页面同时进入禁用倒计时，结束后自动恢复可点。
 * 无论成功或失败都不会出现「按钮常亮可反复点」的情况：失败时由 ViewModel
 * 根据站点错误决定是否锁定冷却，界面始终有明确反馈。
 */
@Composable
fun SendCodeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    enabled: Boolean = true
) {
    val remaining by EmailCodeCooldown.remaining.collectAsState()
    val cooling = remaining > 0
    CartoonButton(
        text = when {
            busy -> "发送中…"
            cooling -> "重新获取 (${remaining}s)"
            else -> Strings.SEND_CODE
        },
        onClick = onClick,
        enabled = enabled && !busy && !cooling,
        modifier = modifier,
        containerColor = CartoonColors.Mint,
        // 与相邻输入框（最小 56dp）等高，避免一行内高低错位
        height = 56.dp
    )
}

/** v1.4.18：认证页统一的结果提示条（错误红 / 成功绿），带图标与柔和底色，不再是一行裸文字。 */
@Composable
fun AuthMessage(text: String, isError: Boolean) {
    val tint = if (isError) CartoonColors.Coral else CartoonColors.Mint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CartoonKit.FieldShape)
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isError) AppIcons.Info else AppIcons.Check,
            null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}
