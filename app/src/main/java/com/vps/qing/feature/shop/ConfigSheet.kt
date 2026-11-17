package com.vps.qing.feature.shop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.TextButton
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
import com.vps.qing.core.AppContainer
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.ChipFlowRow
import com.vps.qing.core.ui.GlassDropdown
import com.vps.qing.core.ui.LoadingSkeleton
import com.vps.qing.core.ui.QuantityStepper
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.CartCalculator
import com.vps.qing.data.repository.CatalogParser
import com.vps.qing.data.repository.ConfigOption
import com.vps.qing.data.repository.CycleOption
import com.vps.qing.data.repository.MfCloudConfig
import com.vps.qing.data.repository.MfSelection
import com.vps.qing.data.repository.OptionType
import com.vps.qing.data.repository.PriceQuote
import com.vps.qing.data.repository.ProductConfig
import com.vps.qing.data.repository.ProductInfo
import com.vps.qing.core.ui.UiT

/**
 * 自定义配置独立页（v1.4.0，原为弹窗）。
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
    CartoonBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 独立页铺满 Scaffold 内容区，顶部标题栏带返回
        ) {
            GlassTopBar(
                title = sheet.product.name,
                subtitle = Strings.CONFIG_SUBTITLE,
                navIcon = AppIcons.Back,
                onNavClick = onDismiss
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // v1.4.20：选购辅助信息——购前须知（站点公告）/ 区域机器须知 / 可用优惠券。
                // 全部来自站点真实数据，App 只负责结构化展示，不硬编码任何业务文案。
                PurchaseInfoBlocks(sheet)
                when {
                    sheet.loading -> {
                        // v1.4.15：配置取数等待态统一为磨砂骨架，不再是居中的干转圈
                        LoadingSkeleton(
                            modifier = Modifier.fillMaxWidth(),
                            rows = 3,
                            message = Strings.CONFIG_LOADING
                        )
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
        modifier = Modifier.fillMaxWidth(),
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

        // 周期（显示随配置同步刷新的实时价格）
        if (cfg.cycles.isNotEmpty()) {
            CycleChips(cfg.cycles, sheet.cycleId, onSelectCycle, sheet.cycleQuotes)
        }

        // 数量
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(Strings.CONFIG_QTY, modifier = Modifier.weight(1f))
            QuantityStepper(sheet.qty, onQty)
        }

        PriceSummary(sheet)
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

/**
 * 周期胶囊行（通用 / mf_cloud 共用样式）。
 * v1.4.3：价格优先取该周期的实时算价（随选配同步刷新）；未取到时显示站点标价并标注「标价」，
 * 绝不把未校验的原价伪装成成交价。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycleChips(
    cycles: List<CycleOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    quotes: Map<String, PriceQuote>
) {
    Column(Modifier.fillMaxWidth()) {
        FieldLabel(Strings.CONFIG_CYCLE)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cycles.forEach { cycle ->
                val selected = selectedId == cycle.id
                val live = quotes[cycle.id]
                val amount = live?.total ?: cycle.amount
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) CartoonColors.Brand else CartoonColors.White.copy(alpha = 0.7f)
                        )
                        .clickable { onSelect(cycle.id) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            cycle.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else CartoonColors.Ink
                        )
                        if (amount > 0) {
                            Text(
                                CartCalculator.money(amount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else CartoonColors.Coral
                            )
                            Text(
                                if (live != null) "实时价 / 单份" else "站点标价",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White.copy(alpha = 0.85f) else CartoonColors.InkSub
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 应付总额 + 逐项价格明细（v1.4.3）。
 * 站点算价的 price 已经包含数量与折扣，这里直接展示，不再乘 qty；
 * 算价失败时显示明确提示而非静态原价，杜绝「价格错误」。
 */
@Composable
private fun PriceSummary(sheet: ConfigSheetState) {
    val quote = sheet.quote
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = CartoonKit.CardShape,
        alpha = 0.55f,
        elevation = 0.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Strings.CONFIG_ESTIMATE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CartoonColors.InkSub
                )
                Spacer(Modifier.weight(1f))
                when {
                    sheet.quoting -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CartoonColors.Brand)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Strings.CONFIG_QUOTING,
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                    }
                    quote == null -> Text(
                        "价格待确认",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CartoonColors.InkSub
                    )
                    else -> Column(horizontalAlignment = Alignment.End) {
                        if (quote.discounted) {
                            Text(
                                CartCalculator.money(quote.basePrice * sheet.qty),
                                style = MaterialTheme.typography.labelSmall,
                                color = CartoonColors.InkSub,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                            )
                        }
                        Text(
                            CartCalculator.money(quote.total),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = CartoonColors.Coral
                        )
                        Text(
                            // v1.4.15：站点标价兜底时如实标注来源，不冒充「实时算价」
                            if (quote.isEstimated) "站点标价 · 以结算价为准"
                            else "${sheet.qty} 份 · ${quote.billingCycle.ifBlank { "站点实时算价" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (quote.isEstimated) CartoonColors.Brand else CartoonColors.InkSub
                        )
                    }
                }
            }

            // v1.4.15：开通费 / 续费单价独立于 preview 明细展示（站点实测这些字段与
            // preview 并非必然同时存在），确保「续费单价」始终如实可见。
            if (quote != null && quote.setupFee > 0) {
                KeyValueLine("开通费", CartCalculator.money(quote.setupFee))
            }
            if (quote != null && quote.renewPrice > 0) {
                KeyValueLine(
                    if (quote.renewDiffers) "续费单价（不同于首期）" else "续费单价",
                    CartCalculator.money(quote.renewPrice)
                )
                if (quote.renewDiscounted) {
                    KeyValueLine(
                        "续费原价（已优惠）",
                        CartCalculator.money(quote.renewListPrice)
                    )
                }
            }

            if (quote != null && quote.lines.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CartoonColors.White.copy(alpha = 0.55f))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "配置与价格明细（站点实时返回）",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CartoonColors.Brand
                        )
                        quote.lines.forEach { line ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    listOf(line.name, line.value).filter { it.isNotBlank() }.joinToString("："),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CartoonColors.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (line.price > 0) "+${CartCalculator.money(line.price)}" else "已含",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (line.price > 0) CartoonColors.Coral else CartoonColors.InkSub
                                )
                            }
                        }
                    }
                }
            }
            sheet.quoteError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * v1.4.27 选购页辅助信息区：
 *  - 购前须知：专区页 SSR「公告·购买前必读」（基础服务条例：退款规则/使用须知…），
 *    替代 v1.4.20 误用的全站公告（那是放假/升级公告，与购前须知无关）；
 *  - 区域机器须知：从商品 description 的「地区/IP/说明」等行提取，随商品变化；
 *  - 可用优惠券：GET /coin/client/coupon（登录后返回 JSON，未登录站点回 HTML → 静默降级为空）。
 * 三者均为站点真实数据，App 只做结构化展示。
 */
@Composable
private fun PurchaseInfoBlocks(sheet: ConfigSheetState) {
    if (sheet.prePurchaseNotices.isEmpty() && sheet.regionNotices.isEmpty() &&
        !sheet.couponsLoading && sheet.coupons.isEmpty()
    ) return

    if (sheet.prePurchaseNotices.isNotEmpty()) {
        InfoCard(title = Strings.CONFIG_PRE_PURCHASE_NOTICE, tint = CartoonColors.Sky) {
            PrePurchaseNoticeContent(notices = sheet.prePurchaseNotices)
        }
    }
    if (sheet.regionNotices.isNotEmpty()) {
        InfoCard(title = Strings.CONFIG_REGION_NOTICE, tint = CartoonColors.Lemon) {
            sheet.regionNotices.forEach { line -> NoticeLine(index = null, text = line) }
        }
    }
    InfoCard(title = Strings.CONFIG_COUPON, tint = CartoonColors.Peach) {
        when {
            sheet.couponsLoading -> Text(
                Strings.CONFIG_LOADING,
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
            sheet.coupons.isEmpty() -> Text(
                Strings.CONFIG_COUPON_NONE,
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
            else -> sheet.coupons.take(4).forEach { c -> CouponLine(coupon = c) }
        }
    }
}

/** 通用玻璃信息卡：标题 + 竖排内容。 */
@Composable
private fun InfoCard(
    title: String,
    tint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = CartoonKit.CardShape,
        alpha = 0.62f,
        elevation = 0.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(width = 4.dp, height = 15.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tint)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink
                )
            }
            content()
        }
    }
}

/** 购前须知正文：章节标题加粗着色，条款逐行圆点；过长时默认折叠，可展开全文。 */
@Composable
private fun PrePurchaseNoticeContent(notices: List<com.vps.qing.data.repository.NoticeParagraph>) {
    var expanded by remember { mutableStateOf(false) }
    val collapsedLimit = 6
    val showAll = expanded || notices.size <= collapsedLimit
    val visible = if (showAll) notices else notices.take(collapsedLimit)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visible.forEach { p ->
            if (p.heading) {
                Text(
                    p.text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Sky,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                NoticeLine(index = null, text = p.text)
            }
        }
        if (notices.size > collapsedLimit) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.height(28.dp)) {
                Text(
                    if (expanded) "收起" else "展开全文（共 ${notices.size} 条）",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.Brand
                )
            }
        }
    }
}

@Composable
private fun NoticeLine(index: Int?, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        if (index != null) {
            Box(
                Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).background(CartoonColors.BrandSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.Brand,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(7.dp))
        } else {
            Text("·", style = MaterialTheme.typography.labelLarge, color = CartoonColors.Brand)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = CartoonColors.InkSub,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CouponLine(coupon: com.vps.qing.data.repository.CouponItem) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CartoonColors.PeachSoft)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coupon.amount.isNotBlank()) {
            Text(
                "￥${coupon.amount}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Coral
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                coupon.name.ifBlank { "优惠券" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val cond = buildString {
                if (coupon.condition.isNotBlank()) append(Strings.CONFIG_CONDITION_PREFIX).append(coupon.condition).append("可用")
                if (coupon.expireTime.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("有效期 ").append(coupon.expireTime)
                }
            }
            if (cond.isNotBlank()) {
                Text(cond, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            }
        }
    }
}

/** 商品指标卡片：把商品详情与配置定义里的关键指标摊开，方便核对「配置对不对」。 */
@Composable
private fun ProductIndicatorsCard(info: ProductInfo, cfg: ProductConfig) {
    val specs = CatalogParser.specsFromHtml(cfg.orderPageDescription).take(5)
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = CartoonKit.CardShape,
        alpha = 0.5f,
        elevation = 0.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                "商品指标",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Ink
            )
            KeyValueLine(
                "计费方式",
                (cfg.payType.takeIf { it.isNotBlank() } ?: info.payType)
                    .let { raw ->
                        when (raw) {
                            "recurring_prepayment" -> "预付费续费"
                            "onetime" -> "一次性"
                            "free" -> "免费"
                            "on_demand" -> "按需计费"
                            else -> raw.ifBlank { "--" }
                        }
                    }
            )
            if (info.price.isNotBlank()) {
                KeyValueLine("站点起价", "￥${info.price} / ${info.cycle}")
            }
            if (cfg.productName.isNotBlank()) {
                KeyValueLine("配置模板", cfg.productName)
            }
            if (info.resModule.isNotBlank()) {
                KeyValueLine("资源模块", info.resModule)
            }
            KeyValueLine("可购数量", if (cfg.allowQty) "可多份" else "限 1 份")
            specs.forEach { spec ->
                Text(
                    "· $spec",
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun KeyValueLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CartoonColors.Ink
        )
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 商品指标（GET /product/{id} + configoption.common_product）
        sheet.indicators?.let { ProductIndicatorsCard(it, cfg) }

        // 可自定义配置项（隐藏项仍会随算价/加购提交，只是不在面板显示）
        cfg.visibleOptions.forEach { opt ->
            ConfigOptionRow(opt, sheet.selections[opt.id]) { value ->
                onSelectOption(opt.id, value)
            }
        }

        // 周期：显示随配置同步刷新的实时价格
        if (cfg.cycles.isNotEmpty()) {
            CycleChips(cfg.cycles, sheet.cycleId, onSelectCycle, sheet.cycleQuotes)
        }

        // 数量
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(Strings.CONFIG_QTY, modifier = Modifier.weight(1f))
            QuantityStepper(sheet.qty, onQty)
        }

        // 应付总额 + 逐项价格明细（站点实时算价，已含数量）
        PriceSummary(sheet)

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
                val value = (current?.toIntOrNull() ?: opt.minValue).coerceIn(opt.minValue, opt.maxValue)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuantityStepper(
                        value = value,
                        onDelta = { delta ->
                            val next = (value + delta).coerceIn(opt.minValue, opt.maxValue)
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

