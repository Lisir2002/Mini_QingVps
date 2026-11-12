package com.vps.qing.feature.profile

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.SettingsStore
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.EmptyHint
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings

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
 * 「账号与服务」v1.3.0：除实名认证外全部对接站点 API（/host /order /transaction
 * /ticket /sub_account /account），点击展开原生数据面板，不再跳网页。
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

/** 实名认证：站点未暴露 API，保留网页兜底。 */
private val certificationMenu = ServiceMenu(
    Strings.CERTIFICATION, "站点未开放接口，网页办理", AppIcons.Shield,
    CartoonColors.Peach, CartoonColors.PeachSoft, webPath = "plugin/22/certification.htm"
)

/** 个人中心 Tab：个人信息 + 账号与服务（原生 API 面板）+ 应用设置/关于 + 退出登录。 */
@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    onOpenWeb: (String) -> Unit
) {
    val vm: ProfileViewModel = viewModel(
        factory = simpleViewModelFactory { ProfileViewModel(AppContainer.profileRepository) }
    )
    val state by vm.ui.collectAsState()
    val themeMode by AppContainer.settings.themeMode.collectAsState()

    var showLogout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.PROFILE,
                subtitle = "账号与应用设置",
                actions = {
                    CartoonIconChip(
                        icon = AppIcons.Refresh,
                        onClick = { vm.load() },
                        tint = CartoonColors.Mint,
                        bg = CartoonColors.MintSoft
                    )
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 账号卡
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.85f) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(50.dp)
                                    .clip(CartoonKit.CardShape)
                                    .background(CartoonColors.BrandSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(AppIcons.Profile, null, tint = CartoonColors.Brand, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Strings.ACCOUNT, style = MaterialTheme.typography.labelMedium, color = CartoonColors.InkSub)
                                Text(
                                    state.account?.username ?: Strings.UNKNOWN,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CartoonColors.Ink
                                )
                            }
                        }
                        InfoRow(AppIcons.Mail, "邮箱", state.account?.email ?: Strings.UNKNOWN)
                        InfoRow(AppIcons.Profile, "手机", state.account?.phone?.takeIf { it.isNotBlank() } ?: "未绑定")
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            InfoInline(AppIcons.Wallet, Strings.BALANCE, state.account?.balance ?: Strings.UNKNOWN, Modifier.weight(1f))
                            InfoInline(AppIcons.Tag, Strings.CREDIT, state.account?.creditLimit ?: Strings.UNKNOWN, Modifier.weight(1f))
                        }
                    }
                }

                if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CartoonColors.Brand)
                state.error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    CartoonButton(text = Strings.RETRY, onClick = { vm.load() })
                }

                SectionTitle(Strings.ACCOUNT_SERVICES, CartoonColors.Brand)
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.82f) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        ServicePanels.entries.forEach { menu ->
                            ServiceRow(
                                menu = menu,
                                expanded = state.openPanel != null && menu.panel == state.openPanel,
                                onClick = { menu.panel?.let(vm::togglePanel) }
                            )
                            menu.panel?.let { panel ->
                                AnimatedVisibility(visible = state.openPanel == panel) {
                                    ServicePanelBody(
                                        data = state.panels[panel],
                                        onRefresh = { vm.refreshOpenPanel() }
                                    )
                                }
                            }
                        }
                        // 实名认证：站点无 API，网页兜底
                        MenuRow(certificationMenu) { onOpenWeb(AppContainer.webUrl(certificationMenu.webPath)) }
                    }
                }

                SectionTitle(Strings.APP_GROUP, CartoonColors.Lilac)
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.82f) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        MenuRow(
                            ServiceMenu(Strings.SETTINGS, "", AppIcons.Settings, CartoonColors.Lilac, CartoonColors.LilacSoft),
                            subtitle = Strings.THEME
                        ) { showSettings = true }
                        MenuRow(
                            ServiceMenu(Strings.ABOUT, "", AppIcons.Info, CartoonColors.Sky, CartoonColors.SkySoft),
                            subtitle = "版本 ${Strings.APP_VERSION}"
                        ) { showAbout = true }
                    }
                }

                CartoonButton(
                    text = Strings.LOGOUT,
                    onClick = { showLogout = true },
                    icon = AppIcons.Logout,
                    containerColor = CartoonColors.Peach,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showLogout) {
        GlassDialog(title = Strings.LOGOUT, text = Strings.LOGOUT_CONFIRM, onDismiss = { showLogout = false }) {
            TextButton(onClick = { showLogout = false }) { Text(Strings.CANCEL, color = CartoonColors.InkSub) }
            TextButton(onClick = { showLogout = false; vm.logout() }) {
                Text(Strings.CONFIRM, fontWeight = FontWeight.Bold, color = CartoonColors.Brand)
            }
        }
    }

    if (showSettings) {
        GlassDialog(title = Strings.THEME, onDismiss = { showSettings = false }) {
            val options = listOf(
                Strings.THEME_SYSTEM to SettingsStore.THEME_SYSTEM,
                Strings.THEME_LIGHT to SettingsStore.THEME_LIGHT,
                Strings.THEME_DARK to SettingsStore.THEME_DARK
            )
            options.forEach { (label, mode) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            AppContainer.settings.setThemeMode(mode)
                            showSettings = false
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = CartoonColors.Brand)
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = CartoonColors.Ink)
                }
            }
        }
    }

    if (showAbout) {
        GlassDialog(
            title = Strings.ABOUT,
            text = Strings.ABOUT_TEXT.replace("%s", Strings.APP_VERSION),
            onDismiss = { showAbout = false }
        ) {
            TextButton(onClick = { showAbout = false }) {
                Text(Strings.CONFIRM, fontWeight = FontWeight.Bold, color = CartoonColors.Brand)
            }
        }
    }
}

/** 服务板块行：图标 + 标题 + 副标题 + chevron；点击展开原生数据面板。 */
@Composable
private fun ServiceRow(menu: ServiceMenu, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(menu.soft),
            contentAlignment = Alignment.Center
        ) {
            Icon(menu.icon, null, tint = menu.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(menu.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
            Text(menu.subtitle, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
        }
        Icon(
            AppIcons.ChevronDown, null,
            tint = CartoonColors.InkSub,
            modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f)
        )
    }
}

/** 展开后的原生数据面板：加载/错误/空态 + 各板块明细。 */
@Composable
private fun ServicePanelBody(data: ServicePanelData?, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(CartoonKit.ItemShape)
            .background(CartoonColors.White.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            data == null || data.loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(modifier = Modifier.width(48.dp), color = CartoonColors.Brand)
                    Spacer(Modifier.width(10.dp))
                    Text("正在从站点读取…", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                }
            }
            data.error != null -> {
                Text(data.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                CartoonButton(text = Strings.RETRY, onClick = onRefresh)
            }
            else -> {
                if (data.hosts.isNotEmpty()) data.hosts.forEach { HostLineRow(it) }
                if (data.orders.isNotEmpty()) data.orders.forEach { OrderLineRow(it) }
                if (data.txns.isNotEmpty()) data.txns.forEach { TxnLineRow(it) }
                if (data.tickets.isNotEmpty()) data.tickets.forEach { TicketLineRow(it) }
                if (data.subAccounts.isNotEmpty()) data.subAccounts.forEach { SubAccountLineRow(it) }
                if (data.security.isNotEmpty()) data.security.forEach { (label, value) ->
                    KeyValueRow(label, value)
                }
                // 全空：明确空态
                val allEmpty = data.hosts.isEmpty() && data.orders.isEmpty() && data.txns.isEmpty() &&
                    data.tickets.isEmpty() && data.subAccounts.isEmpty() && data.security.isEmpty()
                if (allEmpty) {
                    EmptyHint(icon = AppIcons.EmptyBox, text = "暂无数据")
                }
            }
        }
    }
}

/** 状态徽章颜色：Active/Paid 绿色，Unpaid/Pending 珊瑚色，其余灰蓝。 */
@Composable
private fun statusBadge(status: String) {
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
private fun KeyValueRow(label: String, value: String) {
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
private fun HostLineRow(host: com.vps.qing.data.repository.HostLine) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CartoonKit.ItemShape)
            .background(CartoonColors.White)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                host.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, color = CartoonColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis
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

@Composable
private fun OrderLineRow(order: com.vps.qing.data.repository.OrderLine) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CartoonKit.ItemShape)
            .background(CartoonColors.White)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                color = CartoonColors.InkSub, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        KeyValueRow("时间", "${order.createTime} · ${order.gateway}")
    }
}

@Composable
private fun TxnLineRow(txn: com.vps.qing.data.repository.TxnLine) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CartoonKit.ItemShape)
            .background(CartoonColors.White)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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

@Composable
private fun TicketLineRow(ticket: com.vps.qing.data.repository.TicketLine) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CartoonKit.ItemShape)
            .background(CartoonColors.White)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ticket.title.ifBlank { "#${ticket.id}" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, color = CartoonColors.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text(ticket.createTime, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
        }
    }
}

@Composable
private fun SubAccountLineRow(sub: com.vps.qing.data.repository.SubAccountLine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CartoonKit.ItemShape)
            .background(CartoonColors.White)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(sub.username.ifBlank { "#${sub.id}" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
            Text(sub.email, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
        }
    }
}

/** 磨砂玻璃弹窗。 */
@Composable
private fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    text: String? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = CartoonKit.SheetShape,
            alpha = 0.94f,
            elevation = 24.dp
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = CartoonColors.Ink)
                if (text != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = CartoonColors.InkSub)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) { actions() }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label：$value", style = MaterialTheme.typography.bodyMedium, color = CartoonColors.Ink)
    }
}

@Composable
private fun InfoInline(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = CartoonColors.Mint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("$label $value", style = MaterialTheme.typography.labelMedium, color = CartoonColors.InkSub, maxLines = 1)
    }
}

@Composable
private fun MenuRow(menu: ServiceMenu, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(menu.soft),
            contentAlignment = Alignment.Center
        ) {
            Icon(menu.icon, null, tint = menu.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(menu.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
            Text(
                subtitle ?: menu.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
        }
        Icon(AppIcons.Forward, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
    }
}
