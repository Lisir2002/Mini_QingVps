package com.vps.qing.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.number
import com.vps.qing.core.network.objectAt
import com.vps.qing.core.network.string
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 工单中心模型与解析器（v1.4.3）。
 *
 * 协议来自站点插件 idcsmart_ticket 的前端 JS 与 2026-11 实测：
 * 插件页面 plugin/27/ticket.htm、addTicket.htm、ticketDetails.htm；
 * 接口文件 plugins/addon/idcsmart_ticket/template/clientarea/pc/default/api/ticket.js。
 *
 *  - GET  /ticket/statistic   → data 为 {状态id: 数量} 映射
 *  - GET  /ticket/type        → data.list[{id,name}]，工单类型
 *  - GET  /ticket/status      → data.list[{id,name,color,status,default}]
 *  - GET  /ticket/department  → data.list[{id,name}]
 *  - GET  /ticket/config      → data{ticket_notice_open,ticket_notice_description}
 *  - GET  /ticket/{id}        → data.ticket 含 replies[] 回复时间轴
 *  - POST /ticket             → 创建（body 见 [TicketBodies.create]）
 *  - POST /ticket/{id}/reply  → 回复
 *  - PUT  /ticket/{id}/close  → 关闭
 *  - PUT  /ticket/{id}/urge   → 催单
 *
 * 全部解析走纯 JVM JsonElement，字段用多候选键宽松取值，容忍 number/string 混用。
 */

/** 工单类型 / 部门等通用字典项。 */
data class TicketOption(
    val id: String,
    val name: String,
    /** 状态色（#RRGGBB），仅状态字典有。 */
    val color: String = "",
    /** 状态归类：0=进行中 1=已结束。 */
    val groupStatus: Int = 0,
    val isDefault: Boolean = false
) {
    /** 把站点 hex 色解析成 ARGB Long，非法返回 null（UI 用主题色兜底）。 */
    fun colorArgb(): Long? {
        val hex = color.removePrefix("#")
        if (hex.length != 6 && hex.length != 8) return null
        return try {
            val v = java.lang.Long.parseUnsignedLong(if (hex.length == 6) "FF$hex" else hex, 16)
            v
        } catch (e: Exception) {
            null
        }
    }
}

/** 工单统计：状态 id -> 数量，附带便捷汇总。 */
data class TicketStatistic(
    val counts: Map<String, Int> = emptyMap()
) {
    fun countOf(statusId: String): Int = counts[statusId] ?: 0
    val total: Int get() = counts.values.sum()
    val openCount: Int get() = counts.filterKeys { it != "4" }.values.sum()
}

/** 工单通知设置。 */
data class TicketConfigInfo(
    val noticeOpen: Boolean = false,
    val noticeDescription: String = ""
)

/** 引用回复的摘要信息。 */
data class TicketQuoteInfo(
    val id: String = "",
    val senderName: String = "",
    val createTime: String = "",
    val content: String = "",
    val isDeleted: Boolean = false
)

/** 工单回复（时间轴条目）。 */
data class TicketReply(
    val id: String,
    /** Client=用户回复 / Admin=客服回复，站点原值。 */
    val type: String,
    val authorName: String,
    /** HTML 正文，已清洗为纯文本。 */
    val content: String,
    val createTime: String,
    val attachments: List<String> = emptyList(),
    val quoteReplyId: String = "",
    val quote: TicketQuoteInfo? = null
) {
    val isAdmin: Boolean get() = type.equals("Admin", true)
}

/** 工单详情。 */
data class TicketDetail(
    val id: String,
    val num: String = "",
    val title: String = "",
    val status: String = "",
    val statusId: String = "",
    val color: String = "",
    val typeId: String = "",
    val createTime: String = "",
    val hostIds: List<String> = emptyList(),
    val replies: List<TicketReply> = emptyList(),
    /** 是否可继续操作（关闭/催单）；站点 can_operate。 */
    val canOperate: Boolean = true
) {
    val isClosed: Boolean get() = statusId == "4" || status.equals("已关闭", true)
    val displayNum: String get() = num.ifBlank { id }
}

object TicketParser {

    private fun dateTime(v: String?): String {
        val ts = v?.trim()?.toLongOrNull() ?: return v?.takeIf { it.isNotBlank() } ?: "--"
        if (ts <= 0L) return "--"
        // 兼容毫秒 / 秒两种时间戳
        val millis = if (ts > 1_000_000_000_000L) ts else ts * 1000L
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
    }

    /** 把 HTML 正文清洗成可展示的纯文本。 */
    fun plainText(html: String): String {
        if (html.isBlank()) return ""
        var s = html
        s = Regex("""<(script|style)[\s\S]*?</\1>""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE).replace(s, "\n")
        s = Regex("""</(p|div|li|h\d)>""", RegexOption.IGNORE_CASE).replace(s, "\n")
        s = Regex("""<[^>]*>""").replace(s, "")
        s = listOf(
            "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
            "&quot;" to "\"", "&#39;" to "'", "&ldquo;" to "\"", "&rdquo;" to "\""
        ).fold(s) { acc, (k, v) -> acc.replace(k, v, true) }
        return s.lines().joinToString("\n") { it.trim() }
            .replace(Regex("""\n{3,}"""), "\n\n").trim()
    }

    fun statuses(data: JsonElement?): List<TicketOption> =
        (data.arrayAt("list") ?: JsonArray()).mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            TicketOption(
                id = id,
                name = el.string("name").orEmpty(),
                color = el.string("color").orEmpty(),
                groupStatus = el.number("status")?.toInt() ?: 0,
                isDefault = el.number("default", "is_default")?.toInt() == 1
            )
        }

    fun types(data: JsonElement?): List<TicketOption> =
        (data.arrayAt("list") ?: JsonArray()).mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            TicketOption(id = id, name = el.string("name").orEmpty())
        }

    fun departments(data: JsonElement?): List<TicketOption> =
        (data.arrayAt("list") ?: JsonArray()).mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            TicketOption(id = id, name = el.string("name").orEmpty())
        }

    /** /ticket/statistic 的 data 是 {状态id: 数量} 对象。 */
    fun statistic(data: JsonElement?): TicketStatistic {
        val obj = data?.takeIf { it.isJsonObject }?.asJsonObject ?: return TicketStatistic()
        val map = obj.entrySet().mapNotNull { (k, v) ->
            val n = runCatching { v.asInt }.getOrNull() ?: return@mapNotNull null
            k to n
        }.toMap()
        return TicketStatistic(map)
    }

    fun config(data: JsonElement?): TicketConfigInfo = TicketConfigInfo(
        noticeOpen = data.number("ticket_notice_open")?.toInt() == 1 ||
            data?.string("ticket_notice_open") == "1",
        noticeDescription = data.string("ticket_notice_description").orEmpty()
    )

    /** /ticket/{id} → data.ticket。 */
    fun detail(data: JsonElement?): TicketDetail? {
        val t = data.objectAt("ticket") ?: data?.takeIf { it.isJsonObject } ?: return null
        val id = t.string("id") ?: return null
        val replies = (t.arrayAt("replies") ?: JsonArray()).mapNotNull { el -> parseReply(el) }
        val hostIds = (t.arrayAt("host_ids") ?: JsonArray())
            .mapNotNull { runCatching { it.asString }.getOrNull() }
        return TicketDetail(
            id = id,
            num = t.string("ticket_num").orEmpty(),
            title = t.string("title").orEmpty(),
            status = t.string("status").orEmpty(),
            statusId = t.string("status_id").orEmpty(),
            color = t.string("color").orEmpty(),
            typeId = t.string("ticket_type_id").orEmpty(),
            createTime = dateTime(t.string("create_time")),
            hostIds = hostIds,
            replies = replies,
            canOperate = t.number("can_operate")?.toInt() != 0
        )
    }

    private fun parseReply(el: JsonElement): TicketReply? {
        val id = el.string("id") ?: return null
        val type = el.string("type").orEmpty()
        val author = if (type.equals("Client", true)) {
            el.string("client_name").orEmpty()
        } else {
            el.string("admin_name", "client_name").orEmpty()
        }
        val attachments = (el.arrayAt("attachment") ?: JsonArray())
            .mapNotNull { a -> runCatching { a.asString }.getOrNull() }
        val quoteObj = el.objectAt("quote_info")
        val quote = quoteObj?.let {
            TicketQuoteInfo(
                id = it.string("id").orEmpty(),
                senderName = it.string("sender_name").orEmpty(),
                createTime = dateTime(it.string("create_time")),
                content = plainText(it.string("content").orEmpty()),
                isDeleted = it.number("is_deleted")?.toInt() == 1
            )
        }
        return TicketReply(
            id = id,
            type = type,
            authorName = author,
            content = plainText(el.string("content").orEmpty()),
            createTime = dateTime(el.string("create_time")),
            attachments = attachments,
            quoteReplyId = el.string("quote_reply_id").orEmpty(),
            quote = quote
        )
    }
}

/** 工单写操作请求体（纯 JVM）。 */
object TicketBodies {

    /** 创建工单：host_ids 为关联产品 id 数组（可空），attachment 为上传文件名数组。 */
    fun create(
        title: String,
        typeId: String,
        content: String,
        hostIds: List<String> = emptyList(),
        attachments: List<String> = emptyList()
    ): JsonObject = JsonObject().apply {
        addProperty("title", title)
        typeId.toLongOrNull()?.let { addProperty("ticket_type_id", it) }
            ?: addProperty("ticket_type_id", typeId)
        add("host_ids", JsonArray().apply { hostIds.forEach { add(it) } })
        addProperty("content", content)
        add("attachment", JsonArray().apply { attachments.forEach { add(it) } })
    }

    /** 回复工单：换行转成站点使用的 br。 */
    fun reply(content: String, quoteReplyId: String = "0", attachments: List<String> = emptyList()): JsonObject =
        JsonObject().apply {
            addProperty("content", content.replace("\n", "<br>"))
            add("attachment", JsonArray().apply { attachments.forEach { add(it) } })
            (quoteReplyId.toLongOrNull())?.let { addProperty("quote_reply_id", it) }
                ?: addProperty("quote_reply_id", quoteReplyId)
        }

    fun action(id: String): JsonObject = JsonObject().apply {
        id.toLongOrNull()?.let { addProperty("id", it) } ?: addProperty("id", id)
    }
}
