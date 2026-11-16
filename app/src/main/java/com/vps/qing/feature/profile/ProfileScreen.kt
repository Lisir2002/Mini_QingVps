package com.vps.qing.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.notify.NotificationSettingsPage
import com.vps.qing.core.permissions.PermissionOnboardingScreen
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.accountDisplay
import com.vps.qing.core.ui.maskEmail
import com.vps.qing.core.ui.CertBadge
import com.vps.qing.core.ui.CertBadgeChip
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassLoadingBar
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.ScrollGlassEffect

/**
 * 个人中心 v1.3.1：账号与服务每项独立子页面（点击进入整页，返回键/顶栏返回），
 * 应用设置（主题配色）也是独立子页面；弹窗仅保留登出确认与关于。
 */
@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    onOpenWeb: (String) -> Unit,
    /** 子页面开关上报主容器：打开时隐藏底栏（v1.4.1）。 */
    onSubPageChanged: (Boolean) -> Unit = {},
    /** 首页发起的原生导航请求（v1.4.6）：面板 token 或 "product:{id}"，消费后回调置空。 */
    navRequest: String? = null,
    onNavConsumed: () -> Unit = {},
    /** 待支付订单「继续支付」：拉起原生结算支付面板（v1.4.9）。 */
    onContinuePay: (String, String, List<String>) -> Unit = { _, _, _ -> }
) {
    val vm: ProfileViewModel = viewModel(
        factory = simpleViewModelFactory {
            ProfileViewModel(
                AppContainer.profileRepository,
                AppContainer.authRepository,
                AppContainer.siteConfigRepository
            )
        }
    )
    val state by vm.ui.collectAsState()
    val themeMode by AppContainer.settings.themeMode.collectAsState()

    var showLogout by remember { mutableStateOf(false) }
    /**
     * v1.4.15 统一页面返回栈：逗号分隔的页面 token，栈顶即当前页面，空栈 = 个人中心主页。
     * token 规则：
     *  - `panel:<ServicePanel.name>` 服务面板；`settings` / `about` 独立页；
     *  - `product:<hostId>` / `order:<id>` / `ticket:<id>` / `ticketnew` / `subnew` /
     *    `subedit:<id>` / `secedit:<target>` 详情页。
     *
     * 任何页面进入子页面都 push、返回都 pop，天然满足「从哪个页面进入就退回哪个页面」：
     * 例如 安全中心(panel:SECURITY) → 实名卡 push(panel:CERTIFICATION) → 返回 pop 回安全中心，
     * 而不再像旧版那样用 `servicePage = CERTIFICATION` **替换**当前面板，导致返回直接回主页。
     * 服务面板 → 订单详情 → 关联产品详情 也可逐级回退，互不覆盖。
     */
    var navStack by rememberSaveable { mutableStateOf("") }
    val accentId by AppContainer.settings.accentId.collectAsState()

    val topToken: String? = navStack.substringAfterLast(',').takeIf { it.isNotBlank() }
    val pushPage: (String) -> Unit = { token ->
        navStack = if (navStack.isBlank()) token else "$navStack,$token"
    }
    val popPage: () -> Unit = { navStack = navStack.substringBeforeLast(',', "") }
    val openOrderDetail: (String) -> Unit = { pushPage("order:$it") }
    /** 打开已购产品详情：hostId 优先，缺失时按商品名在已购列表里兜底匹配。 */
    val openHostDetail: (String, String) -> Unit = { hostId, productName ->
        val target = hostId.takeIf { it.isNotBlank() }
            ?: state.panels[ServicePanel.HOSTS]?.hosts
                ?.firstOrNull { productName.isNotBlank() && it.productName == productName }?.id
        if (target != null) pushPage("product:$target")
        else if (hostId.isBlank()) vm.ensurePanel(ServicePanel.HOSTS)
    }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    // 写操作反馈（v1.4.3）：用系统 Toast，任意子页面都能收到，显示后即消费
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.actionMessage, state.actionError) {
        val msg = state.actionMessage ?: state.actionError
        if (msg != null) {
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
            vm.consumeAction()
        }
    }

    // v1.4.15：按栈顶页面触发懒加载（服务面板 / 各类详情），回退到旧页面时不会重复加载出错
    LaunchedEffect(topToken) {
        val token = topToken ?: return@LaunchedEffect
        when {
            token.startsWith("panel:") ->
                runCatching { ServicePanel.valueOf(token.removePrefix("panel:")) }
                    .getOrNull()?.let(vm::ensurePanel)
            token.startsWith("product:") -> vm.ensureProductDetail(token.removePrefix("product:"))
            token.startsWith("order:") -> vm.ensureOrderDetail(token.removePrefix("order:"))
            token.startsWith("ticket:") -> vm.ensureTicketDetail(token.removePrefix("ticket:"))
            token == "ticketnew" || token == "subnew" -> vm.ensureFormMeta()
            token.startsWith("subedit:") -> {
                vm.ensureFormMeta()
                vm.ensureSubAccountDetail(token.removePrefix("subedit:"))
            }
        }
    }

    // v1.4.6 / v1.4.15：消费首页发起的导航请求（服务面板直达 / 产品详情直达）。
    // 直达属于「从主页发起的一层导航」，重置为单 token 栈，返回即回个人中心主页。
    LaunchedEffect(navRequest) {
        if (navRequest != null) {
            val token = when (navRequest) {
                "hosts" -> "panel:${ServicePanel.HOSTS.name}"
                "orders" -> "panel:${ServicePanel.ORDERS.name}"
                "finance" -> "panel:${ServicePanel.TRANSACTIONS.name}"
                "tickets" -> "panel:${ServicePanel.TICKETS.name}"
                "subaccounts" -> "panel:${ServicePanel.SUB_ACCOUNTS.name}"
                "security" -> "panel:${ServicePanel.SECURITY.name}"
                "cert" -> "panel:${ServicePanel.CERTIFICATION.name}"
                // v1.4.17：通知深链（orders/tickets/hosts/security 已在上方映射）
                // 以及权限管理 / 通知管理两个新菜单直达
                "permissions" -> "permissions"
                "notifications" -> "notifications"
                // v1.4.18：通知深链不再被丢弃。旧版只放行 "product:"，
                // 工单通知的 "ticket:<id>"、订单通知的 "order:<id>" 会落到 null →
                // 点通知毫无反应，与「点击直达对应页面」的文案不对称。
                else -> when {
                    navRequest.startsWith("product:") -> navRequest
                    navRequest.startsWith("order:") -> navRequest
                    navRequest.startsWith("ticket:") -> navRequest
                    else -> null
                }
            }
            if (token != null) navStack = token
            onNavConsumed()
        }
    }
    // 上报主容器：任意子页面打开即隐藏底栏
    LaunchedEffect(navStack) { onSubPageChanged(navStack.isNotBlank()) }
    // 系统返回键：逐级 pop，永远回到「进入当前页面的那个页面」
    BackHandler(enabled = navStack.isNotBlank()) { popPage() }

    // v1.4.15：统一按栈顶页面渲染；所有子页面 onBack 一律 popPage，
    // 因而「从哪个页面进入就退回哪个页面」——例如安全中心 → 实名认证返回回安全中心。
    topToken?.let { token ->
        when {
            token.startsWith("product:") -> {
                val hostId = token.removePrefix("product:")
                PurchasedProductPage(
                    hostId = hostId,
                    state = state.productDetails[hostId] ?: ProductDetailUi(),
                    onBack = popPage,
                    onRefresh = { vm.refreshProductDetail(hostId) },
                    onOpenOrder = openOrderDetail,
                    onSelectTab = { vm.setHostDetailTab(hostId, it) },
                    onReinstall = { imageId, pwd, port -> vm.reinstallHost(hostId, imageId, pwd, port) },
                    onPower = { action -> vm.hostPower(hostId, action) },
                    onResetPassword = { pwd, port -> vm.resetHostPassword(hostId, pwd, port) },
                    onRefreshLogs = { vm.refreshHostLogs(hostId) }
                )
            }
            token.startsWith("order:") -> {
                val orderId = token.removePrefix("order:")
                OrderDetailPage(
                    orderId = orderId,
                    state = state.orderDetails[orderId] ?: OrderDetailUi(),
                    onBack = popPage,
                    onRefresh = { vm.refreshOrderDetail(orderId) },
                    onOpenProduct = openHostDetail,
                    onContinuePay = onContinuePay
                )
            }
            // v1.4.3：工单详情 / 新建工单
            token.startsWith("ticket:") -> {
                TicketDetailPage(
                    vm = vm,
                    ticketId = token.removePrefix("ticket:"),
                    onBack = popPage
                )
            }
            token == "ticketnew" -> {
                TicketCreatePage(vm = vm, onBack = popPage, onCreated = popPage)
            }
            // v1.4.3：子账户新建 / 编辑
            token == "subnew" -> {
                SubAccountFormPage(vm = vm, editId = null, onBack = popPage, onSaved = popPage)
            }
            token.startsWith("subedit:") -> {
                SubAccountFormPage(
                    vm = vm,
                    editId = token.removePrefix("subedit:"),
                    onBack = popPage,
                    onSaved = popPage
                )
            }
            // v1.4.12：安全中心编辑整页（密码 / 邮箱 / 手机）
            token.startsWith("secedit:") -> {
                SecurityEditPage(
                    vm = vm,
                    target = token.removePrefix("secedit:"),
                    onBack = popPage
                )
            }
            // 服务面板（含安全中心 / 实名认证等），从栈里解析具体面板
            token.startsWith("panel:") -> {
                val panel = runCatching { ServicePanel.valueOf(token.removePrefix("panel:")) }
                    .getOrNull()
                if (panel != null) {
                    ServicePageScreen(
                        panel = panel,
                        data = state.panels[panel],
                        onBack = popPage,
                        onRefresh = { vm.refreshPanel(panel) },
                        onOpenOrder = openOrderDetail,
                        onOpenProduct = { openHostDetail(it, "") },
                        onOpenWeb = onOpenWeb,
                        onOpenTicket = { pushPage("ticket:$it") },
                        onCreateTicket = { pushPage("ticketnew") },
                        onTicketFilter = vm::setTicketFilter,
                        ticketFilterStatusId = state.ticketFilterStatusId,
                        onOpenSubAccount = { pushPage("subedit:$it") },
                        onCreateSubAccount = { pushPage("subnew") },
                        onToggleSubAccount = vm::toggleSubAccountStatus,
                        onDeleteSubAccount = vm::deleteSubAccount,
                        certForm = state.certForm,
                        onSelectCertPlugin = vm::selectCertPlugin,
                        onCertCompanyMode = vm::setCertCompanyMode,
                        onCertFormChange = { newForm -> vm.updateCertForm { newForm } },
                        onSubmitCert = vm::submitCert,
                        actionBusy = state.actionBusy,
                        orderFilter = state.orderFilter,
                        onOrderFilter = vm::setOrderFilter,
                        txnFilter = state.txnFilter,
                        onTxnFilter = vm::setTxnFilter,
                        onEditSecurity = { pushPage("secedit:$it") },
                        certInfo = state.panels[ServicePanel.CERTIFICATION]?.cert,
                        // v1.4.15：实名卡是「从安全中心进入的一层」，push 而非替换，返回回安全中心
                        onOpenCert = { pushPage("panel:${ServicePanel.CERTIFICATION.name}") }
                    )
                }
            }
            token == "settings" -> {
                ThemeSettingsPage(
                    currentMode = themeMode,
                    currentAccentId = accentId,
                    onSelectMode = { AppContainer.settings.setThemeMode(it) },
                    onSelectAccent = { AppContainer.settings.setAccentId(it) },
                    onBack = popPage
                )
            }
            // v1.4.17：应用设置下新增「权限管理」「通知管理」两个独立菜单页
            token == "permissions" -> {
                PermissionOnboardingScreen(
                    onDone = popPage,
                    isOnboarding = false,
                    onBack = popPage
                )
            }
            token == "notifications" -> {
                NotificationSettingsPage(
                    onBack = popPage,
                    onOpenPermissions = { pushPage("permissions") }
                )
            }
            token == "about" -> {
                AboutPage(onBack = popPage, onOpenWeb = onOpenWeb)
            }
        }
        return
    }

    // v1.4.11：滚动时玻璃顶/底栏磨砂感消失只留透明度
    val profileScroll = rememberScrollState()
    ScrollGlassEffect(profileScroll)

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
                    .verticalScroll(profileScroll)
                    .padding(horizontal = 14.dp)
                    .padding(top = 12.dp, bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 账号卡
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
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
                                // v1.4.14：名字区只显示脱敏邮箱（绝不显示实名姓名），实名状态仅用徽标表达
                                val display = accountDisplay(
                                    state.account,
                                    state.panels[ServicePanel.CERTIFICATION]?.cert,
                                    state.account != null
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        display.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CartoonColors.Ink,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (display.badge != CertBadge.NONE) {
                                        Spacer(Modifier.width(8.dp))
                                        CertBadgeChip(
                                            badge = display.badge,
                                            // v1.4.15：未实名徽标进入安全中心（实名认证已合入其中）
                                            onClick = if (display.badge == CertBadge.UNCERTIFIED) {
                                                { pushPage("panel:${ServicePanel.SECURITY.name}") }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                        // v1.4.14：名字区与邮箱行一律脱敏，只显示前 2 位 + 星号 + @域名
                        InfoRow(AppIcons.Mail, "邮箱", maskEmail(state.account?.email))
                        InfoRow(AppIcons.Profile, "手机", state.account?.phone?.takeIf { it.isNotBlank() } ?: "未绑定")
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            InfoInline(AppIcons.Wallet, Strings.BALANCE, state.account?.balance ?: Strings.UNKNOWN, Modifier.weight(1f))
                            InfoInline(AppIcons.Tag, Strings.CREDIT, state.account?.creditLimit ?: Strings.UNKNOWN, Modifier.weight(1f))
                        }
                    }
                }

                // v1.4.15：主页刷新等待态统一为磨砂玻璃提示条
                if (state.loading) {
                    GlassLoadingBar(modifier = Modifier.fillMaxWidth())
                }
                state.error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    CartoonButton(text = Strings.RETRY, onClick = { vm.load() })
                }

                SectionTitle(Strings.ACCOUNT_SERVICES, CartoonColors.Brand)
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        // 账号与服务：每项点击进入独立子页面。
                        // v1.4.14：实名认证已合并进「安全中心」，不再单列独立菜单项。
                        ServicePanels.entries.forEach { menu ->
                            MenuRow(menu) { menu.panel?.let { pushPage("panel:${it.name}") } }
                        }
                    }
                }

                SectionTitle(Strings.APP_GROUP, CartoonColors.Lilac)
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        MenuRow(
                            ServiceMenu(Strings.SETTINGS, Strings.THEME, AppIcons.Settings, 4)
                        ) { pushPage("settings") }
                        // v1.4.17：权限与通知合规入口
                        MenuRow(
                            ServiceMenu("权限管理", "必要 + 可选权限 · 默认全部关闭", AppIcons.Shield, 2)
                        ) { pushPage("permissions") }
                        MenuRow(
                            ServiceMenu("通知管理", "分类开关 · 免打扰 · 链路自检", AppIcons.Bell, 3)
                        ) { pushPage("notifications") }
                        MenuRow(
                            ServiceMenu(Strings.ABOUT, "", AppIcons.Info, 1),
                            subtitle = "版本 ${Strings.APP_VERSION}"
                        ) { pushPage("about") }
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

}

/**
 * 磨砂玻璃弹窗（v1.3.1 修复）：
 *  - 正文与按钮分离：[content] 在 ColumnScope 里纵向排列（修复主题选项被塞进
 *    横向 Row 槽导致 fillMaxWidth 后第一项占满整行、其余选项被挤出可视区的问题）；
 *  - 玻璃底改为近实色（白色 0.97 + 玻璃渐变叠加），背后内容不再透出。
 */
@Composable
private fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    text: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = CartoonKit.SheetShape,
            alpha = 1f,
            elevation = 24.dp
        ) {
            // 玻璃渐变之上再叠近实色白底，保证弹窗完全不透底
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.97f))
                    .padding(22.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = CartoonColors.Ink)
                if (text != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = CartoonColors.InkSub)
                }
                content?.let {
                    Spacer(Modifier.height(12.dp))
                    it()
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

/** 通用菜单行：图标 + 标题 + 副标题 + 右箭头（进入子页面/网页）。 */
@Composable
private fun MenuRow(menu: ServiceMenu, subtitle: String? = null, onClick: () -> Unit) {
    val (accent, soft) = CartoonColors.accent(menu.accentIdx)
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
                .background(soft),
            contentAlignment = Alignment.Center
        ) {
            Icon(menu.icon, null, tint = accent, modifier = Modifier.size(20.dp))
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
