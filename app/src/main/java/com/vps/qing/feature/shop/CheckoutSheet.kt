package com.vps.qing.feature.shop

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.vps.qing.core.AppContainer
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.QrBitmap
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.UiT
import com.vps.qing.data.repository.GatewayOption
import com.vps.qing.data.repository.PayDeadline
import kotlinx.coroutines.delay

/**
 * 原生结算支付面板（v1.4.8 建立 / v1.4.17 流程重梳与支付方式全动态化）。
 *
 * 链路：加入购物车 → 结算生成订单（确认支付）→ 选择支付方式 → 支付码 / 打开支付页。
 * v1.4.17 要点：
 *  - 顶部新增三步骤进度条，任何阶段都能看清当前所处环节；
 *  - 支付方式列表、名称、图标、提交参数全部来自站点 GET /gateway 实时响应，
 *    App 不再硬编码「支付宝 / 微信支付」等任何网关，站点新增即出现、下线即消失；
 *  - 等待支付页文案 / 图标跟随用户实际选中的网关，不再是写死的「支付宝」；
 *  - 拉起外部应用、复制支付链接分别受权限管理页里的可选开关控制（默认关闭）。
 *
 * v1.4.18 支付真实性修复（用户实测反馈逐条对应）：
 *  - 二维码只展示站点真实返回的图片 / 支付码内容，或原样渲染站点 data.html；
 *    不再把收银台跳转链接本地编码成假二维码；
 *  - 有效期来自订单 unpaid_timeout / remain_pay_time，本地倒计时，不再写死「10 分钟」；
 *  - 「我已完成支付」按响应体顶层 code 判定，并给出成功 / 未支付 / 查询失败三态提示；
 *  - 内置 WebView 处理 alipays:// 等支付深链，修复「打开支付宝不可用」。
 */
@Composable
fun CheckoutSheet(
    checkout: CheckoutState,
    onDismiss: () -> Unit,
    onPayWith: (GatewayOption) -> Unit,
    onOpenPayUrl: (String) -> Unit,
    onCheckPayNow: () -> Unit,
    /** v1.4.17：站点支付方式实时刷新（强制回源，绕过缓存）。 */
    onRefreshGateways: () -> Unit = {},
    /** 权限「打开外部应用」开启后才可用：用系统 Intent 拉起支付应用。 */
    onOpenExternal: (String) -> Unit = {},
    externalAppsEnabled: Boolean = false,
    clipboardEnabled: Boolean = false
) {
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = when (checkout.stage) {
                    "SUCCESS" -> "支付完成"
                    "PAYING" -> "完成支付"
                    else -> "确认支付"
                },
                subtitle = checkout.orderId?.let { "订单号 $it" },
                navIcon = AppIcons.Back,
                onNavClick = onDismiss
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---- 流程进度：加入购物车 → 确认订单 → 选择支付方式 → 支付码/跳转 ----
                CheckoutSteps(checkout.stage)

                // ---- 订单详情（绑定显示：选方式与等待支付两页一致） ----
                if (checkout.stage != "SUCCESS") {
                    OrderSummaryCard(checkout = checkout)
                }

                when (checkout.stage) {
                    // ---- 成功态 ----
                    "SUCCESS" -> {
                        GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    Modifier.size(64.dp).clip(CircleShape).background(CartoonColors.MintSoft),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(AppIcons.Check, null, tint = CartoonColors.Mint, modifier = Modifier.size(34.dp))
                                }
                                Text(
                                    "支付成功",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CartoonColors.Ink
                                )
                                Text(
                                    "服务已开通，可以在「我的」里查看",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CartoonColors.InkSub
                                )
                                CartoonButton(
                                    text = "完成",
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }

                    // ---- 支付码 / 跳转支付 ----
                    "PAYING" -> {
                        PayingCard(
                            checkout = checkout,
                            onOpenPayUrl = onOpenPayUrl,
                            onCheckPayNow = onCheckPayNow,
                            onOpenExternal = onOpenExternal,
                            externalAppsEnabled = externalAppsEnabled,
                            clipboardEnabled = clipboardEnabled
                        )
                    }

                    // ---- 选择支付方式（站点实时参数驱动） ----
                    else -> {
                        GatewayPicker(
                            checkout = checkout,
                            onPayWith = onPayWith,
                            onRefreshGateways = onRefreshGateways
                        )
                        checkout.error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (checkout.paying) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = CartoonColors.Brand,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "正在发起支付…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CartoonColors.InkSub
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 三阶段进度条：让「加入购物车 → 支付」整条链路在任意阶段都可感知。 */
@Composable
private fun CheckoutSteps(stage: String) {
    val current = when (stage) {
        "SUCCESS" -> 3
        "PAYING" -> 2
        else -> 1
    }
    val labels = listOf("确认订单", "选择支付方式", "支付码 / 跳转")
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { idx, label ->
                val step = idx + 1
                val on = step == current
                val done = step < current
                val accent = if (on || done) CartoonColors.Brand else CartoonColors.InkSub
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape)
                            .background(if (on || done) accent else accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (done) {
                            Icon(AppIcons.Check, null, tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(12.dp))
                        } else {
                            Text(
                                "$step",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (on) androidx.compose.ui.graphics.Color.White else accent
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = accent,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 支付方式选择区：列表完全来自站点，支持失败重试与主动实时刷新。 */
@Composable
private fun GatewayPicker(
    checkout: CheckoutState,
    onPayWith: (GatewayOption) -> Unit,
    onRefreshGateways: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = UiT.spaceXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "选择支付方式",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Ink
            )
            Text(
                "以下方式由站点实时提供，共 ${checkout.gateways.size} 种",
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
        }
        CartoonIconChip(
            icon = AppIcons.Refresh,
            onClick = onRefreshGateways,
            modifier = Modifier.size(32.dp),
            tint = CartoonColors.Mint,
            bg = CartoonColors.MintSoft
        )
    }

    when {
        checkout.gatewaysLoading -> {
            GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CartoonColors.Brand)
                }
            }
        }
        checkout.gateways.isEmpty() -> {
            GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        checkout.gatewayError ?: "站点当前未返回可用的在线支付方式",
                        style = MaterialTheme.typography.bodySmall,
                        color = CartoonColors.InkSub
                    )
                    CartoonButton(
                        text = "重新获取支付方式",
                        onClick = onRefreshGateways,
                        icon = AppIcons.Refresh,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        else -> {
            checkout.gateways.forEach { gateway ->
                GatewayRow(
                    gateway = gateway,
                    enabled = !checkout.paying && !checkout.checking,
                    onClick = { onPayWith(gateway) }
                )
            }
        }
    }
}

/** 订单详情卡：金额 + 订单号 + 台数 + 商品名，选方式与等待支付两页共用（绑定显示）。 */
@Composable
private fun OrderSummaryCard(checkout: CheckoutState) {
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "应付金额",
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "¥",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = CartoonColors.Coral
                )
                Text(
                    checkout.amount ?: "--",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = CartoonColors.Coral
                )
            }
            if (checkout.orderId != null) {
                Text(
                    "订单号 #${checkout.orderId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub
                )
            }
            if (checkout.hostIds.isNotEmpty()) {
                Text(
                    "支付开通后将获得 ${checkout.hostIds.size} 台服务",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub
                )
            }
            if (checkout.productNames.isNotEmpty()) {
                Text(
                    checkout.productNames.joinToString("、"),
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub,
                    textAlign = TextAlign.Start
                )
            }
            checkout.selectedGateway?.let {
                Text(
                    "已选支付方式：${it.title}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Brand
                )
            }
        }
    }
}

/**
 * 支付码 / 跳转支付卡（v1.4.18 真实性修复）。
 *
 * 旧版把收银台 `href` 用本地二维码编码器造了个「支付码」，那既不是网页端给的
 * 支付码，扫出来也只是个网页链接；有效期写死「10 分钟」；「我已完成支付」因读不到
 * 顶层 code 永远没结果也没提示。现在：
 *  - 只展示站点**真实返回**的二维码图片 / 支付码内容，否则原样渲染站点 data.html；
 *  - 有效期来自订单 unpaid_timeout / remain_pay_time，本地每秒倒计时；
 *  - 按钮文案取站点支付按钮原文（如「立即支付」），点击打开站点实时收银台；
 *  - 「我已完成支付」给出「成功 / 暂未检测到支付 / 查询失败」三态明确反馈。
 */
@Composable
private fun PayingCard(
    checkout: CheckoutState,
    onOpenPayUrl: (String) -> Unit,
    onCheckPayNow: () -> Unit,
    onOpenExternal: (String) -> Unit,
    externalAppsEnabled: Boolean,
    clipboardEnabled: Boolean
) {
    val context = LocalContext.current
    val gatewayTitle = checkout.selectedGateway?.title?.takeIf { it.isNotBlank() } ?: "所选支付方式"
    val remain = rememberPayRemain(checkout.deadline, checkout.paid, checkout.expired)
    val cashierUrl = checkout.payUrl
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "请使用「$gatewayTitle」完成付款",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Ink,
                textAlign = TextAlign.Center
            )

            PayCodeBlock(checkout = checkout)

            DeadlineHint(checkout = checkout, remain = remain)

            // 主按钮：文案来自站点 data.html，点击在 App 内打开站点实时收银台
            CartoonButton(
                text = checkout.payButtonLabel,
                onClick = {
                    if (cashierUrl.isNullOrBlank()) {
                        Toast.makeText(context, "站点未返回收银台链接，请改用其他支付方式", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "正在打开站点收银台…", Toast.LENGTH_SHORT).show()
                        onOpenPayUrl(cashierUrl)
                    }
                },
                icon = AppIcons.Wallet,
                enabled = !cashierUrl.isNullOrBlank() && !checkout.checking && !checkout.expired,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "将打开站点实时返回的支付页（与网页端「${checkout.payButtonLabel}」一致）",
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub,
                textAlign = TextAlign.Center
            )

            // 可选权限「打开外部应用」：开启后才提供浏览器 / 支付应用入口
            if (externalAppsEnabled && !cashierUrl.isNullOrBlank()) {
                CartoonButton(
                    text = "用浏览器 / 支付应用打开",
                    onClick = {
                        Toast.makeText(context, "正在调用外部应用…", Toast.LENGTH_SHORT).show()
                        onOpenExternal(cashierUrl)
                    },
                    icon = AppIcons.ExternalLink,
                    containerColor = CartoonColors.Lilac,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 可选权限「剪切板」：开启后才提供一键复制支付链接
            if (clipboardEnabled && !cashierUrl.isNullOrBlank()) {
                CartoonButton(
                    text = "复制支付链接",
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("QingVPS 支付链接", cashierUrl))
                        Toast.makeText(context, "支付链接已复制", Toast.LENGTH_SHORT).show()
                    },
                    icon = AppIcons.Globe,
                    containerColor = CartoonColors.Sky,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (!externalAppsEnabled) {
                Text(
                    "需要用浏览器打开或复制支付链接？在「我的 → 权限管理」开启对应可选权限即可。",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub,
                    textAlign = TextAlign.Center
                )
            }

            // 我已完成支付：查询中禁用 + 加载态
            CartoonButton(
                text = if (checkout.checking) "正在确认支付结果…" else "我已完成支付",
                onClick = onCheckPayNow,
                enabled = !checkout.checking,
                modifier = Modifier.fillMaxWidth(),
                containerColor = CartoonColors.Mint,
                trailingContent = if (checkout.checking) {
                    {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else null
            )
            checkout.checkMessage?.let { msg ->
                CheckResultHint(text = msg, success = checkout.checkSuccess)
            }
        }
    }
}

/** 订单真实有效期的本地倒计时：每秒递减，支付成功或已超时即停。 */
@Composable
private fun rememberPayRemain(deadline: PayDeadline?, paid: Boolean, expired: Boolean): Int {
    var remain by remember(deadline?.remainSeconds) {
        mutableIntStateOf(deadline?.remainSeconds ?: 0)
    }
    LaunchedEffect(deadline?.remainSeconds, paid, expired) {
        if (paid || expired || deadline?.hasTimeout != true) return@LaunchedEffect
        while (remain > 0) {
            delay(1000L)
            remain--
        }
    }
    return remain
}

private fun formatRemain(sec: Int): String {
    val d = sec / 86400
    val h = (sec % 86400) / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return buildString {
        if (d > 0) append("${d}天")
        if (d > 0 || h > 0) append("${h}小时")
        if (d > 0 || h > 0 || m > 0) append("${m}分")
        append("${s}秒")
    }
}

/** 有效期提示：不再写死「最长等待 10 分钟」，全部来自订单真实字段。 */
@Composable
private fun DeadlineHint(checkout: CheckoutState, remain: Int) {
    val deadline = checkout.deadline
    val timedOut = checkout.expired || (deadline?.hasTimeout == true && remain <= 0)
    val text = when {
        timedOut -> "订单支付有效期已过，请返回重新选择支付方式发起支付"
        deadline?.hasTimeout == true -> "请在 ${formatRemain(remain)} 内完成支付（站点订单真实有效期）"
        else -> "该订单未设置支付时限，付款后点击下方按钮确认即可"
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (timedOut) FontWeight.Bold else FontWeight.Normal,
        color = if (timedOut) MaterialTheme.colorScheme.error else CartoonColors.InkSub,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    if (!timedOut) {
        Text(
            "App 每 2 秒自动检测支付结果，也可付款后手动确认",
            style = MaterialTheme.typography.labelSmall,
            color = CartoonColors.InkSub,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 支付码区：只展示**站点真实返回**的内容。
 * 优先级：真实二维码图片 → 真实支付码内容 → 原样渲染站点 data.html（网页端 #payBox）。
 * 站点若只给了「立即支付」跳转按钮（易支付收银台实测如此），App 不再伪造二维码。
 */
@Composable
private fun PayCodeBlock(checkout: CheckoutState) {
    val qrImage = checkout.qrImageUrl
    val qrContent = checkout.qrContent
    when {
        !qrImage.isNullOrBlank() -> {
            val local = remember(qrImage) { decodeDataImage(qrImage) }
            Box(
                Modifier.size(200.dp).clip(CartoonKit.CardShape).background(Color.White).padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (local != null) {
                    Image(bitmap = local, contentDescription = "站点支付二维码", modifier = Modifier.fillMaxSize())
                } else {
                    AsyncImage(
                        model = qrImage,
                        contentDescription = "站点支付二维码",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                "以上支付码由站点实时返回",
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub,
                textAlign = TextAlign.Center
            )
        }
        !qrContent.isNullOrBlank() -> {
            // 这里编码的是站点给出的真实支付码内容，不是收银台跳转链接
            val qr = remember(qrContent) { QrBitmap.encode(qrContent) }
            Box(
                Modifier.size(200.dp).clip(CartoonKit.CardShape).background(Color.White).padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qr != null) {
                    Image(bitmap = qr, contentDescription = "站点支付码", modifier = Modifier.fillMaxSize())
                } else {
                    Text(
                        "支付码生成失败，请点击「${checkout.payButtonLabel}」在收银台付款",
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Text(
                "以上支付码由站点实时返回",
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub,
                textAlign = TextAlign.Center
            )
        }
        checkout.payHtml.isNotBlank() -> {
            SitePayHtml(html = checkout.payHtml)
            Text(
                "以上支付入口由站点实时返回（与网页端一致），可直接点击",
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub,
                textAlign = TextAlign.Center
            )
        }
        else -> {
            GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
                Text(
                    "站点本次未返回静态支付码，请点击「${checkout.payButtonLabel}」在收银台完成付款",
                    style = MaterialTheme.typography.bodySmall,
                    color = CartoonColors.InkSub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
    }
}

/**
 * 原样渲染站点 `data.html`（网页端 `$("#payBox").html(...)` 同款内容）。
 * 关键：内置 WebView 必须把 `alipays://` / `weixin://` 等支付深链交给系统处理，
 * 否则收银台点击「立即支付」会报 ERR_UNKNOWN_URL_SCHEME，即用户反馈的
 * 「打开支付宝不可用」。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SitePayHtml(html: String) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(CartoonKit.CardShape)
            .background(Color.White),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportZoom(false)
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val uri = request.url ?: return false
                        val scheme = uri.scheme.orEmpty()
                        if (scheme.equals("http", true) || scheme.equals("https", true)) return false
                        // 支付深链：交给系统浏览器 / 支付宝 / 微信处理
                        runCatching {
                            view.context.startActivity(
                                Intent(Intent.ACTION_VIEW, uri)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        return true
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                val doc = "<!doctype html><html><head>" +
                    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                    "<style>body{margin:0;padding:14px;font-family:sans-serif;text-align:center}" +
                    "img{max-width:100%;height:auto}" +
                    "a{display:inline-block;padding:10px 20px;border-radius:999px;" +
                    "background:#4C7CF3;color:#fff;text-decoration:none;font-weight:bold}" +
                    "</style></head><body>$html</body></html>"
                view.loadDataWithBaseURL(AppContainer.SITE_ORIGIN, doc, "text/html", "UTF-8", null)
            }
        }
    )
}

/** 「我已完成支付」三态反馈：成功 / 暂未检测到支付 / 查询失败，绝不再静默无反应。 */
@Composable
private fun CheckResultHint(text: String, success: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CartoonKit.FieldShape)
            .background(if (success) CartoonColors.MintSoft else CartoonColors.PeachSoft)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (success) AppIcons.Check else AppIcons.Info,
            null,
            tint = if (success) CartoonColors.Mint else CartoonColors.Coral,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = CartoonColors.Ink,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 站点返回的图标 / 二维码是 data:image/png;base64,...，Coil 不直接支持 data URI。
 * 这里原样解码成 Bitmap 显示，保证保真（不做任何本地替换）。
 */
private fun decodeDataImage(src: String): ImageBitmap? {
    if (!src.startsWith("data:image", ignoreCase = true)) return null
    val base64 = src.substringAfter("base64,", "").takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bitmap?.asImageBitmap()
    }.getOrNull()
}

@Composable
private fun GatewayRow(gateway: GatewayOption, enabled: Boolean, onClick: () -> Unit) {
    val icon = remember(gateway.iconUrl) { decodeDataImage(gateway.iconUrl) }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CartoonKit.CardShape)
            .clickable(enabled = enabled) { onClick() },
        alpha = UiT.glassCard
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                icon != null -> Image(
                    bitmap = icon,
                    contentDescription = gateway.title,
                    modifier = Modifier.size(28.dp)
                )
                gateway.iconUrl.startsWith("http", ignoreCase = true) -> AsyncImage(
                    model = gateway.iconUrl,
                    contentDescription = gateway.title,
                    modifier = Modifier.size(28.dp)
                )
                else -> Box(
                    Modifier.size(26.dp).clip(CircleShape).background(CartoonColors.BrandSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Wallet, null, tint = CartoonColors.Brand, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                gateway.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Ink,
                modifier = Modifier.weight(1f)
            )
            Icon(AppIcons.Forward, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
        }
    }
}
