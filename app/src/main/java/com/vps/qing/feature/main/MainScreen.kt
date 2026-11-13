package com.vps.qing.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.glass
import com.vps.qing.feature.dashboard.DashboardScreen
import com.vps.qing.feature.message.AnnouncementPage
import com.vps.qing.feature.message.MessagesScreen
import com.vps.qing.feature.profile.ProfileScreen
import com.vps.qing.feature.shop.CheckoutSheet
import com.vps.qing.feature.shop.ConfigSheet
import com.vps.qing.feature.shop.ShopScreen
import com.vps.qing.feature.shop.ShopViewModel

/** 底栏四个 Tab，图标全部为自绘 SVG；accent 按索引在组合期取当前主题色（主题切换即刷新）。 */
private enum class BottomTab(val title: String, val icon: ImageVector) {
    DASHBOARD(Strings.TAB_DASHBOARD, AppIcons.Dashboard),
    SHOP(Strings.TAB_SHOP, AppIcons.Shop),
    MESSAGE(Strings.TAB_MESSAGE, AppIcons.Message),
    PROFILE(Strings.TAB_PROFILE, AppIcons.Profile)
}

/**
 * 应用主容器 v1.4.1：以磨砂玻璃底栏 Tab 为核心。
 *  - 子页面（个人中心服务子页 / 设置页 / 订单详情 / 商品配置页）打开时隐藏底栏，整页沉浸；
 *  - 商品配置页（选购页自定义配置）在主容器层渲染，打开时隐藏底栏；
 *  - ShopViewModel 挂在 MAIN 这个 NavBackStackEntry 上，选购页与底栏角标共享同一实例，
 *    切 Tab 后购物车内容不丢失。
 */
@Composable
fun MainScreen(
    onOpenWeb: (String) -> Unit,
    onLoggedOut: () -> Unit,
    /** 首页「去登录」引导：回登录页并清栈（v1.4.6）。 */
    onNeedLogin: () -> Unit = {}
) {
    var selected by rememberSaveable { mutableStateOf(0) }
    /** 个人中心是否处于子页面（服务子页/设置页/订单详情）——隐藏底栏。 */
    var profileSubOpen by rememberSaveable { mutableStateOf(false) }
    /**
     * 首页发起的原生导航请求（v1.4.6）：
     *  - "hosts"/"orders"/"finance"/"tickets"/"cert"/"security"/"subaccounts" → 对应服务面板；
     *  - "product:{hostId}" → 已购产品详情。
     * 由 ProfileScreen 消费后回调置空；发起时切到「我的」Tab。
     */
    var profileNavRequest by rememberSaveable { mutableStateOf<String?>(null) }
    val openProfileNav: (String) -> Unit = { token ->
        profileNavRequest = token
        selected = 3
    }
    /** 网站公告独立页面（v1.4.7）：首页「更多」与消息页入口共用。 */
    var showAnnouncements by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showAnnouncements) { showAnnouncements = false }
    val shopVm: ShopViewModel = viewModel(
        factory = simpleViewModelFactory { ShopViewModel(AppContainer.catalogRepository) }
    )
    val shopState by shopVm.ui.collectAsState()

    // 商品配置页打开（任意 Tab 发起）时同样隐藏底栏；系统返回键 = 返回来源列表
    val configOpen = shopState.configSheet != null
    BackHandler(enabled = configOpen) { shopVm.dismissConfig() }

    // 原生结算支付面板（v1.4.8）：打开时隐藏底栏
    val checkoutOpen = shopState.checkout != null
    BackHandler(enabled = checkoutOpen) { shopVm.closeCheckout() }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (!profileSubOpen && !configOpen && !showAnnouncements && !checkoutOpen) {
                GlassBottomBar(
                    selected = selected,
                    cartCount = shopState.cartCount,
                    onSelect = { selected = it }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 网站公告独立页面：整页覆盖并隐藏底栏（v1.4.7）
            if (showAnnouncements) {
                AnnouncementPage(
                    onBack = { showAnnouncements = false },
                    onOpenWeb = onOpenWeb
                )
            } else when (selected) {
                0 -> DashboardScreen(
                    onOpenWeb = onOpenWeb,
                    onOpenPanel = openProfileNav,
                    onNeedLogin = onNeedLogin,
                    onGoShop = { selected = 1 },
                    onOpenAnnouncements = { showAnnouncements = true }
                )
                1 -> ShopScreen(vm = shopVm, onOpenWeb = onOpenWeb)
                2 -> MessagesScreen(
                    onOpenWeb = onOpenWeb,
                    onOpenAnnouncements = { showAnnouncements = true }
                )
                else -> ProfileScreen(
                    onLoggedOut = onLoggedOut,
                    onOpenWeb = onOpenWeb,
                    onSubPageChanged = { profileSubOpen = it },
                    navRequest = profileNavRequest,
                    onNavConsumed = { profileNavRequest = null },
                    onContinuePay = { orderId, amount, names -> shopVm.startPayOrder(orderId, amount, names) }
                )
            }

            // 商品配置页（选购页自定义配置）在主容器层渲染，打开时隐藏底栏
            shopState.configSheet?.let { sheet ->
                ConfigSheet(
                    sheet = sheet,
                    onSelectOption = shopVm::selectOption,
                    onSelectCycle = shopVm::selectCycle,
                    onQty = shopVm::changeConfigQty,
                    onConfirm = shopVm::confirmAddToCart,
                    onDismiss = shopVm::dismissConfig,
                    onOpenWeb = onOpenWeb,
                    onMfSelect = { shopVm.updateMfSelection(it) },
                    onSelectImage = { shopVm.selectMfImage(it) }
                )
            }

            // 原生结算支付面板（v1.4.8）：App 内完成支付，支付页仍用 WebView 打开
            shopState.checkout?.let { checkout ->
                CheckoutSheet(
                    checkout = checkout,
                    onDismiss = shopVm::closeCheckout,
                    onPayWith = shopVm::payWith,
                    onOpenPayUrl = onOpenWeb,
                    onCheckPayNow = shopVm::checkPayNow
                )
            }

            // 轻提示（加购成功等）：2 秒后自动消费
            shopState.toast?.let {
                LaunchedEffect(it) {
                    kotlinx.coroutines.delay(1800)
                    shopVm.consumeToast()
                }
                Box(
                    Modifier.fillMaxSize().padding(bottom = if (configOpen) 24.dp else 90.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    GlassSurface(
                        shape = CartoonKit.PillShape,
                        alpha = UiT.glassOverlay,
                        elevation = UiT.shadowLg
                    ) {
                        Row(
                            Modifier.padding(horizontal = UiT.spaceXl, vertical = UiT.spaceMd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(AppIcons.Check, null, tint = CartoonColors.Mint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = CartoonColors.Ink
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 磨砂玻璃底栏：半透明玻璃面 + 选中胶囊高亮 + 自绘图标 + 购物车角标。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassBottomBar(selected: Int, cartCount: Int, onSelect: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .glass(shape = CartoonKit.PillShape, alpha = UiT.glassBar, elevation = UiT.shadowLg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTab.entries.forEachIndexed { index, tab ->
                NavItem(
                    tab = tab,
                    selected = selected == index,
                    cartCount = cartCount,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.NavItem(
    tab: BottomTab,
    selected: Boolean,
    cartCount: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(CartoonKit.PillShape)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val showBadge = tab == BottomTab.SHOP && cartCount > 0
        val (accent, _) = CartoonColors.accent(BottomTab.entries.indexOf(tab))
        BadgedBox(
            badge = {
                if (showBadge) {
                    Badge(
                        containerColor = CartoonColors.Peach,
                        contentColor = Color.White
                    ) { Text("$cartCount") }
                }
            }
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CartoonKit.PillShape)
                    .background(if (selected) accent else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.title,
                    tint = if (selected) Color.White else CartoonColors.InkSub,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            tab.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else CartoonColors.InkSub
        )
    }
}
