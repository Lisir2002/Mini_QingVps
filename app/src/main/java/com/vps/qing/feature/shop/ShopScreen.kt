package com.vps.qing.feature.shop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vps.qing.core.AppContainer
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.EmptyHint
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.QuantityStepper
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.CartCalculator
import com.vps.qing.data.repository.CartItem
import com.vps.qing.data.repository.ProductGroup
import com.vps.qing.data.repository.ProductInfo

/**
 * 选购 Tab（v1.2.0 重构）：
 *  - 顶栏磨砂玻璃，两个菜单（采购页 / 购物车）；
 *  - 采购页左侧是「常驻侧边栏」，可展开（完整分类名）/ 收起（缩略 + 省略号）；
 *  - 商品按选中的分类在本地过滤（站点 /product 恒返回全量）；
 *  - 加购前弹自定义配置，确认后真实写入站点云端购物车；
 *  - 购物车 Tab 展示站点云端购物车条目及自定义配置。
 */
@Composable
fun ShopScreen(vm: ShopViewModel, onOpenWeb: (String) -> Unit) {
    val state by vm.ui.collectAsState()

    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            // toast 由 SnackbarHost 在 MainScreen 层统一呈现，这里只负责消费
        }
    }

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.SHOP_TITLE,
                subtitle = "挑选适合你的云服务器",
                actions = {
                    CartoonIconChip(
                        icon = if (state.sidebarExpanded) AppIcons.PanelClose else AppIcons.PanelOpen,
                        onClick = { vm.toggleSidebar() }
                    )
                    Spacer(Modifier.width(8.dp))
                    CartoonIconChip(
                        icon = AppIcons.Refresh,
                        onClick = { vm.refresh() },
                        tint = CartoonColors.Mint,
                        bg = CartoonColors.MintSoft
                    )
                }
            )

            Spacer(Modifier.height(10.dp))

            // 顶栏两个菜单：采购页 / 购物车（磨砂玻璃分段控件）
            TopTabRow(state, vm)

            if (state.cartLoading && state.tab == 1) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CartoonColors.Brand)
            }

            if (state.tab == 0) {
                PurchasePane(state, vm, modifier = Modifier.weight(1f))
            } else {
                CartPane(state, vm, onOpenWeb, modifier = Modifier.weight(1f))
            }
        }
    }

    state.configSheet?.let { sheet ->
        ConfigSheet(
            sheet = sheet,
            onSelectOption = vm::selectOption,
            onSelectCycle = vm::selectCycle,
            onQty = vm::changeConfigQty,
            onConfirm = vm::confirmAddToCart,
            onDismiss = vm::dismissConfig,
            onOpenWeb = onOpenWeb
        )
    }

    state.toast?.let {
        // 轻提示：用一个临时玻璃气泡，2 秒后自动消费
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(1800)
            vm.consumeToast()
        }
        Box(
            Modifier.fillMaxSize().padding(bottom = 90.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            GlassSurface(
                shape = CartoonKit.PillShape,
                alpha = 0.92f,
                elevation = 12.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(AppIcons.Check, null, tint = CartoonColors.Mint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                }
            }
        }
    }
}

@Composable
private fun TopTabRow(state: ShopUiState, vm: ShopViewModel) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = CartoonKit.PillShape,
        alpha = 0.72f,
        elevation = 8.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(5.dp)
        ) {
            listOf(
                Strings.SHOP_PURCHASE to AppIcons.Shop,
                "${Strings.SHOP_CART} (${state.cartCount})" to AppIcons.Cart
            ).forEachIndexed { index, (label, icon) ->
                val selected = state.tab == index
                Row(
                    Modifier
                        .weight(1f)
                        .clip(CartoonKit.PillShape)
                        .background(if (selected) CartoonColors.Brand else Color.Transparent)
                        .clickable { vm.setTab(index) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon, null,
                        tint = if (selected) Color.White else CartoonColors.InkSub,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else CartoonColors.InkSub
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** 采购页：常驻侧边栏 + 右侧商品列表。 */
@Composable
private fun PurchasePane(
    state: ShopUiState,
    vm: ShopViewModel,
    modifier: Modifier = Modifier
) {
    val sidebarWidth by animateDpAsState(
        targetValue = if (state.sidebarExpanded) 168.dp else 60.dp
    )

    Row(modifier = modifier.fillMaxSize()) {
        // 常驻侧边栏
        GlassSurface(
            modifier = Modifier
                .width(sidebarWidth)
                .fillMaxHeight()
                .padding(start = 14.dp, bottom = 12.dp),
            shape = CartoonKit.CardShape,
            alpha = 0.7f,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (state.firstGroups.isEmpty() && !state.groupsLoading) {
                    Text(
                        Strings.NO_GROUP,
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                state.firstGroups.forEachIndexed { idx, first ->
                    SidebarItem(
                        name = first.name,
                        icon = AppIcons.forCategory(first.name),
                        expanded = state.sidebarExpanded,
                        isPrimary = true,
                        selected = false,
                        accentIndex = idx,
                        onClick = { vm.selectFirst(first.id) }
                    )
                    if (state.selectedFirstId == first.id) {
                        state.currentSeconds.forEachIndexed { sIdx, second ->
                            SidebarItem(
                                name = second.name,
                                icon = AppIcons.forCategory(second.name),
                                expanded = state.sidebarExpanded,
                                isPrimary = false,
                                selected = state.selectedSecondId == second.id,
                                accentIndex = idx + sIdx,
                                onClick = { vm.selectSecond(second.id) }
                            )
                        }
                    }
                }
            }
        }

        // 右侧商品列表
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when {
                state.productsLoading -> {
                    LinearProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                        color = CartoonColors.Brand
                    )
                }
                state.visibleProducts.isEmpty() -> {
                    EmptyHint(
                        icon = AppIcons.EmptyBox,
                        text = if (state.error != null) state.error.orEmpty() else Strings.NO_PRODUCT,
                        modifier = Modifier.align(Alignment.Center),
                        action = {
                            CartoonButton(text = Strings.RETRY, onClick = { vm.refresh() })
                        }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 14.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    state.visibleProducts.size.let { "共 $it 款商品" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CartoonColors.InkSub
                                )
                            }
                        }
                        items(state.visibleProducts, key = { it.id }) { product ->
                            ProductCard(product = product, onCustomize = { vm.openConfig(product) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * 侧边栏条目。展开显示完整名称 + 图标；收起只显示图标 + 缩略文字，
 * 名称超过阈值时截断并显示省略号（需求：超过一定字符显示缩略符号）。
 */
@Composable
private fun SidebarItem(
    name: String,
    icon: ImageVector,
    expanded: Boolean,
    isPrimary: Boolean,
    selected: Boolean,
    accentIndex: Int,
    onClick: () -> Unit
) {
    val (accent, soft) = CartoonColors.accent(accentIndex)
    val bg = when {
        selected -> soft
        isPrimary -> Color.Transparent
        else -> Color.Transparent
    }
    val short = if (expanded) name else abbreviate(name)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = if (expanded) 10.dp else 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(if (isPrimary) 30.dp else 22.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isPrimary) soft else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                tint = if (selected) accent else if (isPrimary) accent else CartoonColors.InkSub,
                modifier = Modifier.size(if (isPrimary) 18.dp else 16.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.width(10.dp))
            Text(
                short,
                style = if (isPrimary) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                fontWeight = if (isPrimary || selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) accent else CartoonColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.width(4.dp))
            Text(
                short,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accent else CartoonColors.InkSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 收起态缩略：去掉 emoji/符号前缀，取前 2 个可见字符，过长补省略号。 */
private fun abbreviate(name: String): String {
    val clean = name.replace(Regex("[🎉✅🌹▪|｜\\s]"), "").trim()
    val core = if (clean.isEmpty()) name.trim() else clean
    return if (core.length > 2) core.take(2) + "…" else core
}

@Composable
private fun ProductCard(product: ProductInfo, onCustomize: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CartoonKit.CardShape)
                        .background(CartoonColors.SkySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Cloud, null, tint = CartoonColors.Sky, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = CartoonColors.Ink
                    )
                    Text(
                        product.groupSecondName ?: product.groupFirstName ?: Strings.UNKNOWN,
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub
                    )
                }
            }

            if (product.specs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    product.specs.take(4).forEach { spec ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).clip(CartoonKit.PillShape).background(CartoonColors.Mint))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                spec,
                                style = MaterialTheme.typography.bodySmall,
                                color = CartoonColors.InkSub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (product.specs.size > 4) {
                        Text(
                            Strings.SPEC_MORE.format(product.specs.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "￥${product.price}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = CartoonColors.Coral
                )
                Text(
                    " / ${product.cycle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CartoonColors.InkSub,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                CartoonButton(
                    text = Strings.CUSTOMIZE,
                    onClick = onCustomize,
                    icon = AppIcons.Sliders,
                    modifier = Modifier.height(44.dp)
                )
            }
        }
    }
}

/** 购物车：展示站点云端购物车条目（含自定义配置），改数量 / 删除均走云端接口。 */
@Composable
private fun CartPane(
    state: ShopUiState,
    vm: ShopViewModel,
    onOpenWeb: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.cart.isEmpty()) {
        EmptyHint(
            icon = AppIcons.Cart,
            text = Strings.CART_EMPTY,
            modifier = modifier.fillMaxSize(),
            action = {
                CartoonButton(text = Strings.SHOP_PURCHASE, onClick = { vm.setTab(0) }, icon = AppIcons.Shop)
            }
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.cart, key = { it.key }) { item ->
                CloudCartCard(
                    item = item,
                    onQty = { delta -> vm.changeCartQty(item, delta) },
                    onRemove = { vm.removeCartItem(item) }
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Cloud, null, tint = CartoonColors.Mint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        Strings.CART_CLOUD_TIP,
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub
                    )
                }
            }
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            shape = CartoonKit.CardShape,
            alpha = 0.85f,
            elevation = 12.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${Strings.TOTAL}：", style = MaterialTheme.typography.titleMedium, color = CartoonColors.Ink)
                    Text(
                        CartCalculator.money(state.cartTotal),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = CartoonColors.Coral
                    )
                }
                CartoonButton(
                    text = Strings.CHECKOUT,
                    onClick = { onOpenWeb(AppContainer.webUrl(AppContainer.CART_WEB_PATH)) },
                    icon = AppIcons.Forward,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CloudCartCard(item: CartItem, onQty: (Int) -> Unit, onRemove: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CartoonKit.CardShape)
                        .background(CartoonColors.MintSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Server, null, tint = CartoonColors.Mint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.productName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                    if (item.cycle.isNotBlank()) {
                        Text(item.cycle, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                    }
                }
                CartoonIconChip(
                    icon = AppIcons.Trash,
                    onClick = onRemove,
                    tint = CartoonColors.Peach,
                    bg = CartoonColors.PeachSoft
                )
            }

            // 自定义配置明细
            if (item.configLabels.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CartoonColors.White.copy(alpha = 0.6f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item.configLabels.forEach { label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Check, null, tint = CartoonColors.Mint, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.bodySmall, color = CartoonColors.Ink)
                        }
                    }
                }
            } else if (item.specs.isNotEmpty()) {
                item.specs.take(3).forEach { spec ->
                    Text("· $spec", style = MaterialTheme.typography.bodySmall, color = CartoonColors.InkSub)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    CartCalculator.money(item.unitPrice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CartoonColors.InkSub
                )
                Spacer(Modifier.weight(1f))
                QuantityStepper(item.quantity, onQty, min = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "小计 ${CartCalculator.money(item.subtotal)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Brand
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text(Strings.REMOVE, color = CartoonColors.InkSub) }
            }
        }
    }
}
