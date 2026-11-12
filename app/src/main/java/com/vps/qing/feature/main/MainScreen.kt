package com.vps.qing.feature.main

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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.glass
import com.vps.qing.feature.dashboard.DashboardScreen
import com.vps.qing.feature.message.MessagesScreen
import com.vps.qing.feature.profile.ProfileScreen
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
 * 应用主容器：以磨砂玻璃底栏 Tab 为核心。
 * ShopViewModel 挂在 MAIN 这个 NavBackStackEntry 上，选购页与底栏角标共享同一实例，
 * 切 Tab 后购物车内容不丢失。
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
        containerColor = Color.Transparent,
        bottomBar = {
            GlassBottomBar(
                selected = selected,
                cartCount = shopState.cartCount,
                onSelect = { selected = it }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selected) {
                0 -> DashboardScreen(onOpenWeb = onOpenWeb)
                1 -> ShopScreen(vm = shopVm, onOpenWeb = onOpenWeb)
                2 -> MessagesScreen(onOpenWeb = onOpenWeb)
                else -> ProfileScreen(onLoggedOut = onLoggedOut, onOpenWeb = onOpenWeb)
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
            .glass(shape = CartoonKit.PillShape, alpha = 0.74f, elevation = 16.dp)
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
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else CartoonColors.InkSub
        )
    }
}
