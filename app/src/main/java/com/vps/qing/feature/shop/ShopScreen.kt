package com.vps.qing.feature.shop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vps.qing.core.AppContainer
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.CartoonRoundButton
import com.vps.qing.core.ui.EmptyHint
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.QuantityStepper
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.data.repository.CartCalculator
import com.vps.qing.data.repository.CartItem
import com.vps.qing.data.repository.ProductGroup
import com.vps.qing.data.repository.ProductInfo

/**
 * 选购 Tab（v1.2.1 优化）：
 *  - 顶栏磨砂玻璃；侧栏开关只在「采购页」出现（购物车 tab 隐藏）；
 *  - 采购页左侧是「常驻侧边栏」，可展开 / 收起；
 *    展开态分类名超出宽度时横向滚动显示（marquee）；
 *  - 展开侧栏时商品卡片文字有缩小动画，加购按钮只保留图标（不留空白）；
 *    收起时按钮文字单行不换行、不被切割；
 *  - 收起态缩略图标：选中用实心彩色圆底 + 白色图标，更醒目；
 *  - 分类下无商品时显示「该分类下暂无商品」；
 *  - 卡片边界清晰、布局紧凑，全部尺寸走 [UiT] 令牌。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShopScreen(vm: ShopViewModel, onOpenWeb: (String) -> Unit) {
    val state by vm.ui.collectAsState()

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.SHOP_TITLE,
                subtitle = "挑选适合你的云服务器",
                actions = {
                    // 侧栏开关只在采购页出现；购物车 tab 不需要它
                    if (state.tab == 0) {
                        SidebarToggleChip(
                            expanded = state.sidebarExpanded,
                            onClick = { vm.toggleSidebar() }
                        )
                        Spacer(Modifier.width(UiT.spaceSm))
                    }
                    CartoonIconChip(
                        icon = AppIcons.Refresh,
                        onClick = { vm.refresh() },
                        tint = CartoonColors.Mint,
                        bg = CartoonColors.MintSoft
                    )
                }
            )

            Spacer(Modifier.height(UiT.spaceSm + 2.dp))

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
                alpha = UiT.glassOverlay,
                elevation = UiT.shadowLg
            ) {
                Row(
                    Modifier.padding(horizontal = UiT.spaceXl, vertical = UiT.spaceMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(AppIcons.Check, null, tint = CartoonColors.Mint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(UiT.spaceSm))
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                }
            }
        }
    }
}

/** 侧栏开关：带文字的胶囊按钮，展开/收起双向语义清晰。 */
@Composable
private fun SidebarToggleChip(expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CartoonKit.PillShape)
            .background(CartoonColors.BrandSoft)
            .clickable { onClick() }
            .padding(horizontal = UiT.spaceMd, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (expanded) AppIcons.PanelClose else AppIcons.PanelOpen,
            contentDescription = if (expanded) Strings.COLLAPSE_SIDEBAR else Strings.EXPAND_SIDEBAR,
            tint = CartoonColors.Brand,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (expanded) Strings.COLLAPSE_SIDEBAR else Strings.EXPAND_SIDEBAR,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = CartoonColors.Brand,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TopTabRow(state: ShopUiState, vm: ShopViewModel) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiT.pagePad),
        shape = CartoonKit.PillShape,
        alpha = UiT.glass + 0.06f,
        elevation = UiT.shadowMd
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
                        .padding(vertical = 9.dp),
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
                        maxLines = 1,
                        softWrap = false,
                        color = if (selected) Color.White else CartoonColors.InkSub
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(UiT.spaceSm + 2.dp))
}

/** 采购页：常驻侧边栏 + 右侧商品列表。 */
@Composable
private fun PurchasePane(
    state: ShopUiState,
    vm: ShopViewModel,
    modifier: Modifier = Modifier
) {
    val sidebarWidth by animateDpAsState(
        targetValue = if (state.sidebarExpanded) UiT.sidebarExpandedWidth else UiT.sidebarCollapsedWidth
    )

    Row(modifier = modifier.fillMaxSize()) {
        // 常驻侧边栏
        GlassSurface(
            modifier = Modifier
                .width(sidebarWidth)
                .fillMaxHeight()
                .padding(start = UiT.pagePad, bottom = UiT.spaceMd),
            shape = CartoonKit.GlassShape,
            alpha = UiT.glassFaint,
            elevation = UiT.shadowMd
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(UiT.spaceSm),
                verticalArrangement = Arrangement.spacedBy(UiT.spaceXs + 2.dp)
            ) {
                if (state.firstGroups.isEmpty() && !state.groupsLoading) {
                    Text(
                        Strings.NO_GROUP,
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub,
                        modifier = Modifier.padding(UiT.spaceSm)
                    )
                }
                state.firstGroups.forEachIndexed { idx, first ->
                    SidebarItem(
                        name = first.name,
                        icon = AppIcons.forCategory(first.name),
                        expanded = state.sidebarExpanded,
                        isPrimary = true,
                        selected = state.selectedSecondId == null && state.selectedFirstId == first.id,
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
                                accentIndex = idx + sIdx + 1,
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
                state.visibleProducts.isEmpty() && state.error != null -> {
                    EmptyHint(
                        icon = AppIcons.EmptyBox,
                        text = state.error.orEmpty(),
                        modifier = Modifier.align(Alignment.Center),
                        action = {
                            CartoonButton(text = Strings.RETRY, onClick = { vm.refresh() })
                        }
                    )
                }
                state.visibleProducts.isEmpty() -> {
                    // 分类下没有商品：明确的空态提示，不放重试按钮
                    EmptyHint(
                        icon = AppIcons.EmptyBox,
                        text = Strings.NO_PRODUCT,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = UiT.spaceMd, end = UiT.pagePad, bottom = UiT.spaceLg
                        ),
                        verticalArrangement = Arrangement.spacedBy(UiT.spaceMd)
                    ) {
                        item {
                            Text(
                                "共 ${state.visibleProducts.size} 款商品",
                                style = MaterialTheme.typography.labelMedium,
                                color = CartoonColors.InkSub
                            )
                        }
                        items(state.visibleProducts, key = { it.id }) { product ->
                            ProductCard(
                                product = product,
                                compact = state.sidebarExpanded,
                                onCustomize = { vm.openConfig(product) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 侧边栏条目。
 *  - 展开态：图标 + 完整名称；名称超出宽度横向滚动显示（marquee），不省略；
 *  - 收起态：纵向「图标在上、两字缩略在下」；选中时图标底色为实心强调色 + 白色图标。
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val short = if (expanded) name else abbreviate(name)

    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CartoonKit.ItemShape)
                .background(if (selected) soft else Color.Transparent)
                .clickable { onClick() }
                .padding(horizontal = UiT.spaceSm + 2.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(if (isPrimary) 30.dp else 24.dp)
                    .clip(CartoonKit.PillShape)
                    .background(if (isPrimary) soft else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null,
                    tint = if (selected || isPrimary) accent else CartoonColors.InkSub,
                    modifier = Modifier.size(if (isPrimary) 18.dp else 16.dp)
                )
            }
            Spacer(Modifier.width(UiT.spaceSm))
            Text(
                name,
                style = if (isPrimary) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                fontWeight = if (isPrimary || selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) accent else CartoonColors.Ink,
                maxLines = 1,
                softWrap = false,
                // 超宽滚动式显示（需求：文字超出宽度选择滚动式显示）
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
    } else {
        // 收起态：图标在上、缩略字在下，整体居中；选中态实心彩色圆底
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CartoonKit.ItemShape)
                .clickable { onClick() }
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(if (isPrimary) 34.dp else 28.dp)
                    .clip(CartoonKit.ItemShape)
                    .background(if (selected) accent else soft.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null,
                    tint = if (selected) Color.White else accent,
                    modifier = Modifier.size(if (isPrimary) 18.dp else 15.dp)
                )
            }
            Spacer(Modifier.height(UiT.spaceXs))
            Text(
                short,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) accent else CartoonColors.InkSub,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
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

/**
 * 商品卡片。
 * [compact] = 侧栏展开：标题/规格文字做缩小动画（graphicsLayer 缩放），
 * 加购按钮只保留图标（图标圆按钮，绝不空白）；侧栏收起时按钮带完整文字且单行不换行。
 */
@Composable
private fun ProductCard(
    product: ProductInfo,
    compact: Boolean,
    onCustomize: () -> Unit
) {
    val textScale by animateFloatAsState(targetValue = if (compact) 0.86f else 1f)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(UiT.cardPad),
            verticalArrangement = Arrangement.spacedBy(UiT.spaceSm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CartoonKit.ItemShape)
                        .background(CartoonColors.SkySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Cloud, null, tint = CartoonColors.Sky, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(UiT.spaceMd))
                Column(
                    Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = textScale
                            scaleY = textScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                ) {
                    Text(
                        product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = CartoonColors.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        product.groupSecondName ?: product.groupFirstName ?: Strings.UNKNOWN,
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub
                    )
                }
            }

            if (product.specs.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.graphicsLayer {
                        scaleX = textScale
                        scaleY = textScale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                ) {
                    product.specs.take(4).forEach { spec ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CartoonKit.PillShape)
                                    .background(CartoonColors.Mint)
                            )
                            Spacer(Modifier.width(UiT.spaceSm))
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "￥${product.price}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = CartoonColors.Coral
                )
                Text(
                    " / ${product.cycle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CartoonColors.InkSub
                )
                Spacer(Modifier.weight(1f))
                if (compact) {
                    // 侧栏展开：只保留图标按钮，无空白
                    CartoonRoundButton(
                        icon = AppIcons.Sliders,
                        contentDescription = Strings.CUSTOMIZE,
                        onClick = onCustomize
                    )
                } else {
                    // 侧栏收起：完整文字按钮，单行绝不换行切割
                    CartoonButton(
                        text = Strings.CUSTOMIZE,
                        onClick = onCustomize,
                        icon = AppIcons.Sliders
                    )
                }
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
            contentPadding = PaddingValues(horizontal = UiT.pagePad, vertical = UiT.spaceXs),
            verticalArrangement = Arrangement.spacedBy(UiT.spaceMd)
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
                .padding(UiT.pagePad),
            shape = CartoonKit.CardShape,
            alpha = UiT.glassStrong,
            elevation = UiT.shadowLg
        ) {
            Column(
                Modifier.padding(UiT.cardPad),
                verticalArrangement = Arrangement.spacedBy(UiT.spaceSm + 2.dp)
            ) {
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
        Column(Modifier.padding(UiT.cardPad), verticalArrangement = Arrangement.spacedBy(UiT.spaceSm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CartoonKit.ItemShape)
                        .background(CartoonColors.MintSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Server, null, tint = CartoonColors.Mint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(UiT.spaceSm + 2.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.productName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CartoonColors.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
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
                        .clip(CartoonKit.ItemShape)
                        .background(CartoonColors.White.copy(alpha = 0.6f))
                        .padding(UiT.spaceSm + 2.dp),
                    verticalArrangement = Arrangement.spacedBy(UiT.spaceXs)
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
