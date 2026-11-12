package com.vps.qing.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.Strings
import com.vps.qing.feature.dashboard.DashboardScreen
import com.vps.qing.feature.message.MessagesScreen
import com.vps.qing.feature.profile.ProfileScreen
import com.vps.qing.feature.shop.ShopScreen
import com.vps.qing.feature.shop.ShopViewModel

/** 底栏四个 Tab 的顺序。用中文单字当图标，避免引入额外图标库依赖。 */
private enum class BottomTab(val title: String, val glyph: String) {
    DASHBOARD(Strings.TAB_DASHBOARD, Strings.GLYPH_DASHBOARD),
    SHOP(Strings.TAB_SHOP, Strings.GLYPH_SHOP),
    MESSAGE(Strings.TAB_MESSAGE, Strings.GLYPH_MESSAGE),
    PROFILE(Strings.TAB_PROFILE, Strings.GLYPH_PROFILE)
}

/**
 * 应用主容器：以底栏 Tab 为核心。
 * ShopViewModel 在 MAIN 这个 NavBackStackEntry 上创建，选购页与底栏角标共享同一实例，
 * 因此切 Tab 后购物车内容不会丢失。
 */
@Composable
fun MainScreen(
    onOpenWeb: (String) -> Unit,
    onLoggedOut: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(0) }
    val shopVm: ShopViewModel = viewModel(
        factory = simpleViewModelFactory { ShopViewModel(AppContainer.catalogRepository) }
    )
    val shopState by shopVm.ui.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomTab.entries.forEachIndexed { index, tab ->
                    BottomNavItem(
                        tab = tab,
                        index = index,
                        selected = selected,
                        cartCount = shopState.cartCount,
                        onClick = { selected = index }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                0 -> DashboardScreen(onOpenWeb = onOpenWeb)
                1 -> ShopScreen(vm = shopVm, onOpenWeb = onOpenWeb)
                2 -> MessagesScreen(onOpenWeb = onOpenWeb)
                else -> ProfileScreen(onLoggedOut = onLoggedOut, onOpenWeb = onOpenWeb)
            }
        }
    }
}

/**
 * 单独抽成 Composable：NavigationBarItem 的 icon/label 是 Composable lambda，
 * 直接在 forEachIndexed 的内联 lambda 里传会被 Compose 编译器判定为非 Composable 上下文。
 * 该版本 Material3 的 NavigationBarItem 没有 badge 参数，用 BadgedBox 包一层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.BottomNavItem(
    tab: BottomTab,
    index: Int,
    selected: Int,
    cartCount: Int,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected == index,
        onClick = onClick,
        alwaysShowLabel = true,
        icon = {
            if (tab == BottomTab.SHOP && cartCount > 0) {
                BadgedBox(badge = { Badge { Text("$cartCount") } }) {
                    Text(tab.glyph, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text(tab.glyph, style = MaterialTheme.typography.titleMedium)
            }
        },
        label = { Text(tab.title) }
    )
}
