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
import com.vps.qing.core.AppContainer
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
import com.vps.qing.core.ui.theme.ThemePalettes
import com.vps.qing.data.repository.CertInfo
import com.vps.qing.data.repository.CertPlugin
import com.vps.qing.data.repository.CertRecord
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
    /** 糖果色索引（组合期解析，主题切换即刷新）。 */
    val accentIdx: Int,
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
            0, panel = ServicePanel.HOSTS
        ),
        ServiceMenu(
            "订单记录", "购买/续费订单与支付状态", AppIcons.Order,
            5, panel = ServicePanel.ORDERS
        ),
        ServiceMenu(
            "交易记录", "流水号与支付渠道", AppIcons.Tag,
            3, panel = ServicePanel.TRANSACTIONS
        ),
        ServiceMenu(
            "工单中心", "售后工单列表", AppIcons.Ticket,
            1, panel = ServicePanel.TICKETS
        ),
        ServiceMenu(
            "子账户管理", "主账户下的子账户", AppIcons.Users,
            4, panel = ServicePanel.SUB_ACCOUNTS
        ),
        ServiceMenu(
            "安全中心", "邮箱/手机绑定与账户状态", AppIcons.Lock,
            5, panel = ServicePanel.SECURITY
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
    onRefresh: () -> Unit,
    onOpenOrder: (String) -> Unit = {},
    onOpenWeb: (String) -> Unit = {}
) {
    val menu = ServicePanels.entries.firstOrNull { it.panel == panel }
    val pageTitle = menu?.title
        ?: if (panel == ServicePanel.CERTIFICATION) Strings.CERTIFICATION else Strings.ACCOUNT_SERVICES
    val pageSub = menu?.subtitle ?: "实名状态与认证办理"
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = pageTitle,
                subtitle = pageSub,
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
                    panel == ServicePanel.CERTIFICATION -> {
                        CertBody(
                            data = data,
                            modifier = Modifier.fillMaxSize(),
                            onOpenWeb = onOpenWeb
                        )
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
                                items(data.orders, key = { "order-${it.id}" }) { order ->
                                    Box(Modifier.clickable { onOpenOrder(order.id) }) {
                                        OrderLineRow(order, withChevron = true)
                                    }
                                }
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

/** 应用设置子页面：主题配色独立整页 —— 深浅模式 + 六套高级主题色板（v1.4.0）。 */
@Composable
fun ThemeSettingsPage(
    currentMode: Int,
    currentAccentId: String,
    onSelectMode: (Int) -> Unit,
    onSelectAccent: (String) -> Unit,
    onBack: () -> Unit
) {
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.SETTINGS,
                subtitle = "深浅模式与主题色板",
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
                    Triple(Strings.THEME_LIGHT, SettingsStore.THEME_LIGHT, "始终使用亮色主题"),
                    Triple(Strings.THEME_DARK, SettingsStore.THEME_DARK, "始终使用深色主题")
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

                Spacer(Modifier.height(4.dp))
                SectionTitle("高级主题", CartoonColors.Brand)
                ThemePalettes.entries.forEach { spec ->
                    val selected = currentAccentId == spec.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CartoonKit.ItemShape)
                            .background(if (selected) CartoonColors.BrandSoft else CartoonColors.White)
                            .clickable { onSelectAccent(spec.id) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 双色预览卡：浅色/深色主色各半
                        Row(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CartoonKit.ItemShape),
                        ) {
                            Box(Modifier.weight(1f).fillMaxSize().background(spec.light.brand))
                            Box(Modifier.weight(1f).fillMaxSize().background(spec.dark.brand))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                spec.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) CartoonColors.Brand else CartoonColors.Ink
                            )
                            Text(spec.desc, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
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

/**
 * 实名认证页（v1.4.0 深度适配 /certification/info + /certification/plugin）。
 * 已实名：展示脱敏姓名/证件号/认证时间/状态；未实名：展示认证方式与网页办理入口。
 */
@Composable
private fun CertBody(
    data: ServicePanelData,
    modifier: Modifier = Modifier,
    onOpenWeb: (String) -> Unit
) {
    val cert = data.cert
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (cert == null) {
            EmptyHint(icon = AppIcons.EmptyBox, text = "暂无实名认证信息")
            return@Column
        }
        // 总状态卡
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(CartoonKit.ItemShape).background(CartoonColors.BrandSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Shield, null, tint = CartoonColors.Brand, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (cert.isCertified) "已完成实名认证" else "尚未完成实名认证",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = CartoonColors.Ink
                    )
                    Text(
                        if (cert.open) "站点实名认证功能已开启" else "站点未开启实名认证",
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub
                    )
                }
                statusBadge(if (cert.isCertified) "Active" else "Pending")
            }
        }

        // 个人认证记录（站点对姓名/证件号已脱敏后返回）
        cert.person?.let { record ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "个人认证", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold, color = CartoonColors.Ink
                        )
                        Spacer(Modifier.width(8.dp))
                        statusBadge(certRecordStatus(record.status))
                        Spacer(Modifier.weight(1f))
                        Text(record.createTime.ifBlank { "--" }, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                    }
                    KeyValueRow("姓名", record.cardName.ifBlank { "--" })
                    KeyValueRow("证件号", record.cardNumber.ifBlank { "--" })
                    if (record.status == "3" && record.failReason.isNotBlank()) {
                        Text(
                            "驳回原因：${record.failReason}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // 企业认证记录（若站点开启且已提交）
        cert.company?.let { record ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "企业认证", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold, color = CartoonColors.Ink
                        )
                        Spacer(Modifier.width(8.dp))
                        statusBadge(certRecordStatus(record.status))
                        Spacer(Modifier.weight(1f))
                        Text(record.createTime.ifBlank { "--" }, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                    }
                    KeyValueRow("企业名称", record.cardName.ifBlank { "--" })
                    KeyValueRow("证件号", record.cardNumber.ifBlank { "--" })
                    if (record.status == "3" && record.failReason.isNotBlank()) {
                        Text(
                            "驳回原因：${record.failReason}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // 未实名：认证方式 + 办理入口
        if (!cert.isCertified) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("可用的认证方式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                    if (data.certPlugins.isEmpty()) {
                        Text("站点未提供认证方式列表", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                    } else {
                        data.certPlugins.forEach { plugin ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Check, null, tint = CartoonColors.Mint, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    plugin.title + if (plugin.types.containsOnly("person")) "（个人）" else "",
                                    style = MaterialTheme.typography.bodySmall, color = CartoonColors.Ink
                                )
                            }
                        }
                    }
                    Text(
                        "认证流程需人脸核验等站点端操作，App 内一键打开认证页办理",
                        style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub
                    )
                    CartoonButton(
                        text = "前往实名认证办理",
                        onClick = { onOpenWeb(AppContainer.webUrl("plugin/22/certification.htm")) },
                        icon = AppIcons.Forward,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun List<String>.containsOnly(v: String) = isNotEmpty() && all { it == v }

/** 认证记录状态 → 徽章文案。 */
private fun certRecordStatus(status: String): String = when (status) {
    "1" -> "Active"
    "2" -> "Pending"
    "3" -> "Failed"
    else -> ""
}

/** 订单详情子页面（v1.4.0，GET /order/{id}）。 */
@Composable
fun OrderDetailPage(
    orderId: String,
    state: OrderDetailUi,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "订单详情",
                subtitle = "#$orderId",
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
                            Text("正在从站点读取订单…", style = MaterialTheme.typography.bodySmall, color = CartoonColors.InkSub)
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
                        EmptyHint(icon = AppIcons.EmptyBox, text = "订单不存在", modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        val d = state.detail
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 金额 + 状态
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "￥${d.amount}",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = CartoonColors.Coral
                                            )
                                            Spacer(Modifier.weight(1f))
                                            statusBadge(d.status)
                                        }
                                        KeyValueRow("订单编号", "#${d.id}")
                                        KeyValueRow("订单类型", orderTypeText(d.type))
                                        KeyValueRow("创建时间", d.createTime.ifBlank { "--" })
                                        if (d.payTime.isNotBlank()) KeyValueRow("支付时间", d.payTime)
                                        if (d.gateway.isNotBlank()) KeyValueRow("支付渠道", d.gateway)
                                        if (d.credit.isNotBlank() && d.credit != "0.00") KeyValueRow("抵扣余额", "￥${d.credit}")
                                        if (d.refundAmount.isNotBlank() && d.refundAmount != "0.00") KeyValueRow("退款金额", "￥${d.refundAmount}")
                                        if (d.amountUnpaid.isNotBlank() && d.amountUnpaid != "0.00") KeyValueRow("待付金额", "￥${d.amountUnpaid}")
                                        if (d.notes.isNotBlank()) KeyValueRow("备注", d.notes)
                                    }
                                }
                            }
                            // 明细项
                            items(d.items, key = { "item-${it.id}" }) { item ->
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                item.productName.ifBlank { "商品 #${item.id}" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = CartoonColors.Ink,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "￥${item.amount}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = CartoonColors.Coral
                                            )
                                        }
                                        if (item.hostName.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                KeyValueRow("主机", item.hostName)
                                                Spacer(Modifier.width(8.dp))
                                                statusBadge(item.hostStatus)
                                            }
                                        }
                                        if (item.billingCycle.isNotBlank()) KeyValueRow("周期", item.billingCycle)
                                        // 逐项配置描述（站点返回多行：CPU/内存/带宽/系统/价格）
                                        if (item.description.isNotBlank()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(CartoonKit.ItemShape)
                                                    .background(CartoonColors.BrandSoft.copy(alpha = 0.5f))
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                item.description.split("\n").map { it.trim() }
                                                    .filter { it.isNotBlank() }
                                                    .forEach { line ->
                                                        Text(
                                                            line,
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
                }
            }
        }
    }
}

private fun orderTypeText(type: String): String = when (type) {
    "new" -> "新购"
    "renew" -> "续费"
    "upgrade" -> "升级配置"
    else -> type.ifBlank { "--" }
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
internal fun OrderLineRow(order: OrderLine, withChevron: Boolean = false) {
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
            if (withChevron) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "点击查看订单详情 →",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.Brand,
                    fontWeight = FontWeight.Bold
                )
            }
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
