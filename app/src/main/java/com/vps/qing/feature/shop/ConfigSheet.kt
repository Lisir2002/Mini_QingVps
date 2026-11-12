package com.vps.qing.feature.shop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vps.qing.core.AppContainer
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.QuantityStepper
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.CartCalculator
import com.vps.qing.data.repository.ConfigOption
import com.vps.qing.data.repository.CycleOption
import com.vps.qing.data.repository.MfCloudConfig
import com.vps.qing.data.repository.MfSelection
import com.vps.qing.data.repository.OptionType
import com.vps.qing.data.repository.ProductConfig

/**
 * 加购前的自定义配置弹窗（磨砂玻璃）。
 * 选择型配置项用玻璃下拉框，数量型用步进器，周期用玻璃胶囊，
 * 实时展示站点算价，确认后真实 POST /cart。
 * 套餐型商品（mf_cloud）无通用配置，降级为「前往网页选购」。
 */
@Composable
fun ConfigSheet(
    sheet: ConfigSheetState,
    onSelectOption: (String, String) -> Unit,
    onSelectCycle: (String) -> Unit,
    onQty: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOpenWeb: (String) -> Unit,
    /** mf_cloud 专属：整体更新配置选择 / 单独改镜像。 */
    onMfSelect: (com.vps.qing.data.repository.MfSelection) -> Unit = {},
    onSelectImage: (Int) -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 620.dp),
            shape = CartoonKit.SheetShape,
            alpha = 0.92f,
            elevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 标题栏
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CartoonKit.CardShape)
                            .background(CartoonColors.BrandSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Sliders, null, tint = CartoonColors.Brand, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            Strings.CONFIG_TITLE,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = CartoonColors.Ink
                        )
                        Text(
                            Strings.CONFIG_SUBTITLE,
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                    }
                    CartoonIconChip(
                        icon = AppIcons.Close,
                        onClick = onDismiss,
                        tint = CartoonColors.InkSub,
                        bg = CartoonColors.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 商品名
                Text(
                    sheet.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink
                )

                Spacer(Modifier.height(12.dp))

                when {
                    sheet.loading -> {
                        Box(
                            Modifier.fillMaxWidth().height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = CartoonColors.Brand)
                                Spacer(Modifier.height(10.dp))
                                Text(Strings.CONFIG_LOADING, style = MaterialTheme.typography.bodySmall, color = CartoonColors.InkSub)
                            }
                        }
                    }
                    sheet.config?.requiresWebOrder == true -> {
                        WebOrderFallback(sheet.product.id, onOpenWeb)
                    }
                    sheet.config?.mf != null -> {
                        MfConfigBody(sheet, onSelectCycle, onQty, onConfirm, onMfSelect, onSelectImage)
                    }
                    else -> {
                        ConfigBody(sheet, onSelectOption, onSelectCycle, onQty, onConfirm)
                    }
                }
            }
        }
    }
}

/**
 * mf_cloud（大带宽等套餐型）配置面板：
 * 区域线路 / CPU / 内存 / 系统盘 / 数据盘 / 带宽 / 操作系统 / 周期 / 数量，全部实时算价。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MfConfigBody(
    sheet: ConfigSheetState,
    onSelectCycle: (String) -> Unit,
    onQty: (Int) -> Unit,
    onConfirm: () -> Unit,
    onMfSelect: (com.vps.qing.data.repository.MfSelection) -> Unit,
    onSelectImage: (Int) -> Unit
) {
    val cfg = sheet.config ?: return
    val mf = cfg.mf ?: return
    val sel = sheet.mf ?: return
    val area = mf.areas.firstOrNull { it.areaId == sel.areaId }
    val bwCandidates = area?.bw?.candidates().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 430.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 区域 / 线路
        if (mf.areas.size > 1) {
            ChipRow(
                label = Strings.MF_AREA,
                options = mf.areas.map { it.areaId.toString() to it.label },
                selectedId = sel.areaId.toString(),
                onSelect = { id ->
                    val target = mf.areas.firstOrNull { it.areaId.toString() == id } ?: return@ChipRow
                    onMfSelect(
                        sel.copy(
                            areaId = target.areaId,
                            lineId = target.lineId,
                            bw = target.bw?.candidates()?.firstOrNull() ?: sel.bw
                        )
                    )
                }
            )
        } else {
            FieldLabel(Strings.MF_AREA)
            ChipFlowRow(
                options = mf.areas.map { it.areaId.toString() to it.label },
                selectedId = sel.areaId.toString(),
                enabled = false,
                onSelect = { }
            )
        }

        if (mf.cpus.isNotEmpty()) {
            ChipRow(
                label = Strings.MF_CPU,
                options = mf.cpus.map { it.toString() to "${it}核" },
                selectedId = sel.cpu.toString(),
                onSelect = { id -> onMfSelect(sel.copy(cpu = id.toInt())) }
            )
        }

        if (mf.memories.isNotEmpty()) {
            ChipRow(
                label = Strings.MF_MEMORY,
                options = mf.memories.map { it.toString() to "${it}GB" },
                selectedId = sel.memory.toString(),
                onSelect = { id -> onMfSelect(sel.copy(memory = id.toInt())) }
            )
        }

        if (mf.systemDisks.isNotEmpty()) {
            ChipRow(
                label = Strings.MF_SYSTEM_DISK,
                options = mf.systemDisks.map { it.toString() to "${it}GB" },
                selectedId = sel.systemDisk.toString(),
                onSelect = { id -> onMfSelect(sel.copy(systemDisk = id.toInt())) }
            )
        }

        if (mf.dataDisks.isNotEmpty()) {
            ChipRow(
                label = Strings.MF_DATA_DISK,
                options = listOf("0" to "不增加") + mf.dataDisks.map { it.toString() to "${it}GB" },
                selectedId = sel.dataDisk.toString(),
                onSelect = { id -> onMfSelect(sel.copy(dataDisk = id.toInt())) }
            )
        }

        if (bwCandidates.isNotEmpty()) {
            ChipRow(
                label = Strings.MF_BANDWIDTH,
                options = bwCandidates.map { it.toString() to "${it}Mbps" },
                selectedId = sel.bw.toString(),
                onSelect = { id -> onMfSelect(sel.copy(bw = id.toInt())) }
            )
        }

        // 操作系统：分组下拉 + 镜像胶囊
        MfImagePicker(mf, sel, onSelectImage)

        // 周期
        if (cfg.cycles.isNotEmpty()) {
            CycleChips(cfg.cycles, sheet.cycleId, onSelectCycle)
        }

        // 数量
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(Strings.CONFIG_QTY, modifier = Modifier.weight(1f))
            QuantityStepper(sheet.qty, onQty)
        }

        MfPriceBar(sheet, cfg)
        sheet.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        CartoonButton(
            text = if (sheet.adding) Strings.CONFIG_ADDING else Strings.CONFIG_ADD,
            onClick = onConfirm,
            enabled = !sheet.adding,
            icon = AppIcons.Cart,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 带标题的胶囊选择行（单行放得下时居中排布，放不下自动换行）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        ChipFlowRow(options, selectedId, enabled = true, onSelect = onSelect)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlowRow(
    options: List<Pair<String, String>>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val selected = value == selectedId
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            selected -> CartoonColors.Brand
                            !enabled -> CartoonColors.White.copy(alpha = 0.45f)
                            else -> CartoonColors.White.copy(alpha = 0.7f)
                        }
                    )
                    .clickable(enabled = enabled) { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    color = when {
                        selected -> Color.White
                        else -> CartoonColors.Ink
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 操作系统两级选择：系统分组玻璃下拉 + 当前分组的镜像胶囊。 */
@Composable
private fun MfImagePicker(
    mf: MfCloudConfig,
    sel: MfSelection,
    onSelectImage: (Int) -> Unit
) {
    if (mf.imageGroups.isEmpty()) return
    val initialGroup = mf.imageGroups.firstOrNull { g -> g.images.any { it.id == sel.imageId } }
        ?: mf.imageGroups.first()
    var displayedGroupId by remember { mutableStateOf(initialGroup.id) }
    val group = mf.imageGroups.firstOrNull { it.id == displayedGroupId } ?: initialGroup

    Column(Modifier.fillMaxWidth()) {
        FieldLabel(Strings.MF_IMAGE)
        Spacer(Modifier.height(6.dp))
        GlassDropdown(
            items = mf.imageGroups.map { it.id.toString() to "${it.name}（${it.images.size}）" },
            selectedId = displayedGroupId.toString(),
            onSelect = { id -> displayedGroupId = id.toIntOrNull() ?: displayedGroupId }
        )
        Spacer(Modifier.height(8.dp))
        ChipFlowRow(
            options = group.images.map { it.id.toString() to it.name },
            selectedId = sel.imageId.toString(),
            enabled = true,
            onSelect = { id -> onSelectImage(id.toInt()) }
        )
    }
}

/** 周期胶囊行（通用 / mf_cloud 共用样式）。 */
@Composable
private fun CycleChips(cycles: List<CycleOption>, selectedId: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(Strings.CONFIG_CYCLE)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cycles.forEach { cycle ->
                val selected = selectedId == cycle.id
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) CartoonColors.Brand else CartoonColors.White.copy(alpha = 0.7f)
                        )
                        .clickable { onSelect(cycle.id) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            cycle.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else CartoonColors.Ink
                        )
                        if (cycle.amount > 0) {
                            Text(
                                CartCalculator.money(cycle.amount),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White.copy(alpha = 0.9f) else CartoonColors.InkSub
                            )
                        }
                    }
                }
            }
        }
    }
}

/** mf_cloud 预估价格条（站点 config_option 实时返回）。 */
@Composable
private fun MfPriceBar(sheet: ConfigSheetState, cfg: ProductConfig) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = CartoonKit.CardShape,
        alpha = 0.55f,
        elevation = 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Strings.CONFIG_ESTIMATE, style = MaterialTheme.typography.bodyMedium, color = CartoonColors.InkSub)
            Spacer(Modifier.weight(1f))
            when {
                sheet.quoting -> {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CartoonColors.Brand)
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.CONFIG_QUOTING, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                }
                else -> {
                    val price = sheet.quote?.price
                        ?: cfg.cycles.firstOrNull { it.id == sheet.cycleId }?.amount
                        ?: sheet.product.unitPrice
                    Text(
                        CartCalculator.money(price * sheet.qty),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = CartoonColors.Coral
                    )
                }
            }
        }
    }
}

@Composable
private fun WebOrderFallback(productId: String, onOpenWeb: (String) -> Unit) {    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(AppIcons.EmptyBox, null, tint = CartoonColors.Brand, modifier = Modifier.size(56.dp))
        Text(
            Strings.CONFIG_WEB_TIP,
            style = MaterialTheme.typography.bodyMedium,
            color = CartoonColors.InkSub,
            textAlign = TextAlign.Center
        )
        CartoonButton(
            text = Strings.WEB_ORDER,
            onClick = { onOpenWeb(AppContainer.webUrl("cart/goods.htm?id=$productId")) },
            icon = AppIcons.Forward,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ConfigBody(
    sheet: ConfigSheetState,
    onSelectOption: (String, String) -> Unit,
    onSelectCycle: (String) -> Unit,
    onQty: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val cfg = sheet.config ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 430.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 可自定义配置项
        cfg.options.filter { it.type != OptionType.CASCADE }.forEach { opt ->
            ConfigOptionRow(opt, sheet.selections[opt.id]) { value ->
                onSelectOption(opt.id, value)
            }
        }

        // 周期
        if (cfg.cycles.isNotEmpty()) {
            FieldLabel(Strings.CONFIG_CYCLE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cfg.cycles.forEach { cycle ->
                    val selected = sheet.cycleId == cycle.id
                    val accent = if (selected) CartoonColors.Brand else CartoonColors.InkSub
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) CartoonColors.Brand else CartoonColors.White.copy(alpha = 0.7f)
                            )
                            .clickable { onSelectCycle(cycle.id) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                cycle.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else CartoonColors.Ink
                            )
                            if (cycle.amount > 0) {
                                Text(
                                    CartCalculator.money(cycle.amount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) Color.White.copy(alpha = 0.9f) else CartoonColors.InkSub
                                )
                            }
                        }
                    }
                }
            }
        }

        // 数量
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(Strings.CONFIG_QTY, modifier = Modifier.weight(1f))
            QuantityStepper(sheet.qty, onQty)
        }

        // 预估价格
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = CartoonKit.CardShape,
            alpha = 0.55f,
            elevation = 0.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(Strings.CONFIG_ESTIMATE, style = MaterialTheme.typography.bodyMedium, color = CartoonColors.InkSub)
                Spacer(Modifier.weight(1f))
                when {
                    sheet.quoting -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CartoonColors.Brand)
                        Spacer(Modifier.width(8.dp))
                        Text(Strings.CONFIG_QUOTING, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                    }
                    else -> {
                        val price = sheet.quote?.price
                            ?: cfg.cycles.firstOrNull { it.id == sheet.cycleId }?.amount
                            ?: sheet.product.unitPrice
                        Text(
                            CartCalculator.money(price * sheet.qty),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = CartoonColors.Coral
                        )
                    }
                }
            }
        }

        sheet.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(4.dp))
        CartoonButton(
            text = if (sheet.adding) Strings.CONFIG_ADDING else Strings.CONFIG_ADD,
            onClick = onConfirm,
            enabled = !sheet.adding,
            icon = AppIcons.Cart,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = CartoonColors.Ink,
        modifier = modifier
    )
}

/** 单个配置项：选择型用玻璃下拉框，数量型用步进器，开关型用胶囊切换。 */
@Composable
private fun ConfigOptionRow(
    opt: ConfigOption,
    current: String?,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(opt.name)
        Spacer(Modifier.height(6.dp))
        when {
            OptionType.isChoice(opt.type) && opt.subs.isNotEmpty() -> {
                GlassDropdown(
                    items = opt.subs.map { it.id to listOfNotNull(it.group.takeIf { g -> g.isNotBlank() }, it.name)
                        .joinToString(" · ") },
                    selectedId = current,
                    onSelect = onSelect
                )
            }
            opt.type == OptionType.QUANTITY || opt.type == OptionType.QUANTITY_RANGE -> {
                val value = current?.toIntOrNull() ?: opt.qtyMin.takeIf { it > 0 } ?: 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuantityStepper(
                        value = value,
                        onDelta = { delta ->
                            val next = (value + delta).coerceIn(
                                opt.qtyMin.takeIf { it > 0 } ?: 1,
                                opt.qtyMax.takeIf { it > 0 } ?: 99
                            )
                            onSelect(next.toString())
                        }
                    )
                    if (opt.unit.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        Text(opt.unit, style = MaterialTheme.typography.bodySmall, color = CartoonColors.InkSub)
                    }
                }
            }
            opt.type == OptionType.YES_NO -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("0" to "否", "1" to "是").forEach { (v, label) ->
                        val selected = (current ?: "0") == v
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) CartoonColors.Brand else CartoonColors.White.copy(0.7f))
                                .clickable { onSelect(v) }
                                .padding(horizontal = 22.dp, vertical = 8.dp)
                        ) {
                            Text(
                                label,
                                color = if (selected) Color.White else CartoonColors.Ink,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            else -> {
                Text("—", style = MaterialTheme.typography.bodySmall, color = CartoonColors.InkSub)
            }
        }
    }
}

/**
 * 磨砂玻璃下拉框：点击展开候选项列表（半透明玻璃面板 + 描边），
 * 选中后收起。收起态为带 chevron 的玻璃条。
 */
@Composable
private fun GlassDropdown(
    items: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = items.firstOrNull { it.first == selectedId }?.second
        ?: items.firstOrNull()?.second.orEmpty()
    val height by animateDpAsState(targetValue = if (expanded) 200.dp else 0.dp)

    Column {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(16.dp),
            alpha = 0.8f,
            elevation = 2.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    AppIcons.ChevronDown, null,
                    tint = CartoonColors.Brand,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                shape = RoundedCornerShape(16.dp),
                alpha = 0.9f,
                elevation = 8.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp)
                ) {
                    items.forEach { (id, label) ->
                        val selected = id == selectedId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) CartoonColors.BrandSoft else Color.Transparent)
                                .clickable {
                                    onSelect(id)
                                    expanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) CartoonColors.Brand else CartoonColors.Ink,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(AppIcons.Check, null, tint = CartoonColors.Brand, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
