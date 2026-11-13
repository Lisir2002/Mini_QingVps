package com.vps.qing.feature.shop

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.UiT
import com.vps.qing.data.repository.GatewayOption

/**
 * 原生结算支付面板（v1.4.8）：金额确认 → 支付方式选择 → 拉起支付页 + 轮询 → 成功态。
 * 全流程 App 内完成，不再跳站点购物车页。
 */
@Composable
fun CheckoutSheet(
    checkout: CheckoutState,
    onDismiss: () -> Unit,
    onPayWith: (GatewayOption) -> Unit,
    onOpenPayUrl: (String) -> Unit,
    onCheckPayNow: () -> Unit
) {
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = when (checkout.stage) {
                    "SUCCESS" -> "支付成功"
                    "PAYING" -> "等待支付"
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
                // ---- 应付金额 ----
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.85f) {
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
                        if (checkout.hostIds.isNotEmpty()) {
                            Text(
                                "支付开通后将获得 ${checkout.hostIds.size} 台服务",
                                style = MaterialTheme.typography.labelSmall,
                                color = CartoonColors.InkSub
                            )
                        }
                    }
                }

                when (checkout.stage) {
                    // ---- 成功态 ----
                    "SUCCESS" -> {
                        GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.85f) {
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

                    // ---- 等待支付（轮询中） ----
                    "PAYING" -> {
                        GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.85f) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "已拉起支付页面，请在浏览器完成付款",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CartoonColors.Ink
                                )
                                Text(
                                    "支付完成后 App 会自动确认（最长等待 10 分钟）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CartoonColors.InkSub
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = CartoonColors.Brand,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        "正在确认支付结果…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CartoonColors.InkSub
                                    )
                                }
                                CartoonButton(
                                    text = "打开支付页面",
                                    onClick = { checkout.payUrl?.let(onOpenPayUrl) },
                                    icon = AppIcons.Forward,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                CartoonButton(
                                    text = "我已完成支付",
                                    onClick = onCheckPayNow,
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = CartoonColors.Mint
                                )
                            }
                        }
                    }

                    // ---- 选支付方式 ----
                    else -> {
                        if (checkout.gatewaysLoading) {
                            GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.85f) {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = CartoonColors.Brand)
                                }
                            }
                        } else if (checkout.gateways.isEmpty()) {
                            GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.85f) {
                                Text(
                                    "站点未提供在线支付方式，请前往网页完成支付",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CartoonColors.InkSub,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            Text(
                                "选择支付方式",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = CartoonColors.Ink,
                                modifier = Modifier.padding(top = UiT.spaceXs)
                            )
                            checkout.gateways.forEach { gateway ->
                                GatewayRow(
                                    gateway = gateway,
                                    enabled = !checkout.paying,
                                    onClick = { onPayWith(gateway) }
                                )
                            }
                        }
                        checkout.error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
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

@Composable
private fun GatewayRow(gateway: GatewayOption, enabled: Boolean, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CartoonKit.CardShape)
            .clickable(enabled = enabled) { onClick() },
        alpha = 0.85f
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (gateway.iconUrl.isNotBlank()) {
                AsyncImage(
                    model = gateway.iconUrl,
                    contentDescription = gateway.title,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Box(
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
