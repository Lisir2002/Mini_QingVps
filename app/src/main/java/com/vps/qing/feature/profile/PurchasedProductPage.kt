package com.vps.qing.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.EmptyHint
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.data.repository.PurchasedDetail
import androidx.compose.foundation.lazy.rememberLazyListState
import com.vps.qing.core.ui.ScrollGlassEffect

/**
 * 已购产品详情（v1.4.2）：展示「已经订购的配置信息 + 支付信息」，不是购买页。
 * 数据来自 GET /host/{id}、GET /remf_cloud/{id}、GET /host/{id}/specific_info。
 * 对应网页产品管理页的「配置信息」「支付信息」两张卡片（见用户截图）。
 */
@Composable
fun PurchasedProductPage(
    hostId: String,
    state: ProductDetailUi,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenOrder: (String) -> Unit = {}
) {
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "已购产品详情",
                subtitle = state.detail?.name?.takeIf { it.isNotBlank() } ?: "#$hostId",
                navIcon = AppIcons.Back,
                onNavClick = onBack,
                actions = {
                    CartoonIconChip(
                        icon = AppIcons.Refresh,
                        onClick = onRefresh,
                        tint = CartoonColors.Mint,
                        bg = CartoonColors.MintSoft
                    )
                }
            )
            Box(Modifier.fillMaxSize()) {
                when {
                    state.loading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(color = CartoonColors.Brand, modifier = Modifier.width(120.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("正在读取已购产品信息…", style = MaterialTheme.typography.bodySmall, color = CartoonColors.InkSub)
                        }
                    }
                    state.error != null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            CartoonButton(text = Strings.RETRY, onClick = onRefresh)
                        }
                    }
                    state.detail == null -> {
                        EmptyHint(icon = AppIcons.EmptyBox, text = "产品不存在", modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        ProductDetailBody(d = state.detail, onOpenOrder = onOpenOrder)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductDetailBody(d: PurchasedDetail, onOpenOrder: (String) -> Unit) {
    /** 站点返回明文密码，默认掩码；点击行可切换显示（对齐网页端眼睛图标）。 */
    var showPassword by remember { mutableStateOf(false) }

    val glassListState = rememberLazyListState()
    ScrollGlassEffect(glassListState)
    LazyColumn(
        state = glassListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 概览：产品名 / 主机标识 / 状态 / 电源 / IP / 地区
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(CartoonKit.ItemShape).background(CartoonColors.BrandSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Server, null, tint = CartoonColors.Brand, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                d.productName.ifBlank { "已购产品 #${d.id}" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = CartoonColors.Ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "主机标识 ${d.name.ifBlank { "--" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CartoonColors.InkSub
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        statusBadge(d.status)
                        Spacer(Modifier.width(8.dp))
                        powerBadge(d.powerStatus)
                    }
                    if (d.ip.isNotBlank()) KeyValueRow("IP 地址", d.ip)
                    val region = listOf(d.country, d.city, d.area).filter { it.isNotBlank() }
                        .distinct().joinToString(" · ")
                    if (region.isNotBlank()) KeyValueRow("所在地区", region)
                }
            }
        }

        // 配置信息（图二第一张卡片）
        item { SectionTitle("配置信息", CartoonColors.Brand) }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (!d.hasCloudConfig) {
                        Text(
                            "该商品未提供实例配置接口，仅展示订单与支付信息",
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    if (d.cpu.isNotBlank()) KeyValueRow("CPU", "${d.cpu} 核")
                    if (d.memory.isNotBlank()) KeyValueRow("内存", "${d.memory} GB")
                    KeyValueRow("GPU", d.gpu.ifBlank { "无" })
                    if (d.bw.isNotBlank()) KeyValueRow("带宽", "${d.bw} Mbps")
                    if (d.systemDisk.isNotBlank()) KeyValueRow("系统盘", "${d.systemDisk} GB")
                    KeyValueRow(
                        "数据盘",
                        if (d.dataDiskCount.isNotBlank() && d.dataDiskCount != "0") {
                            "${d.dataDiskCount} 块 / ${d.dataDiskSize} GB"
                        } else "无"
                    )
                    KeyValueRow("IPv4 数量", if (d.ipNum.isNotBlank()) "${d.ipNum} 个" else "无")
                    KeyValueRow("IPv6 数量", if (d.ipv6Num.isNotBlank() && d.ipv6Num != "0") "${d.ipv6Num} 个" else "无")
                    if (d.imageName.isNotBlank()) KeyValueRow("操作系统", d.imageName)
                    if (d.lineName.isNotBlank()) KeyValueRow("线路", d.lineName)
                    if (d.securityGroup.isNotBlank()) KeyValueRow("安全组", d.securityGroup)
                    if (d.baseInfo.isNotBlank()) KeyValueRow("基础配置", d.baseInfo)
                    if (d.port.isNotBlank()) KeyValueRow("端口", d.port)
                    if (d.username.isNotBlank()) KeyValueRow("用户名", d.username)
                    // 密码：默认掩码，点击切换
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CartoonKit.ItemShape)
                            .clickable { showPassword = !showPassword }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "密码", style = MaterialTheme.typography.labelMedium,
                            color = CartoonColors.InkSub, modifier = Modifier.width(72.dp)
                        )
                        Text(
                            if (showPassword) d.password.ifBlank { "--" } else "••••••••",
                            style = MaterialTheme.typography.bodySmall,
                            color = CartoonColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (showPassword) "隐藏" else "显示",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CartoonColors.Brand
                        )
                    }
                }
            }
        }

        // 支付信息（图二第二张卡片）
        item { SectionTitle("支付信息", CartoonColors.Lilac) }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    KeyValueRow("付款类型", d.billingCycleName.ifBlank { payTypeText(d.payType) })
                    KeyValueRow("首次订购金额", money(d.firstPaymentAmount))
                    KeyValueRow("下次续费金额", money(d.renewAmount))
                    KeyValueRow("开通时间", d.activeTime.ifBlank { "--" })
                    KeyValueRow("下次续费时间", d.dueTime.ifBlank { "--" })
                    KeyValueRow("自动续费", if (d.autoRenew) "已开启" else "未开启")
                    KeyValueRow("优惠码", "--")
                }
            }
        }

        // 关联订单（与订单详情页双向打通）
        if (d.orderId.isNotBlank()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CartoonKit.ItemShape)
                            .clickable { onOpenOrder(d.orderId) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Order, null, tint = CartoonColors.Lilac, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "关联订单 #${d.orderId}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = CartoonColors.Ink
                            )
                            Text(
                                "点击查看订单详情 →",
                                style = MaterialTheme.typography.labelSmall,
                                color = CartoonColors.Brand
                            )
                        }
                        Icon(AppIcons.Forward, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

/** 电源状态徽章：on=运行中，off=已关机。 */
@Composable
private fun powerBadge(status: String) {
    val on = status.equals("on", true)
    val (bg, fg) = if (on) {
        CartoonColors.MintSoft to CartoonColors.Mint
    } else {
        CartoonColors.PeachSoft to CartoonColors.Coral
    }
    Text(
        if (status.isBlank()) "电源状态未知" else if (on) "运行中" else "已关机",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .clip(CartoonKit.PillShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

private fun money(v: String): String = if (v.isBlank()) "--" else "￥$v"

private fun payTypeText(type: String): String = when (type) {
    "recurring_prepayment" -> "预付款循环"
    "free" -> "免费"
    "on_demand" -> "按需计费"
    "once" -> "一次性"
    else -> type.ifBlank { "--" }
}
