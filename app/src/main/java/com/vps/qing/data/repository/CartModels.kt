package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.objectAt
import com.vps.qing.core.network.string
import java.util.Locale

/**
 * 购物车条目。
 *
 * v1.2.0 起购物车以「站点云端购物车」为准：加购走 POST /console/v1/cart（真实入云），
 * 成功后重新拉 GET /console/v1/cart 覆盖本地列表。条目里的 [configLabels] 是
 * 把站点返回的 configoption {选项id: subId} 用商品配置定义翻译出来的可读配置，
 * 因此「添加的服务器及其配置」能在购物车页直接看到。
 *
 * [position] 是站点购物车行下标（站点前端 rules：list 的 index 即 position），
 * 改数量 / 删除都用它。本地快速反馈时 position = -1。
 */
data class CartItem(
    val productId: String,
    val productName: String,
    val specs: List<String> = emptyList(),
    val cycle: String = "",
    val unitPrice: Double = 0.0,
    val quantity: Int = 1,
    val groupName: String = "",
    // ---- 自定义配置 / 云端同步 ----
    val position: Int = -1,
    val cycleId: String? = null,
    /** 如 ["流量：50G", "操作系统：CentOS-7.9"]。 */
    val configLabels: List<String> = emptyList(),
    /** 原始 configoption 映射（optionId -> 选中值），用于改配置或调试。 */
    val configOptionRaw: Map<String, String> = emptyMap(),
    /** 是否来自云端 GET /cart。 */
    val synced: Boolean = false
) {
    /** 同商品 + 同周期 + 同位置视为同一条目；本地条目 position 恒 -1，保持合并语义。 */
    val key: String get() = "$productId|$cycle|$position"
    val subtotal: Double get() = unitPrice * quantity
}

/** 纯 JVM 的购物车运算（ViewModel 只持有 List<CartItem>）。 */
object CartCalculator {
    fun add(items: List<CartItem>, item: CartItem): List<CartItem> {
        if (item.quantity <= 0) return items
        val index = items.indexOfFirst { it.key == item.key }
        if (index < 0) return items + item
        return items.toMutableList().also { mutable ->
            val old = mutable[index]
            mutable[index] = old.copy(quantity = old.quantity + item.quantity)
        }
    }

    /** 数量减到 0 时自动移除该条目。 */
    fun changeQty(items: List<CartItem>, key: String, delta: Int): List<CartItem> =
        items.mapNotNull { item ->
            if (item.key != key) item
            else item.copy(quantity = item.quantity + delta).takeIf { it.quantity > 0 }
        }

    fun remove(items: List<CartItem>, key: String): List<CartItem> =
        items.filterNot { it.key == key }

    fun count(items: List<CartItem>): Int = items.sumOf { it.quantity }

    fun total(items: List<CartItem>): Double = items.sumOf { it.subtotal }

    fun money(amount: Double): String = String.format(Locale.CHINA, "￥%.2f", amount)
}

/**
 * 解析站点云端购物车 GET /console/v1/cart 的 data。
 *
 * 实测单行：
 * {"product_id":45,"name":"APAC | 亚太-体验版","qty":1,
 *  "description":"<html>…",
 *  "config_options":{"configoption":{"12568":45697},
 *                    "cascade_configoption":[],"cycle":3706,...}}
 *
 * [configLookup] 用商品 id 查本地缓存的 ProductConfig，把 id 翻成中文名；
 * 查不到时退化为显示原始 id，保证任何情况下都有内容、不崩。
 */
object CloudCartParser {

    fun parse(
        data: JsonElement?,
        configLookup: (String) -> ProductConfig? = { null }
    ): List<CartItem> {
        val list = data.arrayAt("list") ?: return emptyList()
        return list.mapIndexedNotNull { index, el -> parseLine(index, el, configLookup) }
    }

    private fun parseLine(
        position: Int,
        el: JsonElement,
        configLookup: (String) -> ProductConfig?
    ): CartItem? {
        val productId = el.string("product_id") ?: return null
        val name = el.string("name") ?: "商品 $productId"
        val qty = el.string("qty")?.toIntOrNull() ?: 1
        val html = el.string("description").orEmpty()
        val specs = CatalogParser.specsFromHtml(html)

        val co = el.objectAt("config_options")
        val configOption = co?.objectAt("configoption")
        val cfg = configLookup(productId)

        // mf_cloud（魔方云套餐型）条目：config_options 是平铺的 {data_center_id, cpu, ...}，
        // 没有 configoption 映射，用 MfCloudConfig 翻译成可读配置；无缓存时退化显示原始数值。
        val isMfLine = configOption == null && co?.get("data_center_id") != null
        if (isMfLine) {
            return parseMfLine(position, el, co!!, cfg?.mf, cfg?.cycles ?: emptyList(), qty, html)
        }

        val cycleId = co?.string("cycle")
        val raw = mutableMapOf<String, String>()
        configOption?.entrySet()?.forEach { (optionId, value) ->
            val v = runCatching { value.asString }.getOrNull() ?: value.toString()
            raw[optionId] = v
        }

        // 周期名 + 单价：优先用配置里的 custom_cycles 按 id 匹配
        val cycleMatch = cfg?.cycles?.firstOrNull { it.id == cycleId }
        val cycleName = cycleMatch?.name ?: cycleId?.let { "周期 $it" } ?: ""
        val unitPrice = cycleMatch?.amount ?: 0.0

        // 把 {optionId: subId} 翻译成「选项名：候选名」
        val labels = raw.map { (optionId, subId) ->
            val opt = cfg?.options?.firstOrNull { it.id == optionId }
            val sub = opt?.subs?.firstOrNull { it.id == subId }
            val optName = opt?.name ?: "配置$optionId"
            val subName = sub?.name ?: subId
            "$optName：$subName"
        }

        return CartItem(
            productId = productId,
            productName = name,
            specs = specs,
            cycle = cycleName,
            unitPrice = unitPrice,
            quantity = qty,
            position = position,
            cycleId = cycleId,
            configLabels = labels,
            configOptionRaw = raw,
            synced = true
        )
    }

    /**
     * mf_cloud 条目翻译（实测结构：config_options = {data_center_id:529, cpu:8, memory:8,
     * image_id:10243, system_disk:{size:70}, line_id:746, bw:300, duration_id:1432, ...}）。
     * 用 MfCloudConfig 把数值翻成中文，周期与单价走 duration 缓存（ensureMfCycles 已补齐）。
     */
    private fun parseMfLine(
        position: Int,
        el: JsonElement,
        co: JsonObject,
        mf: MfCloudConfig?,
        cycles: List<CycleOption>,
        qty: Int,
        html: String
    ): CartItem {
        val areaId = co.get("data_center_id")?.asIntOrNull()
        val cpu = co.get("cpu")?.asIntOrNull()
        val memory = co.get("memory")?.asIntOrNull()
        val diskSize = co.objectAt("system_disk")?.get("size")?.asIntOrNull()
        val bw = co.get("bw")?.asIntOrNull()
        val imageId = co.get("image_id")?.asIntOrNull()
        val durationId = co.get("duration_id")?.let {
            runCatching { it.asString }.getOrNull() ?: it.toString()
        }

        val area = mf?.areas?.firstOrNull { it.areaId == areaId }
        val labels = buildList {
            area?.let { add("区域线路：${it.label}") }
            cpu?.let { add("CPU：${it}核") }
            memory?.let { add("内存：${it}GB") }
            diskSize?.let { add("系统盘：${it}GB") }
            bw?.let { add("带宽：${it}Mbps") }
            imageId?.let { id -> mf?.imageName(id)?.let { add("操作系统：$it") } }
        }

        val cycleMatch = cycles.firstOrNull { it.id == durationId }
        val raw = mapOf(
            "data_center_id" to areaId?.toString().orEmpty(),
            "cpu" to cpu?.toString().orEmpty(),
            "memory" to memory?.toString().orEmpty(),
            "system_disk" to diskSize?.toString().orEmpty(),
            "bw" to bw?.toString().orEmpty(),
            "image_id" to imageId?.toString().orEmpty()
        ).filterValues { it.isNotEmpty() }

        return CartItem(
            productId = el.string("product_id").orEmpty(),
            productName = el.string("name") ?: "商品",
            specs = CatalogParser.specsFromHtml(html),
            cycle = cycleMatch?.name ?: durationId?.let { "周期 $it" } ?: "",
            unitPrice = cycleMatch?.amount ?: 0.0,
            quantity = qty,
            position = position,
            cycleId = durationId,
            configLabels = labels,
            configOptionRaw = raw,
            synced = true
        )
    }

    private fun com.google.gson.JsonElement.asIntOrNull(): Int? =
        runCatching { if (isJsonPrimitive) asString.toIntOrNull() ?: asInt else asInt }.getOrNull()
}
