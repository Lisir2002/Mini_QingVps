package com.vps.qing.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.objectAt
import com.vps.qing.core.network.string

/**
 * 宽松取数组：站点真实返回里，公告/新闻/帮助/优惠券的数据都包在 `data.list` 里，
 * 少数插件直接把 data 给成数组。两种都兼容，避免消息中心三个列表因 `data.list`
 * 未命中而全空（v1.4.20 实测修复）。
 * 顶层私有 helper，避免与 CatalogModels 的同名 private 扩展冲突。
 */
private fun JsonElement?.asArrayOrNull(): JsonArray? =
    this?.takeIf { it.isJsonArray }?.asJsonArray
        ?: this?.takeIf { it.isJsonObject }?.asJsonObject?.get("list")?.takeIf { it.isJsonArray }?.asJsonArray

/**
 * v1.4.20 消息中心统一模型。
 *
 * 设计目标：把站点四类"内容型"信息与"个人通知"收敛到一个消息中心：
 *  - [MessageKind.ANNOUNCEMENT] 网站公告（GET /announcement）
 *  - [MessageKind.NEWS]         新闻资讯（GET /news/index）
 *  - [MessageKind.RESOURCE]     帮助/资源文章（GET /help）
 *  - [MessageKind.MAIL]         站内信 / 站点通知（GET /client_care/mail/list，带已读未读）
 *
 * 文章类（公告/新闻/资源）是站点全局内容，本身没有"已读"语义；
 * 站内信与本地业务通知（工单新回复/支付成功等）才有已读/未读，由 [MailItem.read] 表达。
 */
enum class MessageKind(val label: String) {
    ANNOUNCEMENT("公告"),
    NEWS("新闻"),
    RESOURCE("资源"),
    MAIL("通知")
}

/** 统一文章内容条目（公告/新闻/资源共用的列表展示模型）。 */
data class ArticleItem(
    val id: String,
    val title: String,
    val kind: MessageKind,
    val type: String = "",
    val createTime: Long = 0L,
    /** 资源/帮助文章的分类名（帮助分组），公告/新闻可为空。 */
    val groupName: String = ""
) {
    /** 详情接口类型标记，仓库据此选择 /announcement/{id} 或 /news/{id} 或 /help/{id}。 */
    val detailKind: MessageKind get() = kind
}

/** 统一文章详情：正文解析为段落列表，走原生详情页渲染。 */
data class ArticleDetail(
    val id: String,
    val title: String,
    val type: String = "",
    val createTime: Long = 0L,
    val paragraphs: List<String> = emptyList()
)

/** 站内信条目（含已读/未读）。read=false 表示未读。 */
data class MailItem(
    val id: String,
    val title: String,
    val content: String = "",
    val read: Boolean = false,
    val createTime: Long = 0L,
    val type: String = ""
) {
    /** 列表里展示的摘要：正文清洗后的纯文本前若干字。 */
    val summary: String get() = content.replace(Regex("""<[^>]+>"""), "").trim().take(80)
}

/** 站内信概要（未读数驱动消息中心角标）。 */
data class MailSummary(
    val unread: Int = 0,
    val total: Int = 0
)

/** 优惠券条目（用户可用券）。 */
data class CouponItem(
    val id: String,
    val name: String = "",
    val amount: String = "",
    val condition: String = "",
    val expireTime: String = "",
    val code: String = "",
    val raw: String = ""
)

/** 解析站点消息 / 优惠券响应（纯 JVM）。 */
object MessageParser {

    /** 公告中心 GET /announcement → data.list[{id,title,img,create_time,type}]。 */
    fun announcements(data: JsonElement?): List<ArticleItem> =
        data.asArrayOrNull()?.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            ArticleItem(
                id = id,
                title = el.string("title").orEmpty(),
                kind = MessageKind.ANNOUNCEMENT,
                type = el.string("type").orEmpty(),
                createTime = el.string("create_time")?.toLongOrNull() ?: 0L
            )
        } ?: emptyList()

    /** 新闻 GET /news/index → data.list。 */
    fun news(data: JsonElement?): List<ArticleItem> =
        data.asArrayOrNull()?.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            ArticleItem(
                id = id,
                title = el.string("title").orEmpty(),
                kind = MessageKind.NEWS,
                type = el.string("type").orEmpty(),
                createTime = el.string("create_time")?.toLongOrNull() ?: 0L
            )
        } ?: emptyList()

    /** 帮助中心 GET /help → data.list[{id,name,helps:[{id,title}]}]，展平为文章条目。 */
    fun resources(data: JsonElement?): List<ArticleItem> {
        val groups = data.asArrayOrNull() ?: return emptyList()
        return buildList {
            groups.forEach { g ->
                val groupName = g.string("name").orEmpty()
                val helps = g.arrayAt("helps") ?: g.arrayAt("list")
                helps?.forEach { h ->
                    val id = h.string("id") ?: return@forEach
                    add(
                        ArticleItem(
                            id = id,
                            title = h.string("title").orEmpty(),
                            kind = MessageKind.RESOURCE,
                            groupName = groupName
                        )
                    )
                }
            }
        }
    }

    /** 公告详情 data.announcement / 新闻详情 data.news / 帮助详情 data.help。 */
    fun articleDetail(data: JsonElement?, kind: MessageKind, paragraphs: (String) -> List<String>): ArticleDetail? {
        val el = when (kind) {
            MessageKind.ANNOUNCEMENT -> data?.objectAt("announcement")
            MessageKind.NEWS -> data?.objectAt("news")
            MessageKind.RESOURCE -> data?.objectAt("help") ?: data?.objectAt("article")
            MessageKind.MAIL -> null
        } ?: data?.takeIf { it.isJsonObject }
        ?: return null
        val id = el.string("id") ?: return null
        return ArticleDetail(
            id = id,
            title = el.string("title") ?: "详情",
            type = el.string("type").orEmpty(),
            createTime = el.string("create_time")?.toLongOrNull() ?: 0L,
            paragraphs = paragraphs(el.string("content").orEmpty())
        )
    }

    /** 站内信列表 data.list 或 data.mail.list。 */
    fun mails(data: JsonElement?): List<MailItem> {
        val list = data.asArrayOrNull()
            ?: data?.objectAt("list")?.asArrayOrNull()
            ?: data?.objectAt("mail")?.arrayAt("list")
            ?: return emptyList()
        return list.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            // 已读字段多候选：is_read / read / status；站点约定 1=已读，0=未读
            val readRaw = el.string("is_read") ?: el.string("read") ?: el.string("status")
            val read = when (readRaw) {
                "1", "true", "TRUE", "read", "已读" -> true
                else -> false
            }
            MailItem(
                id = id,
                title = el.string("title") ?: el.string("subject") ?: "站点通知",
                content = el.string("content") ?: el.string("message").orEmpty(),
                read = read,
                createTime = el.string("create_time")?.toLongOrNull() ?: 0L,
                type = el.string("type").orEmpty()
            )
        }
    }

    /** 站内信概要：未读数候选 is_read 计数或直接给未读字段。 */
    fun mailSummary(data: JsonElement?, listHint: List<MailItem> = emptyList()): MailSummary {
        val obj = data?.takeIf { it.isJsonObject } ?: return MailSummary(unread = listHint.count { !it.read }, total = listHint.size)
        val unread = obj.string("unread")?.toIntOrNull()
            ?: obj.string("unread_count")?.toIntOrNull()
            ?: obj.string("unread_num")?.toIntOrNull()
            ?: listHint.count { !it.read }
        val total = obj.string("total")?.toIntOrNull() ?: listHint.size
        return MailSummary(unread = unread, total = total)
    }

    /** 优惠券 data.list。金额/门槛字段名多候选，宽松解析。 */
    fun coupons(data: JsonElement?): List<CouponItem> {
        val list = data.asArrayOrNull() ?: data?.objectAt("list")?.asArrayOrNull() ?: return emptyList()
        return list.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            CouponItem(
                id = id,
                name = el.string("name") ?: el.string("title") ?: "优惠券",
                amount = el.string("amount") ?: el.string("money") ?: el.string("discount") ?: "",
                condition = el.string("condition") ?: el.string("full") ?: el.string("threshold") ?: "",
                expireTime = el.string("expire_time") ?: el.string("end_time") ?: "",
                code = el.string("code") ?: "",
                raw = el.toString()
            )
        }
    }
}
