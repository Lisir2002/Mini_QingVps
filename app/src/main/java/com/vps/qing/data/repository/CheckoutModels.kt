package com.vps.qing.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.string

/**
 * 原生结算支付模型（v1.4.8）。协议对齐 IDCSmart 10.4.6 route/home.php：
 *  - GET  gateway          → data.list[{id,name,title,url}]（url 为 data:image/png;base64 图标）
 *  - POST cart/settle      → data{order_id, amount, host_ids[]}
 *  - POST  pay             → 余额支付直接 {code:"Paid"}；网关支付 data.html 为含支付链接的 HTML
 *  - GET   pay/:id/status  → {code:"Paid"|"Upaid"}，官方语义：Paid 停止轮询
 */
data class GatewayOption(
    val name: String,
    val title: String,
    val iconUrl: String = ""
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

    fun gateways(data: JsonElement?): List<GatewayOption> {
        val list = data?.arrayAt("list") ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GatewayOption(
                name = name,
                title = o.string("title").orEmpty().ifBlank { name },
                iconUrl = o.string("url").orEmpty()
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

    /** POST /pay：{id:订单ID, gateway}。 */
    fun pay(orderId: String, gateway: String): JsonObject = JsonObject().apply {
        val id = orderId.toIntOrNull()
        if (id != null) addProperty("id", id) else addProperty("id", orderId)
        addProperty("gateway", gateway)
    }
}
