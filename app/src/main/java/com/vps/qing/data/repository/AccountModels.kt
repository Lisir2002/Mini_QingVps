package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.string

/**
 * 个人中心「账号与服务」原生数据模型（v1.3.0，不再跳网页）。
 *
 * 字段全部来自 2026-11 用登录态对站点 /console/v1 的实测：
 *  - GET /host         → data.list[{id, product_id, product_name, name, active_time, due_time,
 *                          status, renew_amount, billing_cycle, dedicate_ip, base_info, image_name...}]
 *  - GET /order        → data.list[{id, type, create_time, amount, status, gateway,
 *                          host_name, description, product_names...}]
 *  - GET /transaction  → data.list[{id, amount, gateway, transaction_number, order_id, create_time...}]
 *  - GET /ticket       → data.list[{...}]
 *  - GET /sub_account  → data.list[{...}]
 *  - GET /account      → data.account{id, username, email, phone, credit, status, register_time...}
 *
 * 分页注意：与 /product 相同，站点列表接口默认 limit=20，必须显式传大 limit。
 */
object ServiceParser {

    /** 通用取列表：data.list 数组 + data.count 总数。 */
    fun rawList(data: JsonElement?): Pair<List<JsonElement>, Int> {
        val arr = data?.takeIf { it.isJsonObject }?.asJsonObject?.get("list")
            ?.takeIf { it.isJsonArray }?.asJsonArray
        val list = arr?.toList() ?: emptyList()
        val count = data?.takeIf { it.isJsonObject }?.asJsonObject?.get("count")
            ?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() ?: list.size
        return list to count
    }

    /** 时间戳 → 本地时间字符串；失败原样返回。 */
    private fun time(v: String?): String {
        val ts = v?.toLongOrNull() ?: return v.orEmpty()
        if (ts <= 0) return "--"
        return java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.CHINA)
            .format(java.util.Date(ts * 1000))
    }

    fun hosts(data: JsonElement?): List<HostLine> =
        rawList(data).first.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            HostLine(
                id = id,
                name = el.string("name").orEmpty(),
                productName = el.string("product_name").orEmpty(),
                status = el.string("status").orEmpty(),
                dueTime = time(el.string("due_time")),
                activeTime = time(el.string("active_time")),
                billingCycle = el.string("billing_cycle").orEmpty(),
                renewAmount = el.string("renew_amount").orEmpty(),
                ip = el.string("dedicate_ip").orEmpty().ifBlank { el.string("assign_ip").orEmpty() },
                baseInfo = el.string("base_info").orEmpty(),
                imageName = el.string("image_name").orEmpty(),
                renewForbidden = el.string("renew_forbidden") == "1"
            )
        }

    fun orders(data: JsonElement?): List<OrderLine> =
        rawList(data).first.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            val names = el.takeIf { it.isJsonObject }?.asJsonObject?.get("product_names")
                ?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { runCatching { it.asString }.getOrNull() }
                .orEmpty()
            OrderLine(
                id = id,
                amount = el.string("amount").orEmpty(),
                status = el.string("status").orEmpty(),
                gateway = el.string("gateway").orEmpty(),
                createTime = time(el.string("create_time")),
                payTime = time(el.string("pay_time")),
                hostName = el.string("host_name").orEmpty(),
                productNames = names,
                description = el.string("description").orEmpty().trim(),
                type = el.string("type").orEmpty()
            )
        }

    fun transactions(data: JsonElement?): List<TxnLine> =
        rawList(data).first.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            TxnLine(
                id = id,
                amount = el.string("amount").orEmpty(),
                gateway = el.string("gateway").orEmpty(),
                number = el.string("transaction_number").orEmpty(),
                orderId = el.string("order_id").orEmpty(),
                createTime = time(el.string("create_time")),
                type = el.string("type").orEmpty()
            )
        }

    fun tickets(data: JsonElement?): List<TicketLine> =
        rawList(data).first.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            TicketLine(
                id = id,
                title = el.string("title", "name").orEmpty(),
                status = el.string("status").orEmpty(),
                createTime = time(el.string("create_time")),
                lastReply = time(el.string("last_reply_time", "update_time"))
            )
        }

    fun subAccounts(data: JsonElement?): List<SubAccountLine> =
        rawList(data).first.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            SubAccountLine(
                id = id,
                username = el.string("username", "name").orEmpty(),
                email = el.string("email").orEmpty(),
                status = el.string("status").orEmpty()
            )
        }

    /** /account → 安全中心概要（邮箱/手机绑定状态）。 */
    fun security(account: AccountInfo): List<Pair<String, String>> = listOf(
        "邮箱绑定" to (account.email?.takeIf { it.isNotBlank() } ?: "未绑定"),
        "手机绑定" to (account.phone?.takeIf { it.isNotBlank() } ?: "未绑定"),
        "余额" to (account.balance?.takeIf { it.isNotBlank() } ?: "--"),
        "信用额度" to (account.creditLimit?.takeIf { it.isNotBlank() } ?: "--")
    )
}

data class HostLine(
    val id: String,
    val name: String,
    val productName: String,
    val status: String,
    val dueTime: String,
    val activeTime: String,
    val billingCycle: String,
    val renewAmount: String,
    val ip: String,
    val baseInfo: String,
    val imageName: String,
    val renewForbidden: Boolean
)

data class OrderLine(
    val id: String,
    val amount: String,
    val status: String,
    val gateway: String,
    val createTime: String,
    val payTime: String,
    val hostName: String,
    val productNames: List<String>,
    val description: String,
    val type: String
)

data class TxnLine(
    val id: String,
    val amount: String,
    val gateway: String,
    val number: String,
    val orderId: String,
    val createTime: String,
    val type: String
)

data class TicketLine(
    val id: String,
    val title: String,
    val status: String,
    val createTime: String,
    val lastReply: String
)

data class SubAccountLine(
    val id: String,
    val username: String,
    val email: String,
    val status: String
)

/** 测试辅助：构造空包装对象。 */
internal fun listWrapper(vararg items: JsonObject): JsonObject =
    JsonObject().apply {
        add("list", com.google.gson.JsonArray().apply { items.forEach { add(it) } })
        addProperty("count", items.size)
    }
