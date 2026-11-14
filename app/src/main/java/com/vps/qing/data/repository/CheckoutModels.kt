package com.vps.qing.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.string

/**
 * 原生结算支付模型（v1.4.8 建立 / v1.4.17 支付方式全动态化）。
 * 协议对齐 IDCSmart 10.7.2 route/home.php：
 *  - GET  gateway          → data.list[{id,name,title,url}]（url 为 data:image/png;base64 图标）
 *  - POST cart/settle      → data{order_id, amount, host_ids[]}
 *  - POST  pay             → 余额支付直接 {code:"Paid"}；网关支付 data.html 为含支付链接的 HTML
 *  - GET   pay/:id/status  → {code:"Paid"|"Upaid"}，官方语义：Paid 停止轮询
 *
 * v1.4.17：站点会随时增删支付方式（实测已从「仅 EpayAli」变为 EpayAli + EpayWx），
 * 因此 App **绝不硬编码任何网关名称 / 图标 / 数量**：列表、标题、图标、提交参数
 * 全部来自 GET /gateway 的实时响应，并保留站点原始字段（id / 额外参数）以保证保真性。
 */
data class GatewayOption(
    /** POST /pay 的 gateway 参数，原样使用站点返回值。 */
    val name: String,
    /** 展示名称，原样使用站点返回 title。 */
    val title: String,
    /** 图标：站点返回 data:image/png;base64,... 或 http 链接。 */
    val iconUrl: String = "",
    /** 站点返回的网关 id（部分站点按 id 决策，保留备用，不做任何推断）。 */
    val id: String = "",
    /** 站点返回的整条原始参数（去掉超长 base64 图标），后续字段扩展不丢信息。 */
    val raw: Map<String, String> = emptyMap()
)

data class SettleResult(
    val orderId: String,
    val amount: String,
    val hostIds: List<String> = emptyList()
)

/** 发起支付结果：余额支付 paid=true；网关支付返回 payUrl。 */
data class PayOutcome(
    val paid: Boolean,
    val payUrl: String? = null
)

object CheckoutParser {

    /**
     * 解析可用支付方式。兼容站点几种返回形态（data.list / data.gateways / data 数组 /
     * 顶层数组），任何一项缺失都不影响其它网关；不做白名单、不做名称映射，站点给什么显示什么。
     */
    fun gateways(data: JsonElement?): List<GatewayOption> {
        val arr: JsonArray = when {
            data == null -> null
            data.isJsonArray -> data.asJsonArray
            data.isJsonObject -> {
                val root = data.asJsonObject
                when {
                    root.get("list")?.isJsonArray == true -> root.getAsJsonArray("list")
                    root.get("gateways")?.isJsonArray == true -> root.getAsJsonArray("gateways")
                    root.get("data")?.isJsonArray == true -> root.getAsJsonArray("data")
                    else -> null
                }
            }
            else -> null
        } ?: return emptyList()

        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // 原始字段保真：除超长 base64 图标外全部按字符串留存
            val raw = o.entrySet().mapNotNull { (k, v) ->
                if (k == "url") null
                else runCatching { k to v.asString }.getOrNull()
            }.toMap()
            GatewayOption(
                name = name,
                title = o.string("title").orEmpty().ifBlank { name },
                iconUrl = o.string("url").orEmpty(),
                id = o.string("id").orEmpty(),
                raw = raw
            )
        }
    }

    fun settle(data: JsonElement?): SettleResult? {
        val o = data?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val orderId = o.string("order_id")?.takeIf { it.isNotBlank() } ?: return null
        return SettleResult(
            orderId = orderId,
            amount = o.string("amount").orEmpty(),
            hostIds = o.get("host_ids")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { runCatching { it.asString }.getOrNull() }
                .orEmpty()
        )
    }

    /**
     * 解析 POST /pay 的响应：code=Paid 即余额支付成功；
     * 否则从 data.html 里提取支付跳转链接（站点返回
     * `<a href="https://zf...submit.php?...">立即支付</a>`，易支付网关）。
     */
    fun payOutcome(data: JsonElement?): PayOutcome {
        val root = data?.takeIf { it.isJsonObject }?.asJsonObject
        val code = root?.string("code").orEmpty().uppercase()
        if (code == "PAID") return PayOutcome(paid = true)
        val html = root?.get("html")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val url = Regex("""href\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.takeIf { it.size > 1 }?.get(1)
        return PayOutcome(paid = false, payUrl = url?.takeIf { it.startsWith("http") })
    }

    /** 支付状态轮询：返回 "Paid" / "Unpaid"。 */
    fun payStatus(data: JsonElement?): String {
        val root = data?.takeIf { it.isJsonObject }?.asJsonObject ?: return "Unpaid"
        return root.string("code").orEmpty().uppercase().ifBlank { "UNPAID" }
    }
}

object CheckoutBodies {

    /** POST /cart/settle：{positions:[行下标], customfield:{}}。 */
    fun settle(positions: List<Int>): JsonObject = JsonObject().apply {
        val arr = JsonArray()
        positions.forEach { arr.add(it) }
        add("positions", arr)
        add("customfield", JsonObject())
    }

    /** POST /pay：{id:订单ID, gateway}。gateway 直接透传站点返回的 name，不做映射。 */
    fun pay(orderId: String, gateway: String): JsonObject = JsonObject().apply {
        val id = orderId.toIntOrNull()
        if (id != null) addProperty("id", id) else addProperty("id", orderId)
        addProperty("gateway", gateway)
    }
}
