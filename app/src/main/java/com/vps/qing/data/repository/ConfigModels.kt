package com.vps.qing.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.number
import com.vps.qing.core.network.objectAt
import com.vps.qing.core.network.string

/**
 * 商品「可自定义配置」模型与解析器。协议来自 2026-11 对站点的网页端实操：
 *
 *  - GET /reidcsmart_common/product/{id}/configoption
 *      data.configoptions  → 可下拉/可输入的配置项（option_type / subs）
 *      data.custom_cycles  → 周期选项（id / name / cycle_amount）
 *      data.common_product → 商品本体的订购页说明与计费方式
 *      data.advanced_rules → 配置项联动规则（站点当前版本多为空）
 *  - GET /product/{id}/remf_cloud/order_page （mf_cloud 套餐型商品）
 *  - POST /product/{id}/config_option → 实时算价（price / preview / duration / 折扣）
 *
 * v1.4.3 关键修正（实测）：
 *  1. 算价返回的 [PriceQuote.price] 已经是「当前周期 + 当前数量」的应付总额，
 *     不能再乘一次数量（旧版 qty>1 时价格翻倍）；
 *  2. custom_cycles[].cycle_amount 是未打折扣的原价，真实成交价必须走算价接口，
 *     因此每个周期都要按当前配置独立算价，切换选配后同步刷新；
 *  3. preview[] 每项自带 price（配置加价），可用于逐项核对「配置与价格是否对得上」。
 *
 * 站点 /product 会忽略 fpg_id/spg_id 恒返回全量，因此分类过滤放在 [CatalogParser.byGroup]
 * 本地完成；这里只负责「单个商品的配置」。全部解析走纯 JVM JsonElement，便于本地单测。
 */

/** 配置项类型，对应站点 configoptions[].option_type。 */
object OptionType {
    const val SELECT = "select"
    const val MULTI_SELECT = "multi_select"
    const val OS = "os"
    const val AREA = "area"
    const val RADIO = "radio"
    const val QUANTITY = "quantity"
    const val QUANTITY_RANGE = "quantity_range"
    const val YES_NO = "yes_no"
    const val CASCADE = "cascade"

    /** 需要展示成「下拉/点选」的类型（值是一个 sub id）。 */
    fun isChoice(type: String): Boolean =
        type == SELECT || type == MULTI_SELECT || type == OS || type == AREA || type == RADIO

    /** 需要转成单元素数组的类型（站点 formatData 对这类做 [val] 包装）。 */
    fun needsArray(type: String): Boolean =
        type == QUANTITY || type == QUANTITY_RANGE || type == MULTI_SELECT
}

/** 配置项的候选值（subs[]；os 类型下是 version[] 里的一项）。 */
data class ConfigSub(
    val id: String,
    val name: String,
    val country: String = "",
    /** os 分组名，如 CentOS / Ubuntu，用于两级展示。 */
    val group: String = "",
    /** 站点 fee_type（qty / once / recurring），用于展示加价方式。 */
    val feeType: String = ""
)

/** 一个可自定义配置项。 */
data class ConfigOption(
    val id: String,
    val name: String,
    val type: String,
    val description: String = "",
    val unit: String = "",
    val qtyMin: Int = 0,
    val qtyMax: Int = 0,
    val subs: List<ConfigSub> = emptyList(),
    /** 是否展示（站点 is_show=0 时不参与算价与展示）。 */
    val isShow: Boolean = true,
    /** 是否允许重复选购（allow_repeat）。 */
    val allowRepeat: Boolean = false
) {
    /** 默认选中的第一个候选值 id；没有候选返回 null。 */
    val defaultSubId: String? get() = subs.firstOrNull()?.id

    /** 数量型配置的合法区间（缺省时给 1..99 兜底，且保证 min <= max）。 */
    val minValue: Int get() = qtyMin.takeIf { it > 0 } ?: 1
    val maxValue: Int get() = (qtyMax.takeIf { it > 0 } ?: 99).coerceAtLeast(minValue)
}

/** 周期选项（月付 / 季付 / 半年付 / 年付）。id 会原样填进 config_options.cycle。 */
data class CycleOption(
    val id: String,
    val name: String,
    val cycleTime: Int = 0,
    val cycleUnit: String = "",
    /** 该周期对应的原价（站点 custom_cycles[].cycle_amount / amount），仅作兜底展示。 */
    val amount: Double = 0.0,
    val isDefault: Boolean = false
)

/** 配置项联动规则（站点 advanced_rules[]；当前版本多为空，先完整保留结构）。 */
data class ConfigRule(
    val id: String = "",
    val optionId: String = "",
    val subId: String = "",
    val relation: String = "",
    val targetIds: List<String> = emptyList()
)

/** 单个商品的配置定义。 */
data class ProductConfig(
    val productId: String,
    val productName: String = "",
    val options: List<ConfigOption> = emptyList(),
    val cycles: List<CycleOption> = emptyList(),
    /**
     * mf_cloud（魔方云套餐型）商品的深度适配配置；null = 通用商品走 [options]。
     * 通用 configoption 接口返回 400 时，Repository 会尝试 mf_cloud order_page，
     * 成功则填充此字段，配置面板渲染区域/线路/CPU/内存/磁盘/带宽/镜像。
     */
    val mf: MfCloudConfig? = null,
    /** 通用与 mf_cloud 都不可用时，配置面板降级为「站点选购」。 */
    val requiresWebOrder: Boolean = false,
    /** 网页选购地址（相对路径）。 */
    val webOrderPath: String = "",
    /** 商品订购页说明（HTML 转义文本），用于加强商品指标展示。 */
    val orderPageDescription: String = "",
    /** 计费方式：recurring_prepayment / free / onetime / on_demand。 */
    val payType: String = "",
    /** 是否允许购买多份（common_product.allow_qty）。 */
    val allowQty: Boolean = true,
    /** 配置项联动规则。 */
    val rules: List<ConfigRule> = emptyList()
) {
    val defaultCycleId: String? get() =
        cycles.firstOrNull { it.isDefault }?.id ?: cycles.firstOrNull()?.id

    /** 参与算价/展示的配置项（隐藏项仍要提交，但不在面板显示）。 */
    val visibleOptions: List<ConfigOption>
        get() = options.filter { it.isShow && it.type != OptionType.CASCADE }
}

/** 算价结果中的逐项明细（preview[] 的一项）。 */
data class QuoteLine(
    val name: String,
    val value: String,
    /** 该项配置的加价（站点 preview[].price，可能为字符串或数字）。 */
    val price: Double = 0.0
)

/**
 * 算价结果（POST /product/{id}/config_option 的 data）。
 *
 * [price] 已是「当前周期 + 当前 qty」的应付总额（站点实测 qty=2 时 price 翻倍），
 * UI 直接展示即可，严禁再乘数量。
 * [unitPrice] 是单份成交价，[basePrice] 是原价，两者不等时表示有折扣。
 */
data class PriceQuote(
    val price: Double = 0.0,
    val billingCycle: String = "",
    val duration: Long = 0L,
    val description: String = "",
    /** 兼容旧版：形如「流量：50G」的纯文本明细。 */
    val preview: List<String> = emptyList(),
    /** v1.4.3：带加价的逐项明细，用于核对配置与价格。 */
    val lines: List<QuoteLine> = emptyList(),
    val unitPrice: Double = 0.0,
    val basePrice: Double = 0.0,
    val setupFee: Double = 0.0,
    val renewPrice: Double = 0.0,
    val renewTotal: Double = 0.0,
    val priceTotal: Double = 0.0,
    val dueTime: Long = 0L,
    val isNaturalMonthPrepaid: Boolean = false
) {
    /** 应付总额（站点 price_total 优先，缺失回退 price）。 */
    val total: Double get() = if (priceTotal > 0.0) priceTotal else price

    /** 单份成交价（缺失回退 price）。 */
    val perUnit: Double get() = if (unitPrice > 0.0) unitPrice else price

    /** 是否享受了折扣（成交单价低于原价）。 */
    val discounted: Boolean get() = basePrice > 0.0 && perUnit > 0.0 && perUnit < basePrice - 0.001

    /** 首期与续费不一致时提示用户（常见：首月优惠、续费原价）。 */
    val renewDiffers: Boolean get() = renewPrice > 0.0 && kotlin.math.abs(renewPrice - perUnit) > 0.001
}

object ConfigParser {

    /**
     * 解析 GET /reidcsmart_common/product/{id}/configoption 的 data。
     * 站点返回 400「商品未关联通用产品模块接口」时由 Repository 转成
     * [ProductConfig.requiresWebOrder]=true，不走这里。
     */
    fun parse(productId: String, data: JsonElement?): ProductConfig {
        val common = data.objectAt("common_product")
        val name = common?.string("name")
            ?: data?.string("product_name").orEmpty()
        val optionsArr = data.arrayAt("configoptions") ?: JsonArray()
        val options = optionsArr.mapNotNull { el -> parseOption(el) }
        // custom_cycles 为首选（含周期定价），cycles 为空数组时作为兜底
        val cyclesArr = data.arrayAt("custom_cycles")?.takeIf { it.size() > 0 }
            ?: data.arrayAt("cycles") ?: JsonArray()
        val cycles = cyclesArr.mapNotNull { el -> parseCycle(el) }
        val rules = (data.arrayAt("advanced_rules") ?: JsonArray()).mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            val targets = el.arrayAt("target_ids")?.mapNotNull { t ->
                runCatching { t.asString }.getOrNull()
            }.orEmpty()
            ConfigRule(
                id = id,
                optionId = el.string("option_id", "configoption_id").orEmpty(),
                subId = el.string("sub_id", "configoption_sub_id").orEmpty(),
                relation = el.string("relation", "rule", "type").orEmpty(),
                targetIds = targets
            )
        }
        return ProductConfig(
            productId = productId,
            productName = name.orEmpty(),
            options = options,
            cycles = cycles,
            orderPageDescription = common?.string("order_page_description").orEmpty(),
            payType = common?.string("pay_type").orEmpty(),
            allowQty = common?.number("allow_qty")?.toInt() != 0,
            rules = rules
        )
    }

    private fun parseOption(el: JsonElement): ConfigOption? {
        val id = el.string("id") ?: return null
        val type = el.string("option_type").orEmpty()
        val subsArr = el.arrayAt("subs") ?: JsonArray()
        val subs = subsArr.flatMap { sub -> parseSubs(type, sub) }
        return ConfigOption(
            id = id,
            name = el.string("option_name").orEmpty(),
            type = type,
            description = el.string("description").orEmpty(),
            unit = el.string("unit").orEmpty(),
            qtyMin = el.number("qty_min")?.toInt() ?: 0,
            qtyMax = el.number("qty_max")?.toInt() ?: 0,
            subs = subs,
            isShow = el.number("is_show")?.toInt() != 0,
            allowRepeat = el.number("allow_repeat")?.toInt() == 1
        )
    }

    /** os 类型的 subs 是「系统分组」，候选值藏在 version[] 里；其它类型 subs 直接就是候选项。 */
    private fun parseSubs(type: String, sub: JsonElement): List<ConfigSub> {
        if (type == OptionType.OS) {
            val group = sub.string("os").orEmpty()
            val versions = sub.arrayAt("version") ?: JsonArray()
            return versions.mapNotNull { v ->
                val vid = v.string("id") ?: return@mapNotNull null
                ConfigSub(
                    id = vid,
                    name = v.string("option_name").orEmpty(),
                    group = group,
                    feeType = v.string("fee_type").orEmpty()
                )
            }
        }
        val sid = sub.string("id") ?: sub.string("os") ?: return emptyList()
        return listOf(
            ConfigSub(
                id = sid,
                name = sub.string("option_name").orEmpty(),
                country = sub.string("country").orEmpty(),
                feeType = sub.string("fee_type").orEmpty()
            )
        )
    }

    private fun parseCycle(el: JsonElement): CycleOption? {
        val id = el.string("id") ?: return null
        val amount = el.number("cycle_amount")
            ?: el.number("amount") ?: 0.0
        return CycleOption(
            id = id,
            name = el.string("name").orEmpty(),
            cycleTime = el.number("cycle_time")?.toInt() ?: 0,
            cycleUnit = el.string("cycle_unit").orEmpty(),
            amount = amount,
            isDefault = el.number("is_default")?.toInt() == 1
        )
    }

    /**
     * 解析算价响应 POST /product/{id}/config_option 的 data。
     * v1.4.3：完整保留逐项加价、开通费、续费价、原价与折扣信息。
     */
    fun price(data: JsonElement?): PriceQuote {
        val previewArr = data.arrayAt("preview") ?: JsonArray()
        val lines = previewArr.mapNotNull { el ->
            val label = el.string("name").orEmpty()
            val value = el.string("value").orEmpty()
            if (label.isEmpty() && value.isEmpty()) null
            else QuoteLine(label, value, el.number("price") ?: 0.0)
        }
        val preview = lines.map { line ->
            listOf(line.name, line.value).filter { it.isNotEmpty() }.joinToString("：")
        }
        val price = data.number("price") ?: data.number("discount_order_price")
        ?: data.number("base_price") ?: 0.0
        val unit = data.number("discount_order_price")
            ?: data.number("base_price") ?: price
        val base = data.number("base_price") ?: unit
        return PriceQuote(
            price = price,
            billingCycle = data.string("billing_cycle").orEmpty(),
            duration = data.number("duration")?.toLong() ?: 0L,
            description = data.string("description").orEmpty(),
            preview = preview,
            lines = lines,
            unitPrice = unit,
            basePrice = base,
            setupFee = data.number("setup_fee") ?: 0.0,
            renewPrice = data.number("renew_price") ?: data.number("discount_renew_price") ?: 0.0,
            renewTotal = data.number("renew_price_total") ?: 0.0,
            priceTotal = data.number("price_total") ?: 0.0,
            dueTime = data.number("due_time")?.toLong() ?: 0L,
            isNaturalMonthPrepaid = data.number("is_natural_month_prepaid")?.toInt() == 1
        )
    }
}
