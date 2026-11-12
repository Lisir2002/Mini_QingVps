package com.vps.qing.feature.shop

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vps.qing.core.AppContainer
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.CartCalculator
import com.vps.qing.data.repository.CartItem
import com.vps.qing.data.repository.ProductGroup
import com.vps.qing.data.repository.ProductInfo

/**
 * 选购 Tab：顶栏两个菜单（采购页 / 购物车）。
 * 采购页左侧是分类侧边栏（一级分组 + 二级分类），右侧是该分类的商品介绍卡片；
 * 加购时弹出配置确认，加入后可在购物车菜单看到服务器及其配置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(vm: ShopViewModel, onOpenWeb: (String) -> Unit) {
    val state by vm.ui.collectAsState()
    var productForConfig by remember { mutableStateOf<ProductInfo?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(Strings.SHOP_TITLE) },
            actions = { TextButton(onClick = { vm.refreshProducts() }) { Text(Strings.REFRESH) } }
        )

        TabRow(selectedTabIndex = state.tab) {
            Tab(
                selected = state.tab == 0,
                onClick = { vm.setTab(0) },
                text = { Text(Strings.SHOP_PURCHASE) }
            )
            Tab(
                selected = state.tab == 1,
                onClick = { vm.setTab(1) },
                text = { Text("${Strings.SHOP_CART} (${state.cartCount})") }
            )
        }

        if (state.tab == 0) {
            PurchasePane(
                state = state,
                vm = vm,
                onAdd = { productForConfig = it },
                modifier = Modifier.weight(1f)
            )
        } else {
            CartPane(
                state = state,
                vm = vm,
                onOpenWeb = onOpenWeb,
                modifier = Modifier.weight(1f)
            )
        }
    }

    productForConfig?.let { product ->
        AddToCartDialog(
            product = product,
            onConfirm = { qty ->
                vm.addToCart(product, qty)
                productForConfig = null
            },
            onDismiss = { productForConfig = null }
        )
    }
}

@Composable
private fun PurchasePane(
    state: ShopUiState,
    vm: ShopViewModel,
    onAdd: (ProductInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxSize()) {
        // 左侧分类侧边栏
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                Strings.CATEGORY,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (state.firstGroups.isEmpty() && !state.groupsLoading) {
                Text(Strings.NO_GROUP, style = MaterialTheme.typography.bodySmall)
            }
            state.firstGroups.forEach { first ->
                FirstCategoryItem(
                    group = first,
                    selected = state.selectedFirstId == first.id,
                    onClick = { vm.selectFirst(first.id) }
                )
                if (state.selectedFirstId == first.id) {
                    state.currentSeconds.forEach { second ->
                        SecondCategoryItem(
                            group = second,
                            selected = state.selectedSecondId == second.id,
                            onClick = { vm.selectSecond(second.id) }
                        )
                    }
                }
            }
        }

        // 右侧：该分类下的商品介绍卡片
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when {
                state.groupsLoading || state.productsLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            state.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.refreshProducts() }) { Text(Strings.RETRY) }
                    }
                }
                state.products.isEmpty() -> {
                    Text(
                        Strings.NO_PRODUCT,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.products, key = { it.id }) { product ->
                            ProductCard(product = product, onAdd = onAdd)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstCategoryItem(group: ProductGroup, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = group.name,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SecondCategoryItem(group: ProductGroup, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = group.name,
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 8.dp)
    )
}

@Composable
private fun ProductCard(product: ProductInfo, onAdd: (ProductInfo) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Text(
                product.groupSecondName ?: product.groupFirstName ?: Strings.UNKNOWN,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "￥${product.price}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    " / ${product.cycle}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
                Spacer(Modifier.weight(1f))
                product.qty?.let {
                    Text(
                        "${Strings.STOCK} $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (product.specs.isNotEmpty()) {
                Text(Strings.CONFIG, style = MaterialTheme.typography.labelSmall)
                product.specs.take(4).forEach { spec ->
                    Text(
                        "· $spec",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (product.specs.size > 4) {
                    Text(
                        "等 ${product.specs.size} 项配置",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = { onAdd(product) },
                modifier = Modifier.align(Alignment.End)
            ) { Text(Strings.ADD_TO_CART) }
        }
    }
}

/** 加购确认：展示商品真实配置项，选数量后写入本地购物车。 */
@Composable
private fun AddToCartDialog(
    product: ProductInfo,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var quantity by remember { mutableStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.ADD_DIALOG_TITLE) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    Strings.ADD_DIALOG_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("${Strings.PRICE}：￥${product.price} / ${product.cycle}")
                if (product.specs.isNotEmpty()) {
                    Text(Strings.CONFIG, style = MaterialTheme.typography.labelSmall)
                    product.specs.take(8).forEach { spec ->
                        Text("· $spec", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Strings.QUANTITY, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = { if (quantity > 1) quantity-- }) { Text("-") }
                    Text("$quantity", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { if (quantity < 10) quantity++ }) { Text("+") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(quantity) }) { Text(Strings.ADD_TO_CART) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        }
    )
}

@Composable
private fun CartPane(
    state: ShopUiState,
    vm: ShopViewModel,
    onOpenWeb: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.cart.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(Strings.CART_EMPTY, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.setTab(0) }) { Text(Strings.SHOP_PURCHASE) }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.cart, key = { it.key }) { item ->
                CartItemCard(
                    item = item,
                    onQty = { delta -> vm.changeQty(item.key, delta) },
                    onRemove = { vm.removeFromCart(item.key) }
                )
            }
            item {
                Text(
                    Strings.CART_LOCAL_TIP,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${Strings.TOTAL}：", style = MaterialTheme.typography.titleMedium)
                    Text(
                        CartCalculator.money(state.cartTotal),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.clearCart() }) { Text(Strings.CLEAR) }
                }
                Button(
                    onClick = { onOpenWeb(AppContainer.webUrl(AppContainer.CART_WEB_PATH)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(Strings.CHECKOUT) }
            }
        }
    }
}

@Composable
private fun CartItemCard(item: CartItem, onQty: (Int) -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.productName, style = MaterialTheme.typography.titleMedium)
            if (item.groupName.isNotBlank()) {
                Text(
                    item.groupName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.specs.isNotEmpty()) {
                Text(Strings.CONFIG, style = MaterialTheme.typography.labelSmall)
                item.specs.take(5).forEach { spec ->
                    Text(
                        "· $spec",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${CartCalculator.money(item.unitPrice)} / ${item.cycle}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onQty(-1) }) { Text("-") }
                Text("${item.quantity}", style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onQty(1) }) { Text("+") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "小计 ${CartCalculator.money(item.subtotal)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text(Strings.REMOVE) }
            }
        }
    }
}
