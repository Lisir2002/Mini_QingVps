package com.vps.qing.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.ApiEnvelope
import com.vps.qing.core.network.number
import com.vps.qing.core.network.string

/**
 * 原生结算支付模型（v1.4.8 建立 / v1.4.17 支付方式动态化 / v1.4.18 支付码真实性修复）。
 * 协议对齐 IDCSmart 10.7.2 官方前端 payDialog.js：
 *  - GET  gateway          → data.list[{id,name,title,url}]（url 为 data:image/png;base64 图标）
 *  - POST cart/settle      → data{order_id, amount, host_ids[]}
 *  - POST pay              → **data.html 是站点返回的整段支付 HTML**，官方用
 *                            `$("#payBox").html(res.data.data.html)` 原样渲染，
 *                            真实支付二维码 / 支付按钮都在这段 HTML 里（易支付收银台）；
 *                            余额支付则 code=Paid。
 *  - GET  pay/:id/status   → 响应体**顶层** `code: "Paid" | "Upaid"`（官方
 *                            `res.data.code === "Paid"` 停止轮询）。
 *  - GET  order/:id        → data.order{unpaid_timeout, remain_pay_time} 为真实支付有效期。
 *
 * v1.4.18 修复（用户实测反馈）：
 *  - 旧版把 `data.html` 里的 `href`（易支付 submit.php 收银台链接）自己编码成本地二维码，
 *    那**不是网页端给用户的支付码**；现在保留并渲染站点原始 HTML，或提取真实二维码内容/图片。
 *  - 旧版读 `data.code` 判断支付成功，而站点 code 在顶层 → 永远检测不到 Paid；
 *    现在 [payStatusEnvelope] 优先读 [ApiEnvelope.code]。
 *  - 有效期旧版写死「10 分钟」，现在来自订单 `unpaid_timeout / remain_pay_time`。
 *  - 支付方式继续全动态：列表、名称、图标、提交参数全部来自 GET /gateway 实时响应。
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

/** 发起支付结果：余额支付 paid=true；网关支付返回站点原始支付 HTML 与可提取的支付信息。 */
data class PayOutcome(
    val paid: Boolean,
    /** 收银台链接（data.html 里的 href），用于「立即支付」跳转 / 复制，绝不当作二维码内容。 */
    val payUrl: String? = null,
    /** 站点返回的原始支付 HTML：网页端 `#payBox` 同款内容（真实样本为「立即支付」跳转按钮）。 */
    val payHtml: String = "",
    /** 站点支付按钮文案（如「立即支付」），原生按钮与网页端保持一致。 */
    val payButtonLabel: String = "立即支付",
    /** 从 payHtml 提取到的真实二维码内容（code_url / alipays:// / weixin:// 等），可为空。 */
    val qrContent: String? = null,
    /** 从 payHtml 提取到的真实二维码图片地址（data:image 或 http 图片），可为空。 */
    val qrImageUrl: String? = null,
    /** 响应若直接给出有效期（秒），优先于订单详情。 */
    val expireSeconds: Int? = null
)

/** 订单支付有效期：来自 GET /order/{id} 的 order 字段，与官方 payDialog 一致。 */
data class PayDeadline(
    /** unpaid_timeout > 0 表示站点启用了支付超时。 */
    val hasTimeout: Boolean = false,
    /** 剩余可支付秒数（remain_pay_time）。 */
    val remainSeconds: Int = 0
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

    /** 兼容旧调用：只拿到 data 时仍可解析（顶层 code 为空则退回 data.code）。 */
    fun payOutcome(data: JsonElement?): PayOutcome = parsePay("", data)

    /**
     * v1.4.18：POST /pay 的业务 code 同样在**响应体顶层**。
     * 站点 pay_ctrl.php 明确：`status==200 且 code==Paid` 表示支付完成，
     * `Unpaid` 才需要把 data.html 渲染出来。旧版只读 data.code，
     * 余额全额抵扣时永远读不到 Paid，会错误进入「等待支付」阶段。
     */
    fun payOutcomeEnvelope(env: ApiEnvelope): PayOutcome = parsePay(env.code, env.data)

    private fun parsePay(topCode: String, data: JsonElement?): PayOutcome {
        val root = data?.takeIf { it.isJsonObject }?.asJsonObject
        val code = topCode.ifBlank { root?.string("code").orEmpty() }.uppercase()
        if (code == "PAID") return PayOutcome(paid = true)
        val html = root?.get("html")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val expire = (root?.number("expire_seconds") ?: root?.number("expire") ?: root?.number("timeout"))
            ?.toInt()?.takeIf { it > 0 }
        return PayOutcome(
            paid = false,
            payUrl = extractCashierUrl(html),
            payHtml = html,
            payButtonLabel = extractPayButtonLabel(html),
            qrContent = extractQrContent(html),
            qrImageUrl = extractQrImage(html),
            expireSeconds = expire
        )
    }

    /**
     * 收银台跳转链接。站点真实样本是
     * `<a href="https://zf.qingyayun.cn/submit.php?...">立即支付</a>`（易支付收银台）。
     * 优先取含 submit.php / /pay 的支付链接，避免把图标、CSS 之类的 href 误当收银台。
     * 该链接只用于「立即支付」跳转与复制，**绝不再拿去本地编码成二维码**。
     */
    private fun extractCashierUrl(html: String): String? {
        val hrefs = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { it.groupValues.getOrNull(1).orEmpty() }
            .filter { it.startsWith("http", true) }
            .toList()
        // 优先站点收银台（易支付 submit.php / 其它网关 cashier 路径）
        return hrefs.firstOrNull { it.contains("submit.php", true) }
            ?: hrefs.firstOrNull { it.contains("/cashier", true) || it.contains("/pay/", true) }
            // 排除样式 / 脚本 / 图标等静态资源，避免把 pay.css 之类误当支付入口
            ?: hrefs.firstOrNull {
                val path = it.substringBefore("?")
                !path.endsWith(".css", true) && !path.endsWith(".js", true) &&
                    !path.endsWith(".ico", true) && !path.endsWith(".png", true) &&
                    !path.endsWith(".jpg", true) && !path.endsWith(".svg", true)
            }
    }

    /** 站点支付按钮文案（真实样本为「立即支付」），原生按钮与网页端保持一致。 */
    private fun extractPayButtonLabel(html: String): String =
        Regex("""<a[^>]*>\s*([^<]{1,24})\s*</a>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() && !it.contains("http", true) }
            ?: "立即支付"

    /** 站点若在 HTML 里直接给出真实支付码内容（code_url / 支付深链），提取出来本地编码。 */
    private fun extractQrContent(html: String): String? {
        val named = Regex(
            """(?:code_url|qrcode|qr_code|qr_url|pay_url)\s*[:=]\s*["']([^"']{8,})["']""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        if (!named.isNullOrBlank() && !named.equals("undefined", true)) return named
        val scheme = Regex(
            """(?:alipays://[^\s"'<>]+|weixin://[^\s"'<>]+|https?://qr\.[^\s"'<>]+)""",
            RegexOption.IGNORE_CASE
        ).find(html)?.value
        return scheme?.trim()?.takeIf { it.length >= 8 }
    }

    /** 站点若在 HTML 里直接给了二维码图片（含 data:image base64），原样使用。 */
    private fun extractQrImage(html: String): String? {
        val candidates = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.groupValues.getOrNull(1).orEmpty() }
        return candidates.firstOrNull { src ->
            src.startsWith("data:image", true) ||
                src.contains("qrcode", true) || src.contains("qr_code", true) ||
                src.contains("/qr/", true) || src.contains("qrcode", true)
        }
    }

    /** 支付状态轮询：返回 "PAID" / "UNPAID"。优先读响应体顶层 code（官方语义）。 */
    fun payStatus(data: JsonElement?): String {
        val root = data?.takeIf { it.isJsonObject }?.asJsonObject ?: return "UNPAID"
        return root.string("code").orEmpty().uppercase().ifBlank { "UNPAID" }
    }

    /**
     * v1.4.18：官方 `GET /pay/{id}/status` 把 code 放在**响应体顶层**
     * （`{"status":200,"msg":"...","code":"Paid"}`），不在 data 里。
     * 这里同时兼容顶层与 data.code 两种形态，返回 "PAID" / "UNPAID"。
     */
    fun payStatusEnvelope(env: ApiEnvelope): String {
        val top = env.code.uppercase()
        if (top == "PAID") return "PAID"
        if (top == "UPAID" || top == "UNPAID") return "UNPAID"
        return payStatus(env.data)
    }

    /** 订单支付有效期：兼容 data.order 与 data 直接携带两种形态。 */
    fun deadline(data: JsonElement?): PayDeadline {
        val root = data?.takeIf { it.isJsonObject }?.asJsonObject ?: return PayDeadline()
        val order = root.get("order")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
        val timeout = order.number("unpaid_timeout")?.toInt() ?: 0
        val remain = order.number("remain_pay_time")?.toInt() ?: 0
        return PayDeadline(hasTimeout = timeout > 0, remainSeconds = remain.coerceAtLeast(0))
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
