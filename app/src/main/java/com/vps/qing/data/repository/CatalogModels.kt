package com.vps.qing.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.string

/**
 * 选购页 / 仪表盘用到的数据模型与解析器。
 *
 * 字段全部来自 2026-11 对站点的匿名实测：
 *  - GET /product/group/first      → data.list[{id,name,type}]
 *  - GET /product/group/second?id= → data.list[{id,name,parent_id,type,description}]
 *  - GET /product?fpg_id=&spg_id=  → data.list[{id,name,description(HTML),price,cycle,qty,...}]
 *  - GET /news/index               → data.list[{id,title,type,create_time}]
 *  - GET /menu                     → data.menu[{name,url,child}]
 *
 * 解析全部走纯 JVM 的 JsonElement，既便于本地单测，也能容忍站点把 id 从 number 改成 string。
 */
data class ProductGroup(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val description: String = ""
)

data class ProductInfo(
    val id: String,
    val name: String,
    val price: String = "",
    val cycle: String = "",
    val qty: String? = null,
    val stockControl: Boolean = false,
    val groupFirstId: String? = null,
    val groupFirstName: String? = null,
    val groupSecondId: String? = null,
    val groupSecondName: String? = null,
    val payType: String = "",
    val descriptionHtml: String = "",
    /** 从商品 description 的 `div.pd-br` 里抽出来的真实配置项，例如「月流量：50GiB」。 */
    val specs: List<String> = emptyList()
) {
    val unitPrice: Double get() = price.toDoubleOrNull() ?: 0.0
}

data class NewsItem(
    val id: String,
    val title: String,
    val type: String = "",
    val createTime: Long = 0L
)

/** 已订购主机：需登录态，字段未实测，按多候选键宽松解析，缺失显示 --。 */
data class HostSummary(
    val id: String? = null,
    val name: String? = null,
    val status: String? = null,
    val productName: String? = null,
    val dueTime: String? = null
)

data class MenuEntry(
    val name: String,
    val url: String = "",
    val children: List<MenuEntry> = emptyList()
)

object CatalogParser {

    private val DIV_BLOCK = Regex("""<div[^>]*>([\s\S]*?)</div>""")
    private val ANY_TAG = Regex("""<[^>]*>""")
    private val BR_TAG = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val ENTITIES = listOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&#39;" to "'", "&ldquo;" to "\"", "&rdquo;" to "\""
    )

    private fun JsonElement?.listOrNull(): JsonArray? =
        this?.arrayAt("list") ?: this?.takeIf { it.isJsonArray }?.asJsonArray

    /**
     * 商品 description 是一整段 HTML（站点商品详情页原样带过来），
     * 配置项藏在 `<div class="pd-br">…</div>` 与 `<br />` 换行里。
     * 这里把它抽成纯文本行，用于商品卡片和购物车条目的「配置」展示。
     */
    fun specsFromHtml(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val blocks = DIV_BLOCK.findAll(html).map { it.groupValues[1] }.toList()
        val source = blocks.ifEmpty { listOf(html) }
        return source.flatMap { block ->
            val noBr = BR_TAG.replace(block, "\n")
            val plain = ANY_TAG.replace(noBr, "")
            val decoded = ENTITIES.fold(plain) { acc, (entity, value) -> acc.replace(entity, value) }
            decoded.split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() && it.length <= 120 }
        }.distinct().take(12)
    }

    fun firstGroups(data: JsonElement?): List<ProductGroup> = groups(data)

    fun secondGroups(data: JsonElement?): List<ProductGroup> = groups(data)

    private fun groups(data: JsonElement?): List<ProductGroup> =
        data.listOrNull()?.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            ProductGroup(
                id = id,
                name = el.string("name") ?: "未命名分类",
                parentId = el.string("parent_id"),
                description = el.string("description").orEmpty()
            )
        } ?: emptyList()

    fun products(data: JsonElement?): List<ProductInfo> =
        data.listOrNull()?.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            val html = el.string("description").orEmpty()
            ProductInfo(
                id = id,
                name = el.string("name") ?: "未命名商品",
                price = el.string("price").orEmpty(),
                cycle = el.string("cycle").orEmpty(),
                qty = el.string("qty"),
                stockControl = el.string("stock_control") == "1",
                groupFirstId = el.string("product_group_id_first"),
                groupFirstName = el.string("product_group_name_first"),
                groupSecondId = el.string("product_group_id_second"),
                groupSecondName = el.string("product_group_name_second"),
                payType = el.string("pay_type").orEmpty(),
                descriptionHtml = html,
                specs = specsFromHtml(html)
            )
        } ?: emptyList()

    fun news(data: JsonElement?): List<NewsItem> =
        data.listOrNull()?.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            NewsItem(
                id = id,
                title = el.string("title").orEmpty(),
                type = el.string("type").orEmpty(),
                createTime = el.string("create_time")?.toLongOrNull() ?: 0L
            )
        } ?: emptyList()

    fun hosts(data: JsonElement?): List<HostSummary> =
        data.listOrNull()?.map { el ->
            HostSummary(
                id = el.string("id", "host_id"),
                name = el.string("name", "host_name", "domain", "product_name"),
                status = el.string("status", "status_text", "status_name"),
                productName = el.string("product_name", "product", "name"),
                dueTime = el.string("due_time", "end_time", "expire_time", "next_due_date")
            )
        } ?: emptyList()

    fun menu(data: JsonElement?): List<MenuEntry> {
        val arr = data?.arrayAt("menu") ?: data.listOrNull() ?: return emptyList()
        return arr.mapNotNull { el ->
            val name = el.string("name") ?: return@mapNotNull null
            val childArray = el.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("child")?.takeIf { it.isJsonArray }?.asJsonArray
            val children = childArray?.mapNotNull { child ->
                child.string("name")?.let { childName ->
                    MenuEntry(childName, child.string("url").orEmpty())
                }
            } ?: emptyList()
            MenuEntry(name, el.string("url").orEmpty(), children)
        }
    }
}
