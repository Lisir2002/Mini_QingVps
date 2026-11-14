package com.vps.qing.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.feature.auth.CaptchaDialog
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.ScrollGlassEffect
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 安全中心（v1.4.14）：实名认证已合并进来，明确区分「可编辑」与「只读」。
 *  - 实名认证：状态卡 + 入口（点击进入原生认证整页，v1.4.14 从个人中心独立项移入）；
 *  - 可编辑：登录密码、邮箱绑定、手机绑定（点击进入原生编辑页，含验证码流程）；
 *  - 只读：余额、信用额度（站点不允许在客户端修改，明确标注）。
 */
@Composable
fun SecurityBody(
    data: ServicePanelData,
    onEdit: (String) -> Unit,
    certInfo: com.vps.qing.data.repository.CertInfo? = null,
    onOpenCert: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rows = data.security.associate { it.first to it.second }
    val email = rows["邮箱绑定"].orEmpty()
    val phone = rows["手机绑定"].orEmpty()
    val balance = rows["余额"].orEmpty()
    val credit = rows["信用额度"].orEmpty()

    val certStatus = when {
        certInfo == null -> "状态加载中…"
        certInfo.isCertified -> "已完成实名认证"
        else -> "尚未完成实名认证"
    }
    val certAction = if (certInfo?.isCertified == true) "查看" else "去认证"

    val scroll = rememberScrollState()
    ScrollGlassEffect(scroll)
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(horizontal = UiT.pagePad)
            .padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- 实名认证（v1.4.14 合入安全中心）----
        SectionTitle("账号实名", CartoonColors.Mint)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 6.dp)) {
                SecurityActionRow(
                    icon = AppIcons.Shield,
                    tint = CartoonColors.Mint,
                    soft = CartoonColors.MintSoft,
                    title = "实名认证",
                    value = certStatus,
                    action = certAction,
                    onClick = onOpenCert
                )
            }
        }

        SectionTitle("账号安全 · 可编辑", CartoonColors.Brand)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 6.dp)) {
                SecurityActionRow(
                    icon = AppIcons.Lock,
                    tint = CartoonColors.Brand,
                    soft = CartoonColors.BrandSoft,
                    title = "登录密码",
                    value = "••••••••",
                    action = "修改",
                    onClick = { onEdit("password") }
                )
                SecurityActionRow(
                    icon = AppIcons.Mail,
                    tint = CartoonColors.Sky,
                    soft = CartoonColors.SkySoft,
                    title = "邮箱绑定",
                    value = email,
                    action = if (email.isNotBlank() && email != "未绑定") "换绑" else "绑定",
                    onClick = { onEdit("email") }
                )
                SecurityActionRow(
                    icon = AppIcons.Profile,
                    tint = CartoonColors.Mint,
                    soft = CartoonColors.MintSoft,
                    title = "手机绑定",
                    value = phone,
                    action = if (phone.isNotBlank() && phone != "未绑定") "换绑" else "绑定",
                    onClick = { onEdit("phone") }
                )
            }
        }

        SectionTitle("账户资产 · 只读", CartoonColors.Lilac)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadOnlyRow("余额", balance.ifBlank { "--" })
                ReadOnlyRow("信用额度", credit.ifBlank { "--" })
                Text(
                    "以上信息由站点账务系统维护，App 内不可修改",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub
                )
            }
        }
    }
}

@Composable
private fun SecurityActionRow(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    soft: androidx.compose.ui.graphics.Color,
    title: String,
    value: String,
    action: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CartoonKit.ItemShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CartoonKit.PillShape).background(soft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(UiT.chipIcon))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
            Text(value.ifBlank { "未设置" }, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
        }
        Text(action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Brand)
        Spacer(Modifier.width(4.dp))
        Icon(AppIcons.Forward, null, tint = CartoonColors.Brand, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CartoonColors.InkSub)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(CartoonKit.PillShape)
                    .background(CartoonColors.BrandSoft)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text("只读", style = MaterialTheme.typography.labelSmall, color = CartoonColors.Brand)
            }
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
    }
}

/** 安全项编辑整页：密码 / 邮箱 / 手机，含「验证原绑定 → 设置新绑定」两步。 */
@Composable
fun SecurityEditPage(
    vm: ProfileViewModel,
    target: String,
    onBack: () -> Unit
) {
    val state by vm.ui.collectAsState()
    val current = when (target) {
        "email" -> state.account?.email.orEmpty()
        "phone" -> state.account?.phone.orEmpty()
        else -> ""
    }
    val bound = current.isNotBlank()
    val title = when (target) {
        "password" -> "修改登录密码"
        "email" -> if (bound) "换绑邮箱" else "绑定邮箱"
        else -> if (bound) "换绑手机" else "绑定手机"
    }

    // step 0 = 验证当前绑定；step 1 = 设置新值（密码页直接是 step 1）
    var step by remember { mutableStateOf(if (target != "password" && bound) 0 else 1) }
    var value by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var repeatPwd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    /** 发码成功后的 60s 重发倒计时（对齐站点 countDownButton）。 */
    var countdown by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val captchaState by vm.captcha.state.collectAsState()

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    fun toast(text: String) {
        android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * v1.4.13：站点 captcha_client_verify / captcha_client_update=1，
     * 发码前必须先过原生图形验证码。之前直接请求 /phone/code、/email/code，
     * 服务端必定回 400「请输入图形验证码」，绑定手机/邮箱功能实际不可用。
     */
    fun sendCode() {
        val newValue = if (step == 0) current else value
        if (step == 1 && target != "password") {
            val valid = if (target == "email") newValue.contains("@") else newValue.length == 11
            if (!valid) {
                error = if (target == "email") "请输入正确的邮箱地址" else "请输入 11 位手机号"
                return
            }
        }
        error = null; info = null; busy = true
        vm.requestSecurityCode(
            target = target,
            action = if (step == 0) "verify" else "update",
            value = newValue
        ) { err ->
            busy = false
            if (err != null) {
                error = err
            } else {
                info = "验证码已发送，请查收"
                countdown = 60
            }
        }
    }

    fun submit() {
        error = null; info = null
        if (target == "password") {
            when {
                oldPwd.isBlank() || newPwd.isBlank() -> { error = "请填写原密码与新密码"; return }
                newPwd.length !in 6..32 -> { error = "新密码需 6–32 位"; return }
                newPwd != repeatPwd -> { error = "两次输入的新密码不一致"; return }
            }
            busy = true
            scope.launch {
                val err = vm.submitSecurityPassword(oldPwd, newPwd, repeatPwd)
                busy = false
                if (err != null) error = err else {
                    toast("密码已修改，请重新登录")
                    onBack()
                }
            }
            return
        }
        if (code.length != 6) { error = "请输入 6 位验证码"; return }
        busy = true
        scope.launch {
            val err = if (step == 0) {
                vm.verifyOldContact(target, code)
            } else {
                val okValue = value.trim()
                val valid = if (target == "email") okValue.contains("@") else okValue.length == 11
                if (!valid) {
                    busy = false
                    error = if (target == "email") "请输入正确的邮箱地址" else "请输入 11 位手机号"
                    return@launch
                }
                vm.submitSecurityContact(target, okValue, code)
            }
            busy = false
            if (err != null) error = err
            else if (step == 0) {
                step = 1
                code = ""
                countdown = 0
                info = "验证通过，请设置新的${if (target == "email") "邮箱" else "手机号"}"
            } else {
                toast("已保存")
                onBack()
            }
        }
    }

    CartoonBackground {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(
                title = title,
                subtitle = if (target == "password") "修改后需重新登录" else "按站点安全流程验证后生效",
                navIcon = AppIcons.Back,
                onNavClick = onBack
            )
            val scroll = rememberScrollState()
            ScrollGlassEffect(scroll)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = UiT.pagePad)
                    .padding(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (target == "password") {
                            SecurityField("原密码", oldPwd, { oldPwd = it }, "请输入当前密码", isPassword = true, enabled = !busy)
                            SecurityField("新密码", newPwd, { newPwd = it }, "6–32 位", isPassword = true, enabled = !busy)
                            SecurityField("确认新密码", repeatPwd, { repeatPwd = it }, "再次输入新密码", isPassword = true, enabled = !busy)
                        } else if (step == 0) {
                            Text(
                                "当前绑定：${maskContact(target, current)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = CartoonColors.Ink
                            )
                            Text(
                                "为保障账号安全，请先验证当前绑定",
                                style = MaterialTheme.typography.labelSmall,
                                color = CartoonColors.InkSub
                            )
                            SecurityField("验证码", code, { code = it.filter { c -> c.isDigit() }.take(6) }, "6 位数字验证码", enabled = !busy, keyboardType = KeyboardType.Number)
                            CartoonButton(
                                text = when {
                                    countdown > 0 -> "重新获取 (${countdown}s)"
                                    busy -> "处理中…"
                                    else -> "获取验证码"
                                },
                                onClick = { sendCode() },
                                enabled = !busy && countdown == 0,
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = CartoonColors.Mint
                            )
                        } else {
                            SecurityField(
                                if (target == "email") "新邮箱地址" else "新手机号",
                                value, { value = it },
                                if (target == "email") "name@example.com" else "11 位手机号",
                                enabled = !busy,
                                keyboardType = if (target == "email") KeyboardType.Email else KeyboardType.Phone
                            )
                            SecurityField("验证码", code, { code = it.filter { c -> c.isDigit() }.take(6) }, "6 位数字验证码", enabled = !busy, keyboardType = KeyboardType.Number)
                            CartoonButton(
                                text = when {
                                    countdown > 0 -> "重新获取 (${countdown}s)"
                                    busy -> "处理中…"
                                    else -> "获取验证码"
                                },
                                onClick = { sendCode() },
                                enabled = !busy && countdown == 0,
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = CartoonColors.Mint
                            )
                        }

                        error?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = CartoonColors.Coral)
                        }
                        info?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = CartoonColors.Mint)
                        }

                        CartoonButton(
                            text = when {
                                busy -> "处理中…"
                                target == "password" -> "确认修改"
                                step == 0 -> "下一步"
                                else -> "保存"
                            },
                            onClick = { if (!busy) submit() },
                            enabled = !busy,
                            icon = AppIcons.Check,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Text(
                    "点击「获取验证码」后会在 App 内弹出图形验证码，验证通过才发送短信/邮件验证码；" +
                        "服务端返回的任何错误都会原样显示在上方。",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub
                )
            }
        }
        if (captchaState.visible) {
            CaptchaDialog(
                challenge = captchaState.challenge,
                loading = captchaState.loading,
                error = captchaState.error,
                onSubmit = { vm.captcha.submit(it) },
                onRefresh = { vm.captcha.refresh() },
                onDismiss = {
                    vm.captcha.dismiss()
                    busy = false
                }
            )
        }
    }
}

private fun maskContact(target: String, value: String): String = when {
    value.isBlank() -> "未绑定"
    target == "email" && value.contains("@") -> {
        val (name, domain) = value.split("@", limit = 2)
        "${name.take(2)}***@$domain"
    }
    value.length >= 7 -> "${value.take(3)}****${value.takeLast(4)}"
    else -> value
}

@Composable
private fun SecurityField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
        Spacer(Modifier.height(5.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall, color = CartoonColors.InkSub) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
        )
    }
}
