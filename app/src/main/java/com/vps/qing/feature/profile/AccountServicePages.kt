package com.vps.qing.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.EmptyHint
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.data.repository.AuthNode
import com.vps.qing.data.repository.SubAccountDetail
import com.vps.qing.data.repository.SubAccountForm
import com.vps.qing.data.repository.TicketDetail
import com.vps.qing.data.repository.TicketLine
import com.vps.qing.data.repository.TicketOption
import com.vps.qing.data.repository.TicketReply
import com.vps.qing.data.repository.TicketStatistic

/**
 * 账号与服务 v1.4.3：工单中心与子账户管理的完整原生页面。
 *
 * 依据站点插件 JS 挖出的全部端点与字段：
 *  - 工单：统计 / 类型 / 状态字典 / 部门 / 通知配置 / 列表 / 详情+回复时间轴 /
 *    新建 / 回复 / 关闭 / 催单；
 *  - 子账户：列表 / 启停 / 删除 / 详情 / 新建 / 编辑（权限树、模块、关联产品、区号）。
 *
 * 所有写操作均由 [ProfileViewModel] 统一处理并回传一次性反馈，页面只负责展示与收集输入。
 */

// ---------------------------------------------------------------- 通用小组件

/** 状态色徽章：优先用站点返回的 hex 色（工单 / 子账户状态），无则回退主题色。 */
@Composable
private fun HexStatusChip(text: String, hex: String, fallback: Color) {
    val base = remember(hex) {
        val clean = hex.removePrefix("#")
        if (clean.length == 6 || clean.length == 8) {
            try {
                Color(java.lang.Long.parseUnsignedLong(if (clean.length == 6) "FF$clean" else clean, 16))
            } catch (e: Exception) {
                null
            }
        } else null
    } ?: fallback
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = base,
        modifier = Modifier
            .clip(CartoonKit.PillShape)
            .background(base.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = CartoonColors.InkSub,
            modifier = Modifier.width(64.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = CartoonColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 可复用的表单输入（玻璃描边风格）。 */
@Composable
private fun FormField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
        Spacer(Modifier.height(5.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall, color = CartoonColors.InkSub) },
            singleLine = singleLine,
            minLines = minLines,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

/** 单选胶囊组（状态筛选 / 表单选项通用）。 */
@Composable
private fun ChipGroup(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (id, label) ->
            val active = selected == id
            Box(
                Modifier
                    .clip(CartoonKit.PillShape)
                    .background(if (active) CartoonColors.Brand else CartoonColors.White.copy(alpha = 0.7f))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color.White else CartoonColors.Ink
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 工单列表

/** 工单中心列表：统计条 + 状态筛选 + 列表 + 空态。 */
@Composable
fun TicketsListBody(
    data: ServicePanelData,
    filterStatusId: String?,
    onFilter: (String?) -> Unit,
    onOpenTicket: (String) -> Unit,
    onCreateTicket: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statuses = data.ticketStatuses
    val statistic = data.ticketStatistic
    val shown = if (filterStatusId == null) data.tickets
    else data.tickets.filter { it.statusId == filterStatusId }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 统计条
        if (statistic != null) {
            item { TicketStatisticBar(statistic, statuses) }
        }
        // 状态筛选
        if (statuses.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("按状态筛选", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                    ChipGroup(
                        options = listOf(null to "全部 ${data.tickets.size}") +
                            statuses.map { it.id to it.name },
                        selected = filterStatusId,
                        onSelect = onFilter
                    )
                }
            }
        }
        // 通知配置提示
        data.ticketConfig?.let { cfg ->
            if (cfg.noticeOpen && cfg.noticeDescription.isNotBlank()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Bell, null, tint = CartoonColors.Brand, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(cfg.noticeDescription, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                        }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyHint(
                    icon = AppIcons.Ticket,
                    text = if (filterStatusId != null) "该状态下暂无工单" else "还没有工单，点右上角 + 提交第一个",
                    action = { CartoonButton(text = "新建工单", onClick = onCreateTicket, icon = AppIcons.Plus) }
                )
            }
        } else {
            items(shown, key = { "ticket-${it.id}" }) { ticket ->
                TicketCard(ticket, onOpenTicket)
            }
        }
    }
}

@Composable
private fun TicketStatisticBar(statistic: TicketStatistic, statuses: List<TicketOption>) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = CartoonKit.CardShape,
        alpha = 0.6f,
        elevation = 0.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Ticket, null, tint = CartoonColors.Brand, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("工单概览", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                Spacer(Modifier.weight(1f))
                Text("共 ${statistic.total} 单", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val itemsToShow = if (statuses.isNotEmpty()) statuses.take(5)
                else statistic.counts.entries.map { TicketOption(it.key, it.key) }
                itemsToShow.forEach { opt ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CartoonColors.White.copy(alpha = 0.6f))
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            statistic.countOf(opt.id).toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = CartoonColors.Brand
                        )
                        Text(
                            opt.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: TicketLine, onOpen: (String) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(ticket.id) }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${ticket.displayNum}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Brand
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    ticket.title.ifBlank { "（无标题）" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                HexStatusChip(
                    ticket.status.ifBlank { "--" },
                    ticket.color,
                    if (ticket.isClosed) CartoonColors.InkSub else CartoonColors.Mint
                )
            }
            if (ticket.typeName.isNotBlank()) {
                InfoLine("类型", ticket.typeName)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("创建 ${ticket.createTime}", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                Spacer(Modifier.weight(1f))
                Text("最后回复 ${ticket.lastReply.ifBlank { "--" }}", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            }
            Text(
                "查看对话与回复 →",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Brand
            )
        }
    }
}

// ---------------------------------------------------------------- 工单详情

/** 工单详情整页：信息卡 + 回复时间轴 + 回复框 + 关闭/催单。 */
@Composable
fun TicketDetailPage(
    vm: ProfileViewModel,
    ticketId: String,
    onBack: () -> Unit
) {
    val state by vm.ui.collectAsState()
    val ui = state.ticketDetails[ticketId] ?: TicketDetailUi()
    LaunchedEffect(ticketId) { vm.ensureTicketDetail(ticketId) }
    var replyText by remember { mutableStateOf("") }

    CartoonBackground {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "工单 #${ui.detail?.displayNum ?: ticketId}",
                subtitle = "查看对话与回复",
                navIcon = AppIcons.Back,
                onNavClick = onBack,
                actions = {
                    CartoonIconChip(
                        icon = AppIcons.Refresh,
                        onClick = { vm.refreshTicketDetail(ticketId) },
                        tint = CartoonColors.Mint,
                        bg = CartoonColors.MintSoft
                    )
                }
            )
            when {
                ui.loading && ui.detail == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CartoonColors.Brand)
                    }
                }
                ui.error != null && ui.detail == null -> {
                    EmptyHint(
                        icon = AppIcons.EmptyBox,
                        text = ui.error.orEmpty(),
                        modifier = Modifier.fillMaxSize(),
                        action = { CartoonButton(text = Strings.RETRY, onClick = { vm.refreshTicketDetail(ticketId) }) }
                    )
                }
                ui.detail != null -> {
                    val d = ui.detail!!
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item { TicketDetailHeader(d) }
                            if (d.replies.isEmpty()) {
                                item {
                                    EmptyHint(icon = AppIcons.Message, text = "暂无回复，来发第一条吧")
                                }
                            } else {
                                items(d.replies, key = { "reply-${it.id}" }) { reply ->
                                    ReplyBubble(reply)
                                }
                            }
                        }
                        // 底部操作区：回复输入 + 关闭 / 催单
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                            alpha = 0.92f,
                            elevation = 12.dp
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!d.isClosed && d.canOperate) {
                                    OutlinedTextField(
                                        value = replyText,
                                        onValueChange = { replyText = it },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 130.dp),
                                        placeholder = { Text("输入回复内容…", style = MaterialTheme.typography.bodySmall) },
                                        shape = RoundedCornerShape(16.dp),
                                        maxLines = 5
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CartoonButton(
                                            text = if (state.actionBusy) "发送中…" else "发送回复",
                                            onClick = {
                                                vm.replyTicket(ticketId, replyText)
                                                replyText = ""
                                            },
                                            enabled = !state.actionBusy && replyText.isNotBlank(),
                                            icon = AppIcons.Forward,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { vm.urgeTicket(ticketId) },
                                            enabled = !state.actionBusy,
                                            modifier = Modifier.weight(1f)
                                        ) { Text("催单", color = CartoonColors.Brand) }
                                        TextButton(
                                            onClick = { vm.closeTicket(ticketId) },
                                            enabled = !state.actionBusy,
                                            modifier = Modifier.weight(1f)
                                        ) { Text("关闭工单", color = CartoonColors.InkSub) }
                                    }
                                } else {
                                    Text(
                                        if (d.isClosed) "该工单已关闭" else "当前不可回复",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CartoonColors.InkSub
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketDetailHeader(d: TicketDetail) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.title.ifBlank { "（无标题）" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = CartoonColors.Ink,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                HexStatusChip(d.status.ifBlank { "--" }, d.color, CartoonColors.Brand)
            }
            InfoLine("工单号", "#${d.displayNum}")
            InfoLine("创建时间", d.createTime)
            if (d.hostIds.isNotEmpty()) InfoLine("关联产品", d.hostIds.joinToString("、"))
            InfoLine("回复数", d.replies.size.toString())
        }
    }
}

@Composable
private fun ReplyBubble(reply: TicketReply) {
    val isClient = !reply.isAdmin
    val bubbleBg = if (isClient) CartoonColors.BrandSoft else CartoonColors.White.copy(alpha = 0.85f)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isClient) Alignment.End else Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                reply.authorName.ifBlank { if (isClient) "我" else "客服" },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isClient) CartoonColors.Brand else CartoonColors.Mint
            )
            Spacer(Modifier.width(8.dp))
            Text(reply.createTime, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleBg)
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reply.quote?.let { q ->
                    if (!q.isDeleted) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CartoonColors.InkSub.copy(alpha = 0.08f))
                                .padding(8.dp)
                        ) {
                            Text("引用 ${q.senderName}（${q.createTime}）", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                            Text(q.content, style = MaterialTheme.typography.labelSmall, color = CartoonColors.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(reply.content.ifBlank { "（无内容）" }, style = MaterialTheme.typography.bodySmall, color = CartoonColors.Ink)
                if (reply.attachments.isNotEmpty()) {
                    Text("附件：${reply.attachments.size} 个", style = MaterialTheme.typography.labelSmall, color = CartoonColors.Brand)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 新建工单

/** 新建工单整页：标题 / 类型 / 关联产品 / 内容。 */
@Composable
fun TicketCreatePage(
    vm: ProfileViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val state by vm.ui.collectAsState()
    val panel = state.panels[ServicePanel.TICKETS]
    val types = panel?.ticketTypes.orEmpty()
    val hosts = state.subFormMeta.hosts
    LaunchedEffect(Unit) { vm.ensureFormMeta() }

    var title by remember { mutableStateOf("") }
    var typeId by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedHosts by remember { mutableStateOf<List<String>>(emptyList()) }
    var localError by remember { mutableStateOf<String?>(null) }

    // 类型默认选第一项
    LaunchedEffect(types) {
        if (typeId.isBlank() && types.isNotEmpty()) typeId = types.first().id
    }
    // 提交成功后返回列表
    LaunchedEffect(state.actionMessage) {
        if (state.actionMessage != null) onCreated()
    }

    CartoonBackground {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "新建工单",
                subtitle = "描述问题，我们会尽快回复",
                navIcon = AppIcons.Back,
                onNavClick = onBack
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormField("标题", title, { title = it }, placeholder = "简要描述问题")
                Column(Modifier.fillMaxWidth()) {
                    Text("工单类型", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                    Spacer(Modifier.height(6.dp))
                    if (types.isEmpty()) {
                        Text("类型加载中…", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                    } else {
                        ChipGroup(
                            options = types.map { it.id to it.name },
                            selected = typeId,
                            onSelect = { typeId = it ?: "" }
                        )
                    }
                }
                if (hosts.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("关联产品（可选）", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                        Spacer(Modifier.height(6.dp))
                        hosts.forEach { h ->
                            val checked = h.id in selectedHosts
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (checked) CartoonColors.BrandSoft else Color.Transparent)
                                    .clickable {
                                        selectedHosts = if (checked) selectedHosts - h.id else selectedHosts + h.id
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (checked) AppIcons.Check else AppIcons.Server,
                                    null,
                                    tint = if (checked) CartoonColors.Brand else CartoonColors.InkSub,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(h.label, style = MaterialTheme.typography.bodySmall, color = CartoonColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                FormField("问题内容", content, { content = it }, placeholder = "尽量详细地描述遇到的问题", singleLine = false, minLines = 5)
                localError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                CartoonButton(
                    text = if (state.actionBusy) "提交中…" else "提交工单",
                    onClick = {
                        when {
                            title.isBlank() -> localError = "请填写标题"
                            typeId.isBlank() -> localError = "请选择工单类型"
                            content.isBlank() -> localError = "请填写问题内容"
                            else -> {
                                localError = null
                                vm.submitTicket(title, typeId, content, selectedHosts)
                            }
                        }
                    },
                    enabled = !state.actionBusy,
                    icon = AppIcons.Forward,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- 子账户列表

/** 子账户管理列表：状态徽章 + 启停 / 编辑 / 删除 + 新建。 */
@Composable
fun SubAccountsListBody(
    data: ServicePanelData,
    busy: Boolean,
    onOpenSubAccount: (String) -> Unit,
    onCreateSubAccount: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (data.subAccounts.isEmpty()) {
            item {
                EmptyHint(
                    icon = AppIcons.Users,
                    text = "还没有子账户，点右上角 + 创建一个",
                    action = { CartoonButton(text = "新建子账户", onClick = onCreateSubAccount, icon = AppIcons.Plus) }
                )
            }
        } else {
            items(data.subAccounts, key = { "sub-${it.id}" }) { sub ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(34.dp).clip(CartoonKit.ItemShape).background(CartoonColors.SkySoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(AppIcons.Users, null, tint = CartoonColors.Sky, modifier = Modifier.size(19.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sub.username.ifBlank { "#${sub.id}" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                                Text(
                                    listOf(sub.email, sub.phone).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未填写联系方式" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CartoonColors.InkSub,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            HexStatusChip(
                                if (sub.enabled) "已启用" else "已停用",
                                "",
                                if (sub.enabled) CartoonColors.Mint else CartoonColors.InkSub
                            )
                        }
                        if (sub.lastActionTime.isNotBlank() && sub.lastActionTime != "--") {
                            InfoLine("最后活跃", sub.lastActionTime)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { onOpenSubAccount(sub.id) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) { Text("编辑", color = CartoonColors.Brand) }
                            TextButton(
                                onClick = { onToggle(sub.id, !sub.enabled) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) { Text(if (sub.enabled) "停用" else "启用", color = CartoonColors.Ink) }
                            TextButton(
                                onClick = { pendingDelete = sub.id },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) { Text("删除", color = CartoonColors.Coral) }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { id ->
        Dialog(onDismissRequest = { pendingDelete = null }) {
            GlassSurface(modifier = Modifier.fillMaxWidth(), shape = CartoonKit.SheetShape, alpha = 1f, elevation = 20.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(CartoonColors.White.copy(alpha = 0.97f))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("删除子账户", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = CartoonColors.Ink)
                    Text("确定要删除这个子账户吗？该操作不可撤销。", style = MaterialTheme.typography.bodyMedium, color = CartoonColors.InkSub)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { pendingDelete = null }) { Text(Strings.CANCEL, color = CartoonColors.InkSub) }
                        TextButton(onClick = {
                            onDelete(id)
                            pendingDelete = null
                        }) { Text("删除", fontWeight = FontWeight.Bold, color = CartoonColors.Coral) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 子账户表单（新建 / 编辑）

/** 子账户新建 / 编辑整页。editId 为 null 表示新建。 */
@Composable
fun SubAccountFormPage(
    vm: ProfileViewModel,
    editId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by vm.ui.collectAsState()
    val meta = state.subFormMeta
    val detailState = editId?.let { state.subAccountDetails[it] }
    LaunchedEffect(editId) {
        vm.ensureFormMeta()
        editId?.let { vm.ensureSubAccountDetail(it) }
    }
    LaunchedEffect(state.actionMessage) {
        if (state.actionMessage != null) onSaved()
    }

    var form by remember { mutableStateOf(SubAccountForm()) }
    var initialized by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    // 编辑态用详情回填（只回填一次）
    LaunchedEffect(detailState?.detail) {
        val d = detailState?.detail
        if (!initialized && d != null) {
            form = form.copy(
                username = d.username,
                email = d.email,
                phoneCode = d.phoneCode.ifBlank { "86" },
                phone = d.phone,
                visibleProduct = d.visibleProduct,
                projectIds = d.projectIds,
                hostIds = d.hostIds,
                modules = d.modules,
                auth = d.auth
            )
            initialized = true
        }
    }

    CartoonBackground {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(
                title = if (editId == null) "新建子账户" else "编辑子账户",
                subtitle = "设置登录信息与可见权限",
                navIcon = AppIcons.Back,
                onNavClick = onBack
            )
            if (editId != null && detailState?.loading == true && detailState.detail == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CartoonColors.Brand)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormField("用户名", form.username, { form = form.copy(username = it) }, placeholder = "子账户登录名")
                FormField("邮箱", form.email, { form = form.copy(email = it) }, placeholder = "用于接收通知")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        "区号", form.phoneCode, { form = form.copy(phoneCode = it) },
                        modifier = Modifier.weight(0.35f), placeholder = "86"
                    )
                    FormField(
                        "手机号", form.phone, { form = form.copy(phone = it) },
                        modifier = Modifier.weight(0.65f), placeholder = "可选"
                    )
                }
                FormField(
                    if (editId == null) "登录密码" else "重设密码（留空不改）",
                    form.password, { form = form.copy(password = it) },
                    placeholder = if (editId == null) "设置初始密码" else "不修改请留空"
                )

                // 可见产品范围
                Column(Modifier.fillMaxWidth()) {
                    Text("可见产品范围", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                    Spacer(Modifier.height(6.dp))
                    ChipGroup(
                        options = listOf("module" to "按模块", "host" to "按具体产品"),
                        selected = form.visibleProduct,
                        onSelect = { form = form.copy(visibleProduct = it ?: "module") }
                    )
                }
                if (form.visibleProduct == "module" && meta.modules.isNotEmpty()) {
                    MultiCheckCard("可见模块", meta.modules.map { it.name to (it.displayName.ifBlank { it.name }) }, form.modules) { next ->
                        form = form.copy(modules = next)
                    }
                }
                if (form.visibleProduct == "host" && meta.hosts.isNotEmpty()) {
                    MultiCheckCard("可见产品", meta.hosts.map { it.id to it.label }, form.hostIds) { next ->
                        form = form.copy(hostIds = next)
                    }
                }

                // 权限树
                if (meta.auth.isNotEmpty()) {
                    AuthTreeCard(meta.auth, form.auth) { next -> form = form.copy(auth = next) }
                }

                localError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                CartoonButton(
                    text = if (state.actionBusy) "保存中…" else if (editId == null) "创建子账户" else "保存修改",
                    onClick = {
                        val err = form.validate(editId != null)
                        if (err != null) localError = err
                        else {
                            localError = null
                            vm.saveSubAccount(form, editId)
                        }
                    },
                    enabled = !state.actionBusy,
                    icon = AppIcons.Check,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 通用多选卡片（模块 / 具体产品）。 */
@Composable
private fun MultiCheckCard(
    title: String,
    options: List<Pair<String, String>>,
    selected: List<String>,
    onChange: (List<String>) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
            options.forEach { (id, label) ->
                val checked = id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (checked) CartoonColors.BrandSoft else Color.Transparent)
                        .clickable { onChange(if (checked) selected - id else selected + id) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (checked) AppIcons.Check else AppIcons.Server,
                        null,
                        tint = if (checked) CartoonColors.Brand else CartoonColors.InkSub,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, color = CartoonColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 权限树卡片：展示一级分组 + 二级条目，勾选叶子并把父 id 一并回传。 */
@Composable
private fun AuthTreeCard(
    nodes: List<AuthNode>,
    selected: List<String>,
    onChange: (List<String>) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("操作权限", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
            Text("勾选允许子账户使用的功能", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            nodes.forEach { root ->
                val leaves = root.flatten().filter { it.isLeaf }
                val allChecked = leaves.isNotEmpty() && leaves.all { it.id in selected }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (allChecked) CartoonColors.MintSoft else Color.Transparent)
                        .clickable {
                            val next = selected.toMutableList()
                            if (allChecked) {
                                next.remove(root.id)
                                leaves.forEach { next.remove(it.id) }
                            } else {
                                if (root.id !in next) next.add(root.id)
                                leaves.forEach { if (it.id !in next) next.add(it.id) }
                            }
                            onChange(next)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (allChecked) AppIcons.Check else AppIcons.Lock,
                        null,
                        tint = if (allChecked) CartoonColors.Mint else CartoonColors.InkSub,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(root.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
                    Spacer(Modifier.weight(1f))
                    Text("${leaves.size} 项", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                }
            }
        }
    }
}
