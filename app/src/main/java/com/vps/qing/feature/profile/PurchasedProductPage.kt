package com.vps.qing.feature.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.EmptyHint
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassPullRefreshBox
import com.vps.qing.core.ui.GlassRefreshChip
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.LoadingSkeleton
import com.vps.qing.core.ui.ScrollGlassEffect
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.glassFieldColors
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.data.repository.HostOpLog
import com.vps.qing.data.repository.HostOpsBodies
import com.vps.qing.data.repository.MfImageGroup
import com.vps.qing.data.repository.PurchasedDetail
import com.vps.qing.feature.profile.HostDetailTab.BILLING
import com.vps.qing.feature.profile.HostDetailTab.CONNECT
import com.vps.qing.feature.profile.HostDetailTab.OVERVIEW
import com.vps.qing.feature.profile.HostDetailTab.REINSTALL

/**
 * 已购产品详情（v1.4.2 建立 / v1.4.22 重构为 tab 导航）：
 * 数据来自 GET /host/{id}、GET /remf_cloud/{id}、GET /host/{id}/specific_info。
 * v1.4.22：拆为 概览 / 连接信息 / 重装系统 / 费用与订单 四个 tab，
 * 避免全部堆在单页；连接信息支持逐项与一键复制，重装 tab 对接官方接口。
 */
@Composable
fun PurchasedProductPage(
    hostId: String,
    state: ProductDetailUi,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenOrder: (String) -> Unit = {},
    onSelectTab: (Int) -> Unit = {},
    onReinstall: (Int, String, String) -> Unit = { _, _, _ -> },
    onPower: (String) -> Unit = {},
    onResetPassword: (String, String) -> Unit = { _, _ -> },
    onRefreshLogs: () -> Unit = {}
) {
    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "已购产品详情",
                subtitle = state.detail?.name?.takeIf { it.isNotBlank() } ?: "#$hostId",
                navIcon = AppIcons.Back,
                onNavClick = onBack,
                actions = {
                    GlassRefreshChip(
                        refreshing = state.loading,
                        onClick = onRefresh,
                        tint = CartoonColors.Mint,
                        bg = CartoonColors.MintSoft
                    )
                }
            )
            GlassPullRefreshBox(
                isRefreshing = state.loading && state.detail != null,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                error = state.error
            ) {
                when {
                    state.loading && state.detail == null -> {
                        LoadingSkeleton(
                            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                            rows = 3
                        )
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
                        EmptyHint(icon = AppIcons.EmptyBox, text = "产品不存在", modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        ProductDetailBody(
                            d = state.detail,
                            hostId = hostId,
                            state = state,
                            onOpenOrder = onOpenOrder,
                            onSelectTab = onSelectTab,
                            onReinstall = onReinstall,
                            onPower = onPower,
                            onResetPassword = onResetPassword,
                            onRefreshLogs = onRefreshLogs
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductDetailBody(
    d: PurchasedDetail,
    hostId: String,
    state: ProductDetailUi,
    onOpenOrder: (String) -> Unit,
    onSelectTab: (Int) -> Unit,
    onReinstall: (Int, String, String) -> Unit,
    onPower: (String) -> Unit,
    onResetPassword: (String, String) -> Unit,
    onRefreshLogs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部分段 Tab 导航
        DetailTabRow(selected = state.tab, onSelect = onSelectTab)
        val listState = rememberLazyListState()
        // 切换 tab 后回到顶部，避免沿用上一 tab 的滚动位置
        LaunchedEffect(state.tab) {
            if (listState.firstVisibleItemIndex > 0) listState.animateScrollToItem(0)
        }
        ScrollGlassEffect(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (state.tab) {
                OVERVIEW.ordinal -> overviewTab(d)
                CONNECT.ordinal -> connectTab(d)
                REINSTALL.ordinal -> reinstallTab(
                    d = d, hostId = hostId, state = state,
                    onReinstall = onReinstall, onPower = onPower,
                    onResetPassword = onResetPassword, onRefreshLogs = onRefreshLogs
                )
                BILLING.ordinal -> billingTab(d, onOpenOrder)
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

/** 顶部分段 Tab（玻璃胶囊风格，对齐设置页 SettingsTabRow）。 */
@Composable
private fun DetailTabRow(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        Strings.HOST_TAB_OVERVIEW, Strings.HOST_TAB_CONNECT,
        Strings.HOST_TAB_REINSTALL, Strings.HOST_TAB_BILLING
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(CartoonKit.PillShape)
            .background(CartoonColors.White.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tabs.forEachIndexed { idx, label ->
            val on = selected == idx
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CartoonKit.PillShape)
                    .background(if (on) CartoonColors.Brand else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(idx) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) androidx.compose.ui.graphics.Color.White else CartoonColors.InkSub,
                    maxLines = 1
                )
            }
        }
    }
}

// ==================== Tab 1：概览 ====================

private fun androidx.compose.foundation.lazy.LazyListScope.overviewTab(d: PurchasedDetail) {
    item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(CartoonKit.ItemShape).background(CartoonColors.BrandSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Server, null, tint = CartoonColors.Brand, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.productName.ifBlank { "已购产品 #${d.id}" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = CartoonColors.Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "机器名 ${d.name.ifBlank { "--" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    statusBadge(d.status)
                    Spacer(Modifier.width(8.dp))
                    powerBadge(d.powerStatus)
                }
                if (d.ip.isNotBlank()) KeyValueRow("IP 地址", d.ip)
                val region = listOf(d.country, d.city, d.area).filter { it.isNotBlank() }
                    .distinct().joinToString(" · ")
                if (region.isNotBlank()) KeyValueRow("所在地区", region)
            }
        }
    }
    item { SectionTitle("配置信息", CartoonColors.Brand) }
    item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (!d.hasCloudConfig) {
                    Text(
                        "该商品未提供实例配置接口，仅展示订单与支付信息",
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub
                    )
                    Spacer(Modifier.height(2.dp))
                }
                if (d.cpu.isNotBlank()) KeyValueRow("CPU", "${d.cpu} 核")
                if (d.memory.isNotBlank()) KeyValueRow("内存", "${d.memory} GB")
                KeyValueRow("GPU", d.gpu.ifBlank { "无" })
                if (d.bw.isNotBlank()) KeyValueRow("带宽", "${d.bw} Mbps")
                if (d.systemDisk.isNotBlank()) KeyValueRow("系统盘", "${d.systemDisk} GB")
                KeyValueRow(
                    "数据盘",
                    if (d.dataDiskCount.isNotBlank() && d.dataDiskCount != "0") {
                        "${d.dataDiskCount} 块 / ${d.dataDiskSize} GB"
                    } else "无"
                )
                KeyValueRow("IPv4 数量", if (d.ipNum.isNotBlank()) "${d.ipNum} 个" else "无")
                KeyValueRow("IPv6 数量", if (d.ipv6Num.isNotBlank() && d.ipv6Num != "0") "${d.ipv6Num} 个" else "无")
                if (d.imageName.isNotBlank()) KeyValueRow("操作系统", d.imageName)
                if (d.lineName.isNotBlank()) KeyValueRow("线路", d.lineName)
                if (d.securityGroup.isNotBlank()) KeyValueRow("安全组", d.securityGroup)
                if (d.baseInfo.isNotBlank()) KeyValueRow("基础配置", d.baseInfo)
            }
        }
    }
}

// ==================== Tab 2：连接信息 ====================

private fun androidx.compose.foundation.lazy.LazyListScope.connectTab(d: PurchasedDetail) {
    item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    Strings.HOST_CONNECT_TIP,
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub
                )
                Spacer(Modifier.height(2.dp))
                CopyRow("IP 地址", d.ip.ifBlank { "--" }, d.ip)
                CopyRow("机器名", d.name.ifBlank { "--" }, d.name)
                CopyRow("端口", d.port.ifBlank { "22" }, d.port.ifBlank { "22" })
                CopyRow("root 账户", d.username.ifBlank { "root" }, d.username.ifBlank { "root" })
                CopyRow("密码", d.password.ifBlank { "--" }, d.password, sensitive = true)
            }
        }
    }
    item {
        val ctx = LocalContext.current
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Strings.HOST_COPY_ALL, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                CartoonButton(
                    text = Strings.HOST_COPY_ALL,
                    icon = AppIcons.Category,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        copyText(
                            ctx,
                            HostOpsBodies.connectionText(d.ip, d.name, d.port, d.username, d.password)
                        )
                    }
                )
                val ssh = HostOpsBodies.sshCommand(d.ip, d.port, d.username)
                if (ssh.isNotBlank()) {
                    Text(Strings.HOST_SSH_CMD, style = MaterialTheme.typography.labelMedium, color = CartoonColors.InkSub)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CartoonKit.ItemShape)
                            .background(CartoonColors.BrandSoft)
                            .clickable { copyText(ctx, ssh) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ssh, style = MaterialTheme.typography.bodySmall, color = CartoonColors.Ink, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(Strings.PROMO_COPY, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CartoonColors.Brand)
                    }
                }
            }
        }
    }
}

/** 单行：标签 + 值（敏感项掩码）+ 复制按钮。 */
@Composable
private fun CopyRow(label: String, display: String, copyValue: String, sensitive: Boolean = false) {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf(!sensitive) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CartoonKit.ItemShape)
            .background(CartoonColors.White.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = CartoonColors.InkSub, modifier = Modifier.width(84.dp))
        Text(
            if (sensitive && !shown) "••••••••" else display,
            style = MaterialTheme.typography.bodySmall,
            color = CartoonColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (sensitive) {
            TextButton(onClick = { shown = !shown }, modifier = Modifier.height(32.dp)) {
                Text(if (shown) Strings.HOST_HIDE else Strings.HOST_SHOW, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (copyValue.isNotBlank()) {
            TextButton(onClick = { copyText(ctx, copyValue) }, modifier = Modifier.height(32.dp)) {
                Text(Strings.PROMO_COPY, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==================== Tab 3：重装系统 ====================

private fun androidx.compose.foundation.lazy.LazyListScope.reinstallTab(
    d: PurchasedDetail,
    hostId: String,
    state: ProductDetailUi,
    onReinstall: (Int, String, String) -> Unit,
    onPower: (String) -> Unit,
    onResetPassword: (String, String) -> Unit,
    onRefreshLogs: () -> Unit
) {
    if (!d.hasCloudConfig) {
        item {
            EmptyHint(icon = AppIcons.Info, text = Strings.HOST_NO_CLOUD_CONFIG, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    item {
        ReinstallFormCard(
            hostId = hostId,
            images = state.images,
            loading = state.imagesLoading,
            busy = state.opBusy,
            defaultPort = d.port.ifBlank { "22" },
            defaultPassword = d.password,
            onReinstall = onReinstall
        )
    }
    item { SectionTitle(Strings.HOST_POWER_TITLE, CartoonColors.Lilac) }
    item {
        PowerCard(busy = state.opBusy, onPower = onPower)
    }
    item {
        ResetPasswordCard(
            busy = state.opBusy,
            defaultPort = d.port.ifBlank { "22" },
            onReset = onResetPassword
        )
    }
    item { SectionTitle(Strings.HOST_OP_LOG, CartoonColors.Sky) }
    item {
        OpLogCard(logs = state.logs, onRefresh = onRefreshLogs)
    }
}

@Composable
private fun ReinstallFormCard(
    hostId: String,
    images: List<MfImageGroup>,
    loading: Boolean,
    busy: Boolean,
    defaultPort: String,
    defaultPassword: String,
    onReinstall: (Int, String, String) -> Unit
) {
    var selectedImageId by remember { mutableStateOf<Int?>(null) }
    var password by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(defaultPort) }
    var showConfirm by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Strings.HOST_REINSTALL_WARN, style = MaterialTheme.typography.labelSmall, color = CartoonColors.Coral)
            Text(Strings.HOST_SELECT_IMAGE, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
            if (loading) {
                Text("正在加载镜像…", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            } else if (images.isEmpty()) {
                Text("未获取到可选镜像，请下拉刷新重试", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            } else {
                images.forEach { group ->
                    Text(group.name.ifBlank { "系统" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Brand)
                    group.images.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            pair.forEach { img ->
                                val on = selectedImageId == img.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(CartoonKit.ItemShape)
                                        .background(if (on) CartoonColors.BrandSoft else CartoonColors.White.copy(alpha = 0.4f))
                                        .clickable { selectedImageId = img.id }
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        img.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                        color = if (on) CartoonColors.Brand else CartoonColors.Ink,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(Strings.HOST_NEW_PASSWORD) },
                placeholder = { Text(Strings.HOST_NEW_PASSWORD_HINT) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = glassFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text(Strings.HOST_PORT) },
                singleLine = true,
                colors = glassFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            CartoonButton(
                text = if (busy) Strings.HOST_REINSTALLING else Strings.HOST_REINSTALL_CONFIRM,
                icon = AppIcons.Update,
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedImageId != null && !busy && !loading,
                onClick = { showConfirm = true }
            )
        }
    }
    if (showConfirm) {
        val imageName = images.flatMap { it.images }.firstOrNull { it.id == selectedImageId }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(Strings.HOST_REINSTALL_CONFIRM) },
            text = { Text("确定重装为「$imageName」吗？${Strings.HOST_REINSTALL_WARN}") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    selectedImageId?.let { onReinstall(it, password, port) }
                }) { Text(Strings.HOST_REINSTALL_CONFIRM) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun PowerCard(busy: Boolean, onPower: (String) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val actions = listOf(
                Strings.HOST_POWER_ON to "on",
                Strings.HOST_POWER_REBOOT to "reboot",
                Strings.HOST_POWER_OFF to "off",
                Strings.HOST_POWER_HARD_REBOOT to "hard_reboot",
                Strings.HOST_POWER_HARD_OFF to "hard_off"
            )
            actions.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { (label, action) ->
                        CartoonButton(
                            text = label,
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            onClick = { onPower(action) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetPasswordCard(busy: Boolean, defaultPort: String, onReset: (String, String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(defaultPort) }
    var show by remember { mutableStateOf(false) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Strings.HOST_RESET_PASSWORD, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(Strings.HOST_NEW_PASSWORD) },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { show = !show }) {
                        Text(if (show) Strings.HOST_HIDE else Strings.HOST_SHOW, style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = glassFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text(Strings.HOST_PORT) },
                singleLine = true,
                colors = glassFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            CartoonButton(
                text = Strings.HOST_RESET_PASSWORD,
                modifier = Modifier.fillMaxWidth(),
                enabled = password.isNotBlank() && !busy,
                onClick = { onReset(password, port) }
            )
        }
    }
}

@Composable
private fun OpLogCard(logs: List<HostOpLog>, onRefresh: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (logs.isEmpty()) {
                Text(Strings.HOST_OP_LOG_EMPTY, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            } else {
                logs.take(20).forEach { log ->
                    Column {
                        Text(log.text, style = MaterialTheme.typography.bodySmall, color = CartoonColors.Ink)
                        if (log.time.isNotBlank()) {
                            Text(log.time, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                        }
                    }
                }
            }
            TextButton(onClick = onRefresh) { Text(Strings.REFRESH) }
        }
    }
}

// ==================== Tab 4：费用与订单 ====================

private fun androidx.compose.foundation.lazy.LazyListScope.billingTab(
    d: PurchasedDetail,
    onOpenOrder: (String) -> Unit
) {
    item { SectionTitle("支付信息", CartoonColors.Lilac) }
    item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                KeyValueRow("付款类型", d.billingCycleName.ifBlank { payTypeText(d.payType) })
                KeyValueRow("首次订购金额", money(d.firstPaymentAmount))
                KeyValueRow("下次续费金额", money(d.renewAmount))
                KeyValueRow("开通时间", d.activeTime.ifBlank { "--" })
                KeyValueRow("下次续费时间", d.dueTime.ifBlank { "--" })
                KeyValueRow("自动续费", if (d.autoRenew) "已开启" else "未开启")
                KeyValueRow("优惠码", "--")
            }
        }
    }
    if (d.orderId.isNotBlank()) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CartoonKit.ItemShape)
                        .clickable { onOpenOrder(d.orderId) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(AppIcons.Order, null, tint = CartoonColors.Lilac, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "关联订单 #${d.orderId}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = CartoonColors.Ink
                        )
                        Text("点击查看订单详情 →", style = MaterialTheme.typography.labelSmall, color = CartoonColors.Brand)
                    }
                    Icon(AppIcons.Forward, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ==================== 通用小组件 ====================

/** 电源状态徽章：on=运行中，off=已关机。 */
@Composable
private fun powerBadge(status: String) {
    val on = status.equals("on", true)
    val (bg, fg) = if (on) {
        CartoonColors.MintSoft to CartoonColors.Mint
    } else {
        CartoonColors.PeachSoft to CartoonColors.Coral
    }
    Text(
        if (status.isBlank()) "电源状态未知" else if (on) "运行中" else "已关机",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .clip(CartoonKit.PillShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

private fun copyText(ctx: android.content.Context, text: String) {
    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("QingVPS", text))
    Toast.makeText(ctx, Strings.HOST_COPIED, Toast.LENGTH_SHORT).show()
}

private fun money(v: String): String = if (v.isBlank()) "--" else "￥$v"

private fun payTypeText(type: String): String = when (type) {
    "recurring_prepayment" -> "预付款循环"
    "free" -> "免费"
    "on_demand" -> "按需计费"
    "once" -> "一次性"
    else -> type.ifBlank { "--" }
}
