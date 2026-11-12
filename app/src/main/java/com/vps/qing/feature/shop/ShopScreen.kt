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
import androidx.activity.compose.BackHandler
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
                    // 侧栏开关内嵌在侧边栏底部（v1.2.2），顶栏只保留刷新
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

    // 配置详情独立页：系统返回键 = 返回选购列表
    BackHandler(enabled = state.configSheet != null) { vm.dismissConfig() }

    state.configSheet?.let { sheet ->
        ConfigSheet(
            sheet = sheet,
            onSelectOption = vm::selectOption,
            onSelectCycle = vm::selectCycle,
            onQty = vm::changeConfigQty,
            onConfirm = vm::confirmAddToCart,
            onDismiss = vm::dismissConfig,
            onOpenWeb = onOpenWeb,
            onMfSelect = { vm.updateMfSelection(it) },
            onSelectImage = { vm.selectMfImage(it) }
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

/** 侧边栏底部内嵌开关：展开态是带文字的胶囊，收起态是居中的圆形图标按钮。 */
@Composable
private fun SidebarToggle(expanded: Boolean, onClick: () -> Unit) {
    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CartoonKit.PillShape)
                .background(CartoonColors.BrandSoft)
                .clickable { onClick() }
                .padding(horizontal = UiT.spaceMd, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                AppIcons.PanelClose, null,
                tint = CartoonColors.Brand,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                Strings.COLLAPSE_SIDEBAR,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Brand,
                maxLines = 1,
                softWrap = false
            )
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = UiT.spaceXs),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CartoonKit.PillShape)
                    .background(CartoonColors.BrandSoft)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AppIcons.PanelOpen, null,
                    tint = CartoonColors.Brand,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
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
                        modifier = Modifier.size(UiT.buttonIcon)
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
        // 常驻侧边栏（开关内嵌在底部，v1.2.2）
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
                    .padding(UiT.spaceSm)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
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
                    // 一级分类（产品类型）：手风琴展开对应二级分类（商品分类）
                    state.firstGroups.forEachIndexed { idx, first ->
                        val seconds = state.secondGroups[first.id].orEmpty()
                        val open = state.openFirstId == first.id
                        SidebarFirstItem(
                            name = first.name,
                            icon = AppIcons.forCategory(first.name),
                            sidebarExpanded = state.sidebarExpanded,
                            selected = state.selectedFirstId == first.id,
                            open = open && seconds.isNotEmpty(),
                            hasChildren = seconds.isNotEmpty() ||
                                state.secondGroups[first.id] == null,
                            accentIndex = idx,
                            onHeaderClick = { vm.selectFirst(first.id) },
                            onToggle = { vm.toggleFirstExpanded(first.id) }
                        )
                        if (open) {
                            seconds.forEachIndexed { sIdx, second ->
                                SidebarSecondItem(
                                    name = second.name,
                                    icon = AppIcons.forCategory(second.name),
                                    sidebarExpanded = state.sidebarExpanded,
                                    selected = state.selectedSecondId == second.id,
                                    accentIndex = idx + sIdx + 1,
                                    onClick = { vm.selectSecond(second.id) }
                                )
                            }
                        }
                    }
                }
                SidebarToggle(
                    expanded = state.sidebarExpanded,
                    onClick = { vm.toggleSidebar() }
                )
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
                    // 选中了没有商品的分类（v1.2.2 起二级严格过滤，空分类有明确提示）
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
 * 一级分类条目（产品类型）：
 *  - 展开态：图标 + 名称（超宽 marquee）+ chevron（手风琴箭头，可点收起/展开）；
 *  - 点主体 = 选中该分类并展开；点 chevron = 仅折叠/展开；
 *  - 收起态：图标 + 缩略字，chevron 融入缩略下方小箭头。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarFirstItem(
    name: String,
    icon: ImageVector,
    sidebarExpanded: Boolean,
    selected: Boolean,
    open: Boolean,
    hasChildren: Boolean,
    accentIndex: Int,
    onHeaderClick: () -> Unit,
    onToggle: () -> Unit
) {
    val (accent, soft) = CartoonColors.accent(accentIndex)
    val chevronRotation by animateFloatAsState(targetValue = if (open) 0f else -90f)

    if (sidebarExpanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CartoonKit.ItemShape)
                .background(if (selected) soft else Color.Transparent)
                .clickable { onHeaderClick() }
                .padding(horizontal = UiT.spaceSm + 2.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CartoonKit.PillShape)
                    .background(soft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(UiT.spaceSm))
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) accent else CartoonColors.Ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )
            if (hasChildren) {
                Icon(
                    AppIcons.ChevronDown,
                    contentDescription = if (open) Strings.COLLAPSE_SIDEBAR else Strings.CATEGORY,
                    tint = if (selected) accent else CartoonColors.InkSub,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onToggle() }
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CartoonKit.ItemShape)
                .clickable { onHeaderClick() }
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CartoonKit.ItemShape)
                    .background(if (selected) accent else soft.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null,
                    tint = if (selected) Color.White else accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(UiT.spaceXs))
            Text(
                abbreviate(name),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) accent else CartoonColors.InkSub,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )
            if (hasChildren) {
                Icon(
                    AppIcons.ChevronDown, null,
                    tint = CartoonColors.InkSub.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(11.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
        }
    }
}

/**
 * 二级分类条目（商品分类）：展开态带缩进与小圆点，体现手风琴层次；
 * 收起态以更小号的图标呈现。
 */
@Composable
private fun SidebarSecondItem(
    name: String,
    icon: ImageVector,
    sidebarExpanded: Boolean,
    selected: Boolean,
    accentIndex: Int,
    onClick: () -> Unit
) {
    val (accent, soft) = CartoonColors.accent(accentIndex)
    if (sidebarExpanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = UiT.spaceLg)
                .clip(CartoonKit.ItemShape)
                .background(if (selected) soft else Color.Transparent)
                .clickable { onClick() }
                .padding(horizontal = UiT.spaceSm, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CartoonKit.PillShape)
                    .background(if (selected) accent else accent.copy(alpha = 0.5f))
            )
            Spacer(Modifier.width(UiT.spaceSm + 2.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) accent else CartoonColors.InkSub,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CartoonKit.ItemShape)
                .clickable { onClick() }
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CartoonKit.ItemShape)
                    .background(if (selected) accent else soft.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null,
                    tint = if (selected) Color.White else accent,
                    modifier = Modifier.size(14.dp)
                )
            }
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
 * 商品卡片。侧栏展开时（[compact]=true）标题/规格/价格以 0.85× 统一缩放适配窄栏；
 * 加购按钮固定尺寸不缩放：展开态为圆形购物车图标按钮（图标与顶栏 tab 等大，
 * 修复「有的图标大有的图标小」），收起态为「加入购物车」文字按钮（v1.2.4）。
 */
@Composable
private fun ProductCard(
    product: ProductInfo,
    compact: Boolean,
    onCustomize: () -> Unit
) {
    val scale by animateFloatAsState(targetValue = if (compact) 0.85f else 1f)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(UiT.cardPad)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
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
                Column(Modifier.weight(1f)) {
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
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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

            // 价格随卡片整体缩放；加购按钮不参与缩放（固定尺寸），
            // 避免展开侧栏时按钮图标与顶栏购物车图标一大一小（v1.2.4）
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(UiT.spaceXs)) {
                    Row(
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                    ) {
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
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        CartoonRoundButton(
                            icon = AppIcons.Cart,
                            contentDescription = Strings.ADD_TO_CART,
                            onClick = onCustomize
                        )
                    }
                }
            } else {
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
                    CartoonButton(
                        text = Strings.ADD_TO_CART,
                        onClick = onCustomize,
                        icon = AppIcons.Cart
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
