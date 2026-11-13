package com.vps.qing.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.number
import com.vps.qing.core.network.string

/**
 * mf_cloud（魔方云套餐型）商品的深度适配模型。协议来自 2026-11 对站点网页端 JS
 * （plugins/reserver/mf_cloud/template/clientarea/pc/default/js/mf_cloud.js 与 api/mf_cloud.js）
 * 的实测与解析，全部字段经 /console/v1 实际请求验证：
 *
 *  - GET  /product/{id}/remf_cloud/order_page   → 区域(国家→城市→区域→线路)/CPU/内存/磁盘
 *  - GET  /product/{id}/remf_cloud/image        → 操作系统两级列表（系统分组 → 镜像）
 *  - GET  /product/{id}/remf_cloud/line/{line}  → 线路详情（bill_type + bw/flow 档位）
 *  - POST /product/{id}/remf_cloud/duration     → body = 配置参数 + id，返回周期列表
 *  - POST /product/{id}/config_option           → body = {id, config_options, qty}，实时算价
 *  - POST /cart                                 → body = {product_id, config_options, qty, ...}
 *
 * 站点前端实测（美国大宽带 8核8G，product_id=24）加购成功、GET /cart 立即可见。
 * 全部解析走纯 JVM JsonElement，便于本地单测。
 */

/** 一个可选数值档位（radio 固定单值 / slider 区间步进）。 */
data class MfRangeOption(
    val type: String,
    val value: Int,
    val minValue: Int = 0,
    val maxValue: Int = 0,
    val step: Int = 1
) {
    /**
     * 展开成可展示的候选值。radio 只有一个值；
     * slider 展开为等差序列（最多 [limit] 档，末尾兜底 maxValue）。
     */
    fun candidates(limit: Int = 12): List<Int> {
        if (type != "slider" || maxValue <= minValue || step <= 0) return listOf(value)
        val steps = ((maxValue - minValue) / step).coerceAtMost(limit - 1)
        val list = (0..steps).map { minValue + it * step }.toMutableList()
        if (list.last() < maxValue) list.add(maxValue)
        return list.distinct()
    }

    companion object {
        fun from(el: JsonElement?): MfRangeOption? {
            val obj = el?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val v = obj.number("value")?.toInt() ?: return null
            return MfRangeOption(
                type = obj.string("type").orEmpty(),
                value = v,
                minValue = obj.number("min_value")?.toInt() ?: 0,
                maxValue = obj.number("max_value")?.toInt() ?: 0,
                step = obj.number("step")?.toInt() ?: 1
            )
        }
    }
}

/** 区域 + 线路（data_center → city → area → line 合并为一个可选项）。 */
data class MfAreaOption(
    val areaId: Int,
    val areaName: String,
    val cityName: String,
    val countryName: String,
    val lineId: Int,
    val lineName: String,
    /** bill_type：bw = 按带宽计费（config_options.bw）；flow = 按流量计费（config_options.flow）。 */
    val billType: String,
    val bw: MfRangeOption? = null,
    val flow: MfRangeOption? = null
) {
    /** 展示名：国家 · 区域（如「美国 · 美国4837」）。 */
    val label: String get() = listOf(countryName, areaName).filter { it.isNotBlank() }.distinct().joinToString(" · ")
}

/** 操作系统镜像。 */
data class MfImage(val id: Int, val name: String, val price: Double = 0.0)

/** 操作系统分组（CentOS / Ubuntu / Debian…）。 */
data class MfImageGroup(val id: Int, val name: String, val images: List<MfImage>)

/**
 * 单个 mf_cloud 商品完整配置定义。由 [MfParser.parse] 从 order_page + image + line 详情组装。
 */
data class MfCloudConfig(
    val productId: String,
    val areas: List<MfAreaOption> = emptyList(),
    val cpus: List<Int> = emptyList(),
    val memories: List<Int> = emptyList(),
    val systemDisks: List<Int> = emptyList(),
    val dataDisks: List<Int> = emptyList(),
    val imageGroups: List<MfImageGroup> = emptyList(),
    /**
     * 网络类型开关（v1.4.8）：站点前端规则——
     * type=="host" 且 support_normal_network==0 → "vpc"，否则 "normal"。
     * 结算时站点校验 network_type 必填，传空串会报「请选择网络类型」。
     */
    val supportNormalNetwork: Boolean = true
) {
    val networkType: String get() = if (supportNormalNetwork) "normal" else "vpc"
    val images: List<MfImage> get() = imageGroups.flatMap { it.images }

    fun imageName(imageId: Int): String? = images.firstOrNull { it.id == imageId }?.name
        ?: imageGroups.firstOrNull { it.images.any { i -> i.id == imageId } }
            ?.let { group -> group.images.firstOrNull { it.id == imageId }?.name }
}

/** 用户在配置面板里对 mf_cloud 商品的全部选择。 */
data class MfSelection(
    val areaId: Int,
    val lineId: Int,
    val cpu: Int,
    val memory: Int,
    val systemDisk: Int,
    val dataDisk: Int = 0,
    val bw: Int = 0,
    val imageId: Int,
    val durationId: String? = null
) {
    companion object {
        /** 从配置定义取每个维度的默认第一项。 */
        fun default(cfg: MfCloudConfig): MfSelection? {
            val area = cfg.areas.firstOrNull() ?: return null
            val image = cfg.images.firstOrNull() ?: return null
            val bwCandidates = area.bw?.candidates().orEmpty()
            return MfSelection(
                areaId = area.areaId,
                lineId = area.lineId,
                cpu = cfg.cpus.firstOrNull() ?: 0,
                memory = cfg.memories.firstOrNull() ?: 0,
                systemDisk = cfg.systemDisks.firstOrNull() ?: 0,
                dataDisk = cfg.dataDisks.firstOrNull() ?: 0,
                bw = bwCandidates.firstOrNull() ?: 0,
                imageId = image.id
            )
        }
    }
}

// ---------------- 解析 ----------------

object MfParser {

    /** 解析 GET /product/{id}/remf_cloud/order_page 的 data。 */
    fun parseOrderPage(productId: String, data: JsonElement?): MfCloudConfig {
        val areas = mutableListOf<MfAreaOption>()
        data.arrayAt("data_center")?.forEach { country ->
            val countryName = country.string("name").orEmpty()
            country.arrayAt("city")?.forEach { city ->
                val cityName = city.string("name").orEmpty()
                city.arrayAt("area")?.forEach { area ->
                    val areaId = area.number("id")?.toInt() ?: return@forEach
                    val areaName = area.string("name").orEmpty()
                    area.arrayAt("line")?.forEach { line ->
                        val lineId = line.number("id")?.toInt() ?: return@forEach
                        areas += MfAreaOption(
                            areaId = areaId,
                            areaName = areaName,
                            cityName = cityName,
                            countryName = countryName,
                            lineId = lineId,
                            lineName = line.string("name").orEmpty(),
                            billType = line.string("bill_type").orEmpty()
                        )
                    }
                }
            }
        }

        fun intValues(key: String): List<Int> =
            data.arrayAt(key)?.mapNotNull { it.number("value")?.toInt() }.orEmpty()

        // system_disk / data_disk 是档位数组，取每档的 value（容量 GB）
        fun diskSizes(key: String): List<Int> =
            data.arrayAt(key)?.mapNotNull { it.number("value")?.toInt() }.orEmpty().distinct()

        return MfCloudConfig(
            productId = productId,
            areas = areas,
            cpus = intValues("cpu").distinct(),
            memories = intValues("memory").distinct(),
            systemDisks = diskSizes("system_disk"),
            dataDisks = diskSizes("data_disk"),
            supportNormalNetwork = run {
                // 与站点 mf_cloud.js 对齐：非 host 一律 normal；host 按 support_normal_network 开关
                val type = data.string("config", "type").orEmpty()
                val supportNormal = data.string("config", "support_normal_network").orEmpty()
                type != "host" || supportNormal != "0"
            }
        )
    }

    /** 解析 GET /product/{id}/remf_cloud/image 的 data → 操作系统分组。 */
    fun parseImages(data: JsonElement?): List<MfImageGroup> =
        data.arrayAt("list")?.mapNotNull { group ->
            val gid = group.number("id")?.toInt() ?: return@mapNotNull null
            val images = group.arrayAt("image")?.mapNotNull { img ->
                val id = img.number("id")?.toInt() ?: return@mapNotNull null
                MfImage(id = id, name = img.string("name").orEmpty(), price = img.number("price") ?: 0.0)
            }.orEmpty()
            if (images.isEmpty()) null
            else MfImageGroup(id = gid, name = group.string("name").orEmpty(), images = images)
        }.orEmpty()

    /**
     * 把 GET line/{line_id} 的 data（{bill_type, bw:[…], flow:[…]}）合并进区域选项。
     * order_page 的 line 节点不带档位，必须单独取线路详情（站点前端同样这么做）。
     */
    fun mergeLineDetail(areas: List<MfAreaOption>, lineId: Int, data: JsonElement?): List<MfAreaOption> =
        areas.map { area ->
            if (area.lineId != lineId) area
            else area.copy(
                billType = data?.string("bill_type").takeUnless { it.isNullOrBlank() } ?: area.billType,
                bw = MfRangeOption.from(data?.arrayAt("bw")?.firstOrNull()) ?: area.bw,
                flow = MfRangeOption.from(data?.arrayAt("flow")?.firstOrNull()) ?: area.flow
            )
        }
}

// ---------------- 请求体构建（字段与站点 mf_cloud.js 逐项对齐） ----------------

object MfCloudCart {

    /**
     * config_options 主体（算价 / 周期 / 加购共用）。
     * 站点前端对 bw 计费线路删掉 flow、flow 计费删掉 bw；ip_num/ipv6_num 未选传 null。
     * network_type 由商品配置推导（v1.4.8）：结算时站点强校验该字段。
     */
    fun configOptions(sel: MfSelection, networkType: String = "normal"): JsonObject {
        val body = JsonObject().apply {
            addProperty("data_center_id", sel.areaId)
            addProperty("cpu", sel.cpu)
            addProperty("memory", sel.memory)
            addProperty("image_id", sel.imageId)
            addProperty("app_preset_id", 0)
            add("system_disk", JsonObject().apply {
                addProperty("size", sel.systemDisk)
                addProperty("disk_type", "")
                addProperty("disk_limit_id", "")
            })
            add("data_disk", if (sel.dataDisk > 0) JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("size", sel.dataDisk)
                    addProperty("disk_type", "")
                    addProperty("disk_limit_id", "")
                })
            } else JsonArray())
            addProperty("line_id", sel.lineId)
            addProperty("peak_defence", "")
            add("ip_num", com.google.gson.JsonNull.INSTANCE)
            add("ipv6_num", com.google.gson.JsonNull.INSTANCE)
            addProperty("network_type", networkType)
        }
        if (sel.bw > 0) body.addProperty("bw", sel.bw) else body.addProperty("flow", "")
        sel.durationId?.let { runCatching { body.addProperty("duration_id", it.toInt()) } }
            ?: body.addProperty("duration_id", 0)
        return body
    }

    /** POST /product/{id}/remf_cloud/duration：body = 配置参数平铺 + id。 */
    fun durationBody(sel: MfSelection, productId: String, networkType: String = "normal"): JsonObject =
        configOptions(sel, networkType).apply { addProperty("id", productId.toIntOrNull() ?: 0) }

    /** POST /product/{id}/config_option（mf_cloud 算价）：{id, config_options, qty}。 */
    fun calcBody(sel: MfSelection, productId: String, qty: Int, networkType: String = "normal"): JsonObject = JsonObject().apply {
        addProperty("id", productId.toIntOrNull() ?: 0)
        add("config_options", configOptions(sel, networkType).apply {
            addProperty("promo_code", "")
            addProperty("event_promotion", "")
        })
        addProperty("qty", qty)
    }

    /** POST /cart（mf_cloud 加购）：实测「添加成功」，GET /cart 立即可见。 */
    fun cartBody(sel: MfSelection, productId: String, qty: Int, networkType: String = "normal"): JsonObject = JsonObject().apply {
        addProperty("product_id", productId.toIntOrNull() ?: 0)
        add("config_options", configOptions(sel, networkType).apply {
            addProperty("activeName", "custom")
            addProperty("login_way", "auto_create")
            addProperty("password", "")
            addProperty("re_password", "")
        })
        addProperty("qty", qty)
        add("customfield", JsonObject())
        add("self_defined_field", JsonObject())
    }
}
