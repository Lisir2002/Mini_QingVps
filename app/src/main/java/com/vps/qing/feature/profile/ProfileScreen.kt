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
import androidx.compose.material3.LinearProgressIndicator
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
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.accountDisplay
import com.vps.qing.core.ui.CertBadge
import com.vps.qing.core.ui.CertBadgeChip
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.ScrollGlassEffect

/** 实名认证：站点未暴露 API，保留网页兜底。 */
private val certificationMenu = ServiceMenu(
    Strings.CERTIFICATION, "实名状态与认证办理", AppIcons.Shield, 2,
    panel = ServicePanel.CERTIFICATION
)

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
    /** 独立「关于」页面（v1.4.12，取代原弹窗）。 */
    var showAboutPage by rememberSaveable { mutableStateOf(false) }
    /** 当前打开的服务子页面；null = 个人中心主页。 */
    var servicePage by rememberSaveable { mutableStateOf<ServicePanel?>(null) }
    /** 应用设置子页面。 */
    var showSettingsPage by rememberSaveable { mutableStateOf(false) }
    /**
     * 详情页返回栈（v1.4.2）：逗号分隔的 "product:{hostId}" / "order:{orderId}"。
     * 用栈而非两个互斥 id，是为了让「已购产品详情 ↔ 订单详情」可以互相跳转并逐级返回，
     * 不会出现点了关联订单却被上层页面盖住、或返回时丢失上一页的问题。
     */
    var detailStack by rememberSaveable { mutableStateOf("") }
    val accentId by AppContainer.settings.accentId.collectAsState()

    val currentDetail = detailStack.substringAfterLast(',').takeIf { it.isNotBlank() }
    val pushDetail: (String) -> Unit = { token ->
        detailStack = if (detailStack.isBlank()) token else "$detailStack,$token"
    }
    val popDetail: () -> Unit = {
        detailStack = detailStack.substringBeforeLast(',', "")
    }
    val openOrderDetail: (String) -> Unit = { pushDetail("order:$it") }
    /** 打开已购产品详情：hostId 优先，缺失时按商品名在已购列表里兜底匹配。 */
    val openHostDetail: (String, String) -> Unit = { hostId, productName ->
        val target = hostId.takeIf { it.isNotBlank() }
            ?: state.panels[ServicePanel.HOSTS]?.hosts
                ?.firstOrNull { productName.isNotBlank() && it.productName == productName }?.id
        if (target != null) pushDetail("product:$target")
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

    // 子页面打开时：进入懒加载 + 系统返回键逐级回退（详情页 → 服务页 / 设置页 → 主页）
    LaunchedEffect(servicePage) { servicePage?.let(vm::ensurePanel) }

    // v1.4.6：消费首页发起的导航请求（服务面板直达 / 产品详情直达）
    LaunchedEffect(navRequest) {
        if (navRequest != null) {
            val panel = when (navRequest) {
                "hosts" -> ServicePanel.HOSTS
                "orders" -> ServicePanel.ORDERS
                "finance" -> ServicePanel.TRANSACTIONS
                "tickets" -> ServicePanel.TICKETS
                "subaccounts" -> ServicePanel.SUB_ACCOUNTS
                "security" -> ServicePanel.SECURITY
                "cert" -> ServicePanel.CERTIFICATION
                else -> null
            }
            if (panel != null) servicePage = panel
            else if (navRequest.startsWith("product:")) pushDetail(navRequest)
            onNavConsumed()
        }
    }
    LaunchedEffect(currentDetail) {
        when {
            currentDetail == null -> Unit
            currentDetail.startsWith("product:") ->
                vm.ensureProductDetail(currentDetail.removePrefix("product:"))
            currentDetail.startsWith("order:") ->
                vm.ensureOrderDetail(currentDetail.removePrefix("order:"))
            currentDetail.startsWith("ticket:") ->
                vm.ensureTicketDetail(currentDetail.removePrefix("ticket:"))
            currentDetail == "ticketnew" || currentDetail == "subnew" -> vm.ensureFormMeta()
            currentDetail.startsWith("subedit:") -> {
                vm.ensureFormMeta()
                vm.ensureSubAccountDetail(currentDetail.removePrefix("subedit:"))
            }
        }
    }
    // 上报主容器：任意子页面打开即隐藏底栏
    LaunchedEffect(servicePage, showSettingsPage, showAboutPage, detailStack) {
        onSubPageChanged(
            servicePage != null || showSettingsPage || showAboutPage || detailStack.isNotBlank()
        )
    }
    BackHandler(
        enabled = servicePage != null || showSettingsPage || showAboutPage || detailStack.isNotBlank()
    ) {
        when {
            detailStack.isNotBlank() -> popDetail()
            showAboutPage -> showAboutPage = false
            showSettingsPage -> showSettingsPage = false
            else -> servicePage = null
        }
    }

    // 栈顶详情页优先渲染（订单详情与已购产品详情可互相跳转）
    currentDetail?.let { token ->
        when {
            token.startsWith("product:") -> {
                val hostId = token.removePrefix("product:")
                PurchasedProductPage(
                    hostId = hostId,
                    state = state.productDetails[hostId] ?: ProductDetailUi(),
                    onBack = popDetail,
                    onRefresh = { vm.refreshProductDetail(hostId) },
                    onOpenOrder = openOrderDetail
                )
                return
            }
            token.startsWith("order:") -> {
                val orderId = token.removePrefix("order:")
                OrderDetailPage(
                    orderId = orderId,
                    state = state.orderDetails[orderId] ?: OrderDetailUi(),
                    onBack = popDetail,
                    onRefresh = { vm.refreshOrderDetail(orderId) },
                    onOpenProduct = openHostDetail,
                    onContinuePay = onContinuePay
                )
                return
            }
            // v1.4.3：工单详情 / 新建工单
            token.startsWith("ticket:") -> {
                TicketDetailPage(
                    vm = vm,
                    ticketId = token.removePrefix("ticket:"),
                    onBack = popDetail
                )
                return
            }
            token == "ticketnew" -> {
                TicketCreatePage(
                    vm = vm,
                    onBack = popDetail,
                    onCreated = popDetail
                )
                return
            }
            // v1.4.3：子账户新建 / 编辑
            token == "subnew" -> {
                SubAccountFormPage(vm = vm, editId = null, onBack = popDetail, onSaved = popDetail)
                return
            }
            token.startsWith("subedit:") -> {
                SubAccountFormPage(
                    vm = vm,
                    editId = token.removePrefix("subedit:"),
                    onBack = popDetail,
                    onSaved = popDetail
                )
                return
            }
            // v1.4.12：安全中心编辑整页（密码 / 邮箱 / 手机）
            token.startsWith("secedit:") -> {
                SecurityEditPage(
                    vm = vm,
                    target = token.removePrefix("secedit:"),
                    onBack = popDetail
                )
                return
            }
        }
    }

    servicePage?.let { panel ->
        ServicePageScreen(
            panel = panel,
            data = state.panels[panel],
            onBack = { servicePage = null },
            onRefresh = { vm.refreshPanel(panel) },
            onOpenOrder = openOrderDetail,
            onOpenProduct = { openHostDetail(it, "") },
            onOpenWeb = onOpenWeb,
            onOpenTicket = { pushDetail("ticket:$it") },
            onCreateTicket = { pushDetail("ticketnew") },
            onTicketFilter = vm::setTicketFilter,
            ticketFilterStatusId = state.ticketFilterStatusId,
            onOpenSubAccount = { pushDetail("subedit:$it") },
            onCreateSubAccount = { pushDetail("subnew") },
            onToggleSubAccount = vm::toggleSubAccountStatus,
            onDeleteSubAccount = vm::deleteSubAccount,
            // v1.4.5 实名认证提交与进度
            certForm = state.certForm,
            onSelectCertPlugin = vm::selectCertPlugin,
            onCertCompanyMode = vm::setCertCompanyMode,
            onCertFormChange = { newForm -> vm.updateCertForm { newForm } },
            onSubmitCert = vm::submitCert,
            actionBusy = state.actionBusy,
            // v1.4.12 订单 / 交易记录分类
            orderFilter = state.orderFilter,
            onOrderFilter = vm::setOrderFilter,
            txnFilter = state.txnFilter,
            onTxnFilter = vm::setTxnFilter,
            onEditSecurity = { pushDetail("secedit:$it") }
        )
        return
    }

    if (showSettingsPage) {
        ThemeSettingsPage(
            currentMode = themeMode,
            currentAccentId = accentId,
            onSelectMode = { AppContainer.settings.setThemeMode(it) },
            onSelectAccent = { AppContainer.settings.setAccentId(it) },
            onBack = { showSettingsPage = false }
        )
        return
    }

    // v1.4.12：关于改为独立整页（占位五项，后续逐项补充）
    if (showAboutPage) {
        AboutPage(onBack = { showAboutPage = false })
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
                                // v1.4.12：未实名显示账号名 + 未实名徽标，不冒充实名姓名
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
                                            onClick = if (display.badge == CertBadge.UNCERTIFIED) {
                                                { servicePage = ServicePanel.CERTIFICATION }
                                            } else null
                                        )
                                    }
                                }
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
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        // 账号与服务：每项点击进入独立子页面
                        ServicePanels.entries.forEach { menu ->
                            MenuRow(menu) { menu.panel?.let { servicePage = it } }
                        }
                        // 实名认证：v1.4.0 原生认证页（深度适配 /certification/*）
                        MenuRow(certificationMenu) { certificationMenu.panel?.let { servicePage = it } }
                    }
                }

                SectionTitle(Strings.APP_GROUP, CartoonColors.Lilac)
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = UiT.glassCard) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        MenuRow(
                            ServiceMenu(Strings.SETTINGS, Strings.THEME, AppIcons.Settings, 4)
                        ) { showSettingsPage = true }
                        MenuRow(
                            ServiceMenu(Strings.ABOUT, "", AppIcons.Info, 1),
                            subtitle = "版本 ${Strings.APP_VERSION}"
                        ) { showAboutPage = true }
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
