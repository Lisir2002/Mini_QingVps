package com.vps.qing.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.vps.qing.data.repository.CertPlugin
import com.vps.qing.data.repository.CertRecord
import com.vps.qing.data.repository.ServiceParser
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
            "已购产品", "已购产品列表，点击查看已购配置", AppIcons.Server,
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
    /** 打开「已购产品详情」页（传入 hostId，v1.4.2：已订购配置而非购买页）。 */
    onOpenProduct: (String) -> Unit = {},
    onOpenWeb: (String) -> Unit = {},
    // ---- v1.4.3 工单中心 ----
    onOpenTicket: (String) -> Unit = {},
    onCreateTicket: () -> Unit = {},
    onTicketFilter: (String?) -> Unit = {},
    ticketFilterStatusId: String? = null,
    // ---- v1.4.3 子账户管理 ----
    onOpenSubAccount: (String) -> Unit = {},
    onCreateSubAccount: () -> Unit = {},
    onToggleSubAccount: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteSubAccount: (String) -> Unit = {},
    // ---- v1.4.5 实名认证 ----
    certForm: CertFormUi = CertFormUi(),
    onSelectCertPlugin: (String) -> Unit = {},
    onCertCompanyMode: (Boolean) -> Unit = {},
    onCertFormChange: (CertFormUi) -> Unit = {},
    onSubmitCert: () -> Unit = {},
    actionBusy: Boolean = false
) {
    val menu = ServicePanels.entries.firstOrNull { it.panel == panel }
    val pageTitle = menu?.title
        ?: if (panel == ServicePanel.CERTIFICATION) Strings.CERTIFICATION else Strings.ACCOUNT_SERVICES
    val pageSub = menu?.subtitle ?: "实名状态与认证办理"
    val isTicketPanel = panel == ServicePanel.TICKETS
    val isSubPanel = panel == ServicePanel.SUB_ACCOUNTS
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = pageTitle,
                subtitle = pageSub,
                navIcon = AppIcons.Back,
                onNavClick = onBack,
                actions = {
                    if (isTicketPanel || isSubPanel) {
                        CartoonIconChip(
                            icon = AppIcons.Plus,
                            onClick = { if (isTicketPanel) onCreateTicket() else onCreateSubAccount() },
                            tint = CartoonColors.Brand,
                            bg = CartoonColors.BrandSoft
                        )
                        Spacer(Modifier.width(6.dp))
                    }
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
                            form = certForm,
                            modifier = Modifier.fillMaxSize(),
                            onSelectPlugin = onSelectCertPlugin,
                            onCompanyMode = onCertCompanyMode,
                            onFormChange = onCertFormChange,
                            onSubmit = onSubmitCert,
                            onOpenWeb = onOpenWeb
                        )
                    }
                    // v1.4.3：工单中心 = 统计条 + 状态筛选 + 可点进详情的列表 + 新建入口
                    isTicketPanel -> {
                        TicketsListBody(
                            data = data,
                            filterStatusId = ticketFilterStatusId,
                            onFilter = onTicketFilter,
                            onOpenTicket = onOpenTicket,
                            onCreateTicket = onCreateTicket,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // v1.4.3：子账户管理 = 列表 + 启停/编辑/删除 + 新建入口
                    isSubPanel -> {
                        SubAccountsListBody(
                            data = data,
                            busy = actionBusy,
                            onOpenSubAccount = onOpenSubAccount,
                            onCreateSubAccount = onCreateSubAccount,
                            onToggle = onToggleSubAccount,
                            onDelete = onDeleteSubAccount,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        val empty = data.hosts.isEmpty() && data.orders.isEmpty() && data.txns.isEmpty() &&
                            data.security.isEmpty()
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
                                items(data.hosts, key = { "host-${it.id}" }) { host ->
                                    Box(Modifier.clickable { onOpenProduct(host.id) }) {
                                        HostLineRow(host)
                                    }
                                }
                                items(data.orders, key = { "order-${it.id}" }) { order ->
                                    Box(Modifier.clickable { onOpenOrder(order.id) }) {
                                        OrderLineRow(order, withChevron = true)
                                    }
                                }
                                items(data.txns, key = { "txn-${it.number}-${it.createTime}" }) { txn ->
                                    // 交易记录绑定关联订单：有 order_id 即可点击进入订单详情（v1.4.1）
                                    Box(
                                        Modifier.clickable(
                                            enabled = txn.orderId.isNotBlank()
                                        ) { onOpenOrder(txn.orderId) }
                                    ) {
                                        TxnLineRow(txn)
                                    }
                                }
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
 * 实名认证页（v1.4.5 原生提交与进度，协议对齐官方插件 idcsmart_certification）。
 * 已实名：脱敏姓名/证件号/认证时间/状态（官方语义 1=已实名 2=未通过 3=待审核 4=已提交资料）；
 * 未实名：选择认证方式 → 原生表单提交 → 认证进度轮询；人脸核验环节用 WebView 打开站点官方认证页。
 */
@Composable
private fun CertBody(
    data: ServicePanelData,
    form: CertFormUi,
    modifier: Modifier = Modifier,
    onSelectPlugin: (String) -> Unit = {},
    onCompanyMode: (Boolean) -> Unit = {},
    onFormChange: (CertFormUi) -> Unit = {},
    onSubmit: () -> Unit = {},
    onOpenWeb: (String) -> Unit = {}
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
        // 总状态卡（含未实名购买限制提示）
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
                        when {
                            !cert.open -> "站点未开启实名认证"
                            cert.isCertified -> "站点实名认证功能已开启"
                            cert.buyBlocked -> "未实名账号无法购买商品，请尽快完成认证"
                            else -> "站点实名认证功能已开启"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (cert.buyBlocked && !cert.isCertified) MaterialTheme.colorScheme.error
                        else CartoonColors.InkSub
                    )
                }
                statusBadge(if (cert.isCertified) "Active" else "Pending")
            }
        }

        // 认证记录卡（站点对姓名/证件号已脱敏后返回）
        certRecordCard(cert.person, "个人认证")
        certRecordCard(cert.company, "企业认证")

        // 未实名：认证进度（提交后轮询）→ 认证方式 → 表单
        if (!cert.isCertified) {
            if (form.polling) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Shield, null, tint = CartoonColors.Mint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "认证进度",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = CartoonColors.Ink
                            )
                        }
                        Text(
                            form.pollHint ?: "已提交，等待站点核验…",
                            style = MaterialTheme.typography.bodySmall,
                            color = CartoonColors.InkSub
                        )
                        LinearProgressIndicator(
                            color = CartoonColors.Mint,
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                        Text(
                            "如站点要求人脸核验，请点击下方按钮在官方认证页完成；完成后回到本页将自动显示最新状态",
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                        CartoonButton(
                            text = "前往人脸核验",
                            onClick = { onOpenWeb(AppContainer.webUrl("authentication_thrid.htm?type=1")) },
                            icon = AppIcons.Forward,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 认证方式选择
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "选择认证方式",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = CartoonColors.Ink
                    )
                    if (data.certPlugins.isEmpty()) {
                        Text(
                            "站点未提供认证方式，请前往网页端办理",
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                        CartoonButton(
                            text = "前往网页端办理",
                            onClick = { onOpenWeb(AppContainer.webUrl("authentication_select.htm")) },
                            icon = AppIcons.Forward,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        data.certPlugins.forEach { plugin ->
                            val selected = form.selectedPlugin == plugin.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CartoonKit.ItemShape)
                                    .background(if (selected) CartoonColors.BrandSoft else Color.Transparent)
                                    .clickable { onSelectPlugin(plugin.id) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                                        plugin.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) CartoonColors.Brand else CartoonColors.Ink
                                    )
                                    Text(
                                        certPluginTypeLabel(plugin),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CartoonColors.InkSub
                                    )
                                }
                            }
                        }
                        // 费用提示（GET /certification/plugin/config）
                        form.config?.let { config ->
                            Text(
                                config.feeHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (config.pay) MaterialTheme.colorScheme.error else CartoonColors.Mint
                            )
                        }
                    }
                }
            }

            // 认证表单（提交期间与轮询期间禁用）
            if (form.selectedPlugin.isNotBlank() && !form.polling) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (form.companyMode) "企业认证信息" else "个人认证信息",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = CartoonColors.Ink
                        )
                        // 插件同时支持个人与企业时可切换
                        if (form.selectedPluginTypes.size > 1) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                listOf(false to "个人认证", true to "企业认证").forEach { (company, label) ->
                                    val selected = form.companyMode == company
                                    Row(
                                        modifier = Modifier
                                            .clip(CartoonKit.ItemShape)
                                            .background(if (selected) CartoonColors.BrandSoft else CartoonColors.White)
                                            .clickable { onCompanyMode(company) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) CartoonColors.Brand else CartoonColors.InkSub
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                        }
                        CertTextField(
                            label = if (form.companyMode) "法人姓名" else "姓名",
                            value = form.cardName,
                            onValueChange = { onFormChange(form.copy(cardName = it)) },
                            enabled = !form.submitting
                        )
                        CertTextField(
                            label = "手机号（选填）",
                            value = form.phone,
                            onValueChange = { onFormChange(form.copy(phone = it)) },
                            enabled = !form.submitting,
                            keyboardType = KeyboardType.Phone
                        )
                        // 证件类型：横向 chips（官方 8 种证件）
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "证件类型",
                                style = MaterialTheme.typography.labelSmall,
                                color = CartoonColors.InkSub
                            )
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                ServiceParser.CARD_TYPES.forEach { (value, label) ->
                                    val selected = form.cardType == value
                                    Row(
                                        modifier = Modifier
                                            .clip(CartoonKit.ItemShape)
                                            .background(if (selected) CartoonColors.Brand else CartoonColors.White)
                                            .clickable(enabled = !form.submitting) {
                                                onFormChange(form.copy(cardType = value))
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) CartoonColors.White else CartoonColors.Ink
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                            }
                        }
                        CertTextField(
                            label = "证件号码",
                            value = form.cardNumber,
                            onValueChange = { onFormChange(form.copy(cardNumber = it)) },
                            enabled = !form.submitting
                        )
                        // 企业认证附加字段
                        if (form.companyMode) {
                            CertTextField(
                                label = "企业名称",
                                value = form.customValues["company"].orEmpty(),
                                onValueChange = {
                                    onFormChange(form.copy(customValues = form.customValues + ("company" to it)))
                                },
                                enabled = !form.submitting
                            )
                            CertTextField(
                                label = "统一社会信用代码",
                                value = form.customValues["company_organ_code"].orEmpty(),
                                onValueChange = {
                                    onFormChange(form.copy(customValues = form.customValues + ("company_organ_code" to it)))
                                },
                                enabled = !form.submitting
                            )
                        }
                        // 站点要求上传证件照时（本站 certification_upload=0，暂不内置上传）
                        if (data.cert?.uploadRequired == true) {
                            Text(
                                "站点要求上传证件照片，请在网页端完成本步骤",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        form.submitError?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        CartoonButton(
                            text = if (form.submitting) "提交中…" else "提交认证",
                            onClick = onSubmit,
                            icon = AppIcons.Check,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !form.submitting
                        )
                        Text(
                            "证件信息仅用于站点实名核验，App 不存储任何证件数据",
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                    }
                }
            }
        }
    }
}

/** 认证记录卡：状态语义与文案对齐官方（1=已认证 2=未通过 3=待审核 4=已提交资料）。 */
@Composable
private fun certRecordCard(record: CertRecord?, title: String) {
    if (record == null) return
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink
                )
                Spacer(Modifier.width(8.dp))
                statusBadge(certRecordStatus(record.status))
                Spacer(Modifier.weight(1f))
                Text(
                    record.createTime.ifBlank { "--" },
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub
                )
            }
            KeyValueRow(if (title == "企业认证") "企业名称" else "姓名", record.cardName.ifBlank { "--" })
            KeyValueRow("证件号", record.cardNumber.ifBlank { "--" })
            // 官方语义：2=未通过，驳回原因在 auth_fail
            if (record.status == "2" && record.failReason.isNotBlank()) {
                Text(
                    "驳回原因：${record.failReason}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** 认证方式支持的类型 → 展示标签。 */
private fun certPluginTypeLabel(plugin: CertPlugin): String = when {
    plugin.types.containsOnly("person") -> "支持：个人认证"
    plugin.types.containsOnly("company") -> "支持：企业认证"
    else -> "支持：个人认证 / 企业认证"
}

/** 认证表单输入框（统一卡通配色）。 */
@Composable
private fun CertTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CartoonColors.Brand,
            unfocusedBorderColor = CartoonColors.InkSub,
            focusedLabelColor = CartoonColors.Brand,
            cursorColor = CartoonColors.Brand
        ),
        modifier = modifier.fillMaxWidth()
    )
}

private fun List<String>.containsOnly(v: String) = isNotEmpty() && all { it == v }

/** 认证记录状态 → 徽章文案。 */
private fun certRecordStatus(status: String): String = when (status) {
    "1" -> "Active"
    "2" -> "Pending"
    "3" -> "Failed"
    else -> ""
}

/** 订单详情子页面（GET /order/{id}）；明细项跳转对应「已购产品详情」页（v1.4.2）。 */
@Composable
fun OrderDetailPage(
    orderId: String,
    state: OrderDetailUi,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenProduct: (String, String) -> Unit = { _, _ -> },
    /** 待支付订单的「继续支付」入口（v1.4.9）：拉起原生结算支付面板。 */
    onContinuePay: (String, String, List<String>) -> Unit = { _, _, _ -> }
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
                            // 待支付/待审核订单：原生「继续支付」入口（v1.4.9）
                            if (d.status.equals("Unpaid", true) || d.status.equals("Pending", true)) {
                                item {
                                    CartoonButton(
                                        text = "继续支付",
                                        onClick = {
                                            onContinuePay(d.id, d.amount, d.items.map { it.productName })
                                        },
                                        icon = AppIcons.Wallet,
                                        containerColor = CartoonColors.Coral,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            // 明细项（点击进入该订单对应的已购产品详情，v1.4.2）
                            items(d.items, key = { "item-${it.id}" }) { item ->
                                Box(Modifier.clickable { onOpenProduct(item.hostId, item.productName) }) {
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
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "点击查看已购产品详情 →",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CartoonColors.Brand,
                                            fontWeight = FontWeight.Bold
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
            Spacer(Modifier.height(2.dp))
            Text(
                "点击查看已购详情 →",
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.Brand,
                fontWeight = FontWeight.Bold
            )
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
            if (txn.orderId.isNotBlank()) {
                Text(
                    "关联订单 #${txn.orderId} · 点击查看详情 →",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.Brand,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// 工单 / 子账户列表已迁移到 AccountServicePages.kt 的增强实现（v1.4.3）：
// 统计条 + 状态筛选 + 详情跳转 + 新建，以及启停 / 编辑 / 删除操作。
