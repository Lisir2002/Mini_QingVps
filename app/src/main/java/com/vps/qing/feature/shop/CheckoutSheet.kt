package com.vps.qing.feature.shop

import android.content.ClipData
import android.content.Context
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
 * 支付码 / 跳转支付卡。文案与图标跟随用户实际选中的站点网关，
 * 不存在任何写死的「支付宝 / 微信支付」字样。
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
    GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "请使用「$gatewayTitle」扫码完成付款",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Ink,
                textAlign = TextAlign.Center
            )

            // 二维码：把站点返回的支付深链编码成本地二维码，直接展示在按钮上方
            val qr: ImageBitmap? = remember(checkout.payUrl) { QrBitmap.encode(checkout.payUrl) }
            Box(
                Modifier
                    .size(200.dp)
                    .clip(CartoonKit.CardShape)
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qr != null) {
                    Image(
                        bitmap = qr,
                        contentDescription = "支付二维码",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        "二维码生成失败，请点击下方按钮打开支付页面",
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Text(
                "支付完成后 App 会自动确认（最长等待 10 分钟）",
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub,
                textAlign = TextAlign.Center
            )

            // 应用内打开支付页：始终可用，不受外部应用权限影响
            CartoonButton(
                text = "打开支付页面",
                onClick = { checkout.payUrl?.let(onOpenPayUrl) },
                icon = AppIcons.Wallet,
                enabled = checkout.payUrl != null && !checkout.checking,
                modifier = Modifier.fillMaxWidth()
            )

            // 可选权限「打开外部应用」：开启后才提供拉起支付 App / 浏览器的入口
            if (externalAppsEnabled) {
                CartoonButton(
                    text = "用外部应用打开",
                    onClick = { checkout.payUrl?.let(onOpenExternal) },
                    icon = AppIcons.ExternalLink,
                    enabled = checkout.payUrl != null,
                    containerColor = CartoonColors.Lilac,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 可选权限「剪切板」：开启后才提供一键复制支付链接
            if (clipboardEnabled) {
                CartoonButton(
                    text = "复制支付链接",
                    onClick = {
                        val url = checkout.payUrl ?: return@CartoonButton
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("QingVPS 支付链接", url))
                        Toast.makeText(context, "支付链接已复制", Toast.LENGTH_SHORT).show()
                    },
                    icon = AppIcons.Globe,
                    enabled = checkout.payUrl != null,
                    containerColor = CartoonColors.Sky,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (!externalAppsEnabled) {
                Text(
                    "需要拉起支付应用或复制支付链接？在「我的 → 权限管理」开启对应可选权限即可。",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub,
                    textAlign = TextAlign.Center
                )
            }

            // 我已完成支付：查询中禁用 + 加载态，给出回应式反馈
            CartoonButton(
                text = if (checkout.checking) "正在确认支付结果…" else "我已完成支付",
                onClick = onCheckPayNow,
                enabled = !checkout.checking,
                modifier = Modifier.fillMaxWidth(),
                containerColor = CartoonColors.Mint,
                trailingContent = if (checkout.checking) {
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
}

/**
 * 站点返回的图标是 data:image/png;base64,...，Coil 不直接支持 data URI。
 * 这里原样解码成 Bitmap 显示，保证网关图标保真（不做任何本地图标替换）。
 */
private fun decodeGatewayIcon(iconUrl: String): ImageBitmap? {
    if (!iconUrl.startsWith("data:image", ignoreCase = true)) return null
    val base64 = iconUrl.substringAfter("base64,", "").takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bitmap?.asImageBitmap()
    }.getOrNull()
}

@Composable
private fun GatewayRow(gateway: GatewayOption, enabled: Boolean, onClick: () -> Unit) {
    val icon = remember(gateway.iconUrl) { decodeGatewayIcon(gateway.iconUrl) }
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
