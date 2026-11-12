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
 *      data.custom_cycles  → 周期选项（id / name / amount）
 *  - GET /product/{id}/remf_cloud/order_page （mf_cloud 套餐型商品，本版仅识别不深挖）
 *  - POST /product/{id}/config_option → 实时算价（price / preview / duration）
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
    val group: String = ""
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
    val subs: List<ConfigSub> = emptyList()
) {
    /** 默认选中的第一个候选值 id；没有候选返回 null。 */
    val defaultSubId: String? get() = subs.firstOrNull()?.id
}

/** 周期选项（月付 / 季付 / 半年付 / 年付）。id 会原样填进 config_options.cycle。 */
data class CycleOption(
    val id: String,
    val name: String,
    val cycleTime: Int = 0,
    val cycleUnit: String = "",
    /** 该周期对应价格（站点 custom_cycles[].cycle_amount / amount）。 */
    val amount: Double = 0.0,
    val isDefault: Boolean = false
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
    val webOrderPath: String = ""
) {
    val defaultCycleId: String? get() =
        cycles.firstOrNull { it.isDefault }?.id ?: cycles.firstOrNull()?.id
}

/** 算价结果。 */
data class PriceQuote(
    val price: Double = 0.0,
    val billingCycle: String = "",
    val duration: Long = 0L,
    val description: String = "",
    val preview: List<String> = emptyList()
)

object ConfigParser {

    /**
     * 解析 GET /reidcsmart_common/product/{id}/configoption 的 data。
     * 站点返回 400「商品未关联通用产品模块接口」时由 Repository 转成
     * [ProductConfig.requiresWebOrder]=true，不走这里。
     */
    fun parse(productId: String, data: JsonElement?): ProductConfig {
        val name = data.objectAt("common_product")?.string("name")
            ?: data?.string("product_name").orEmpty()
        val optionsArr = data.arrayAt("configoptions") ?: JsonArray()
        val options = optionsArr.mapNotNull { el -> parseOption(el) }
        val cyclesArr = data.arrayAt("custom_cycles") ?: JsonArray()
        val cycles = cyclesArr.mapNotNull { el -> parseCycle(el) }
        return ProductConfig(
            productId = productId,
            productName = name.orEmpty(),
            options = options,
            cycles = cycles
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
            subs = subs
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
                    group = group
                )
            }
        }
        val sid = sub.string("id") ?: sub.string("os") ?: return emptyList()
        return listOf(
            ConfigSub(
                id = sid,
                name = sub.string("option_name").orEmpty(),
                country = sub.string("country").orEmpty()
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

    /** 解析算价响应 POST /product/{id}/config_option 的 data。 */
    fun price(data: JsonElement?): PriceQuote {
        val previewArr = data.arrayAt("preview") ?: JsonArray()
        val preview = previewArr.mapNotNull { el ->
            val label = el.string("name").orEmpty()
            val value = el.string("value").orEmpty()
            if (label.isEmpty() && value.isEmpty()) null
            else listOf(label, value).filter { it.isNotEmpty() }.joinToString("：")
        }
        return PriceQuote(
            price = data.number("price") ?: data.number("discount_order_price")
            ?: data.number("base_price") ?: 0.0,
            billingCycle = data.string("billing_cycle").orEmpty(),
            duration = data.number("duration")?.toLong() ?: 0L,
            description = data.string("description").orEmpty(),
            preview = preview
        )
    }
}
