package com.vps.qing.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vps.qing.core.SettingsStore
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
import com.vps.qing.data.repository.HostLine
import com.vps.qing.data.repository.OrderLine
import com.vps.qing.data.repository.SubAccountLine
import com.vps.qing.data.repository.TicketLine
import com.vps.qing.data.repository.TxnLine

/** 账号与服务板块定义。webPath 非空的板块没有原生 API，仍走网页兜底（如实名认证）。 */
data class ServiceMenu(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val soft: Color,
    val panel: ServicePanel? = null,
    val webPath: String = ""
)

/**
 * 「账号与服务」：除实名认证外全部对接站点 API（/host /order /transaction
 * /ticket /sub_account /account）。v1.3.1 起每项独立子页面。
 */
object ServicePanels {
    val entries: List<ServiceMenu> = listOf(
        ServiceMenu(
            "我的主机", "已订购服务器、IP、到期时间", AppIcons.Server,
            CartoonColors.Mint, CartoonColors.MintSoft, panel = ServicePanel.HOSTS
        ),
        ServiceMenu(
            "订单记录", "购买/续费订单与支付状态", AppIcons.Order,
            CartoonColors.Brand, CartoonColors.BrandSoft, panel = ServicePanel.ORDERS
        ),
        ServiceMenu(
            "交易记录", "流水号与支付渠道", AppIcons.Tag,
            CartoonColors.Lemon, CartoonColors.LemonSoft, panel = ServicePanel.TRANSACTIONS
        ),
        ServiceMenu(
            "工单中心", "售后工单列表", AppIcons.Ticket,
            CartoonColors.Sky, CartoonColors.SkySoft, panel = ServicePanel.TICKETS
        ),
        ServiceMenu(
            "子账户管理", "主账户下的子账户", AppIcons.Users,
            CartoonColors.Lilac, CartoonColors.LilacSoft, panel = ServicePanel.SUB_ACCOUNTS
        ),
        ServiceMenu(
            "安全中心", "邮箱/手机绑定与账户状态", AppIcons.Lock,
            CartoonColors.Brand, CartoonColors.BrandSoft, panel = ServicePanel.SECURITY
        )
    )
}

/**
 * 账号与服务独立子页面（v1.3.1）：每个功能一个整页，
 * 顶部磨砂栏带返回键，正文按板块渲染数据列表。
 */
@Composable
fun ServicePageScreen(
    panel: ServicePanel,
    data: ServicePanelData?,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val menu = ServicePanels.entries.firstOrNull { it.panel == panel }
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = menu?.title ?: Strings.ACCOUNT_SERVICES,
                subtitle = menu?.subtitle,
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
                    data == null || data.loading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(color = CartoonColors.Brand, modifier = Modifier.width(120.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("正在从站点读取…", style = MaterialTheme.typography.bodySmall, color = CartoonColors.InkSub)
                        }
                    }
                    data.error != null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                data.error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            CartoonButton(text = Strings.RETRY, onClick = onRefresh)
                        }
                    }
                    else -> {
                        val empty = data.hosts.isEmpty() && data.orders.isEmpty() && data.txns.isEmpty() &&
                            data.tickets.isEmpty() && data.subAccounts.isEmpty() && data.security.isEmpty()
                        if (empty) {
                            EmptyHint(
                                icon = AppIcons.EmptyBox,
                                text = "暂无数据",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(data.hosts, key = { "host-${it.id}" }) { HostLineRow(it) }
                                items(data.orders, key = { "order-${it.id}" }) { OrderLineRow(it) }
                                items(data.txns, key = { "txn-${it.number}-${it.createTime}" }) { TxnLineRow(it) }
                                items(data.tickets, key = { "ticket-${it.id}" }) { TicketLineRow(it) }
                                items(data.subAccounts, key = { "sub-${it.id}" }) { SubAccountLineRow(it) }
                                if (data.security.isNotEmpty()) {
                                    item {
                                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(
                                                Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                data.security.forEach { (label, value) ->
                                                    KeyValueRow(label, value)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 应用设置子页面：主题配色独立整页，选项为通栏单选卡片。 */
@Composable
fun ThemeSettingsPage(
    currentMode: Int,
    onSelectMode: (Int) -> Unit,
    onBack: () -> Unit
) {
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.SETTINGS,
                subtitle = "选择应用的主题配色",
                navIcon = AppIcons.Back,
                onNavClick = onBack
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionTitle(Strings.THEME, CartoonColors.Lilac)
                val options = listOf(
                    Triple(Strings.THEME_SYSTEM, SettingsStore.THEME_SYSTEM, "跟随手机系统的深浅色设置"),
                    Triple(Strings.THEME_LIGHT, SettingsStore.THEME_LIGHT, "始终使用纯白卡通亮色主题"),
                    Triple(Strings.THEME_DARK, SettingsStore.THEME_DARK, "始终使用柔和夜间的深浅主题")
                )
                options.forEach { (label, mode, desc) ->
                    val selected = currentMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CartoonKit.ItemShape)
                            .background(if (selected) CartoonColors.BrandSoft else CartoonColors.White)
                            .clickable { onSelectMode(mode) }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = CartoonColors.Brand)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) CartoonColors.Brand else CartoonColors.Ink
                            )
                            Text(desc, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                        }
                        if (selected) {
                            Icon(AppIcons.Check, null, tint = CartoonColors.Brand, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 状态徽章颜色：Active/Paid 绿色，Unpaid/Pending 珊瑚色，其余灰蓝。 */
@Composable
internal fun statusBadge(status: String) {
    val (bg, fg, text) = when {
        status.equals("Active", true) || status.equals("Paid", true) ->
            Triple(CartoonColors.MintSoft, CartoonColors.Mint, "使用中")
        status.equals("Unpaid", true) || status.equals("Pending", true) ->
            Triple(CartoonColors.PeachSoft, CartoonColors.Coral, "待支付")
        status.isBlank() -> Triple(CartoonColors.BrandSoft, CartoonColors.InkSub, "--")
        else -> Triple(CartoonColors.BrandSoft, CartoonColors.InkSub, status)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .clip(CartoonKit.PillShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
internal fun KeyValueRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, style = MaterialTheme.typography.labelMedium,
            color = CartoonColors.InkSub, modifier = Modifier.width(72.dp)
        )
        Text(
            value, style = MaterialTheme.typography.bodySmall,
            color = CartoonColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun HostLineRow(host: HostLine) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    host.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, color = CartoonColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                statusBadge(host.status)
                Spacer(Modifier.weight(1f))
                Text(
                    "${host.billingCycle} ${host.renewAmount}",
                    style = MaterialTheme.typography.labelSmall, color = CartoonColors.Coral
                )
            }
            Text(
                host.productName, style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (host.baseInfo.isNotBlank()) KeyValueRow("配置", host.baseInfo)
            if (host.ip.isNotBlank()) KeyValueRow("IP", host.ip)
            if (host.imageName.isNotBlank()) KeyValueRow("系统", host.imageName)
            KeyValueRow("到期", host.dueTime.ifBlank { "--" })
        }
    }
}

@Composable
internal fun OrderLineRow(order: OrderLine) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${order.id}", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, color = CartoonColors.Ink
                )
                Spacer(Modifier.width(8.dp))
                statusBadge(order.status)
                Spacer(Modifier.weight(1f))
                Text("￥${order.amount}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Coral)
            }
            if (order.productNames.isNotEmpty()) {
                Text(
                    order.productNames.joinToString("、"),
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            KeyValueRow("时间", "${order.createTime} · ${order.gateway}")
        }
    }
}

@Composable
internal fun TxnLineRow(txn: TxnLine) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("￥${txn.amount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Coral)
                Spacer(Modifier.weight(1f))
                Text(txn.createTime, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            }
            KeyValueRow("渠道", txn.gateway.ifBlank { "--" })
            if (txn.number.isNotBlank()) KeyValueRow("流水", txn.number)
        }
    }
}

@Composable
internal fun TicketLineRow(ticket: TicketLine) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ticket.title.ifBlank { "#${ticket.id}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, color = CartoonColors.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                Text(ticket.createTime, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            }
        }
    }
}

@Composable
internal fun SubAccountLineRow(sub: SubAccountLine) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(sub.username.ifBlank { "#${sub.id}" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                Text(sub.email, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            }
        }
    }
}
