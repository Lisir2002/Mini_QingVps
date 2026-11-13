package com.vps.qing.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.objectAt
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
    val specs: List<String> = emptyList(),
    /** v1.4.3 商品指标：实际资源模块（mf_cloud / idcsmart_common…），可提前判断配置类型。 */
    val resModule: String = "",
    /** 商品模式（only_api 等）。 */
    val mode: String = "",
    /** 站点商品分组 id（product_group_id）。 */
    val groupId: String = "",
    /** 库存（qty）。 */
    val stock: String = "",
    /** 是否支持试用付费。 */
    val payOnTrial: Boolean = false,
    /** 是否推荐商品。 */
    val recommend: Boolean = false,
    /** 供应商名称（商品详情）。 */
    val supplierName: String = "",
    /** 是否上架（商品详情 show=1）。 */
    val show: Boolean = true
) {
    val unitPrice: Double get() = price.toDoubleOrNull() ?: 0.0

    /** 是否需要走 mf_cloud 深度配置（列表字段即可预判，省掉一次必失败的通用配置请求）。 */
    val isMfCloud: Boolean
        get() = resModule.contains("mf_cloud", true) || resModule.contains("mf_dcim", true)

    /** 展示用的计费方式文案。 */
    val payTypeText: String
        get() = when (payType) {
            "recurring_prepayment" -> "预付费续费"
            "onetime" -> "一次性"
            "free" -> "免费"
            "on_demand" -> "按需计费"
            else -> payType.ifBlank { "--" }
        }
}

data class NewsItem(
    val id: String,
    val title: String,
    val type: String = "",
    val createTime: Long = 0L
)

/**
 * 公告详情（v1.4.10）：GET /news/{id} → data.news。
 * 正文是一整页 HTML，解析为段落列表供原生详情页直排渲染。
 */
data class NewsDetail(
    val id: String,
    val title: String,
    val type: String = "",
    val createTime: Long = 0L,
    val paragraphs: List<String> = emptyList()
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

    private val DIV_BLOCK = Regex("""<div[^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
    private val ANY_TAG = Regex("""<[^>]*>""")
    private val BR_TAG = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val DIV_CLOSE = Regex("""</div\s*>""", RegexOption.IGNORE_CASE)
    private val DOC_TAG = Regex("""<!DOCTYPE[^>]*>""", RegexOption.IGNORE_CASE)
    private val SCRIPT_BLOCK = Regex("""<(script|style)[\s\S]*?</\1>""", RegexOption.IGNORE_CASE)
    private val STRUCT_TAG = Regex("""</?(html|head|body)[^>]*>""", RegexOption.IGNORE_CASE)
    /** 块级标签（v1.4.10 公告正文分段用）。 */
    private val BLOCK_CLOSE = Regex("""</(p|div|h[1-6]|li|tr|section|article)\s*>""", RegexOption.IGNORE_CASE)
    private val BLOCK_OPEN = Regex("""<(p|div|h[1-6]|li|tr|section|article)[^>]*/?>""", RegexOption.IGNORE_CASE)
    private val NUMERIC_ENTITY = Regex("""&#(\d+);""")
    private val ENTITIES = listOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&#39;" to "'", "&ldquo;" to "\"", "&rdquo;" to "\"",
        "&times;" to "×", "&ge;" to "≥", "&le;" to "≤"
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
        // 站点 description 是「整页 HTML 套整页 HTML」的嵌套结构（含 DOCTYPE / html / head / body）。
        // v1.4.3：先剥离结构性标签与脚本，再把 div 开合、br 统一转成换行，
        // 这样既拿得到 div.pd-br 里的配置项，也不会漏掉块外的「处理器：…」等说明。
        var text = DOC_TAG.replace(html, "")
        text = SCRIPT_BLOCK.replace(text, "")
        text = STRUCT_TAG.replace(text, "")
        text = DIV_BLOCK.replace(text) { it.groupValues[1] + "\n" }
        text = DIV_CLOSE.replace(text, "\n")
        text = BR_TAG.replace(text, "\n")
        text = ANY_TAG.replace(text, "")
        text = ENTITIES.fold(text) { acc, (entity, value) -> acc.replace(entity, value, true) }
        return text.split("\n")
            .map { it.trim().replace(Regex("""\s{2,}"""), " ") }
            .filter { it.isNotBlank() && it.length <= 120 && it != "DOCTYPE" }
            .distinct()
            .take(16)
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

    /**
     * 本地分类过滤。站点 GET /product 会忽略 fpg_id/spg_id 恒返回全量商品，
     * 因此不能靠接口参数区分分类，只能按每条商品自带的分组 id 归类（2026-11 实测确认）。
     * v1.2.2 起二级分类严格过滤：选中无商品的二级分类时返回空列表，
     * 让 UI 显示「该分类下暂无商品」空态，而不是悄悄回退到一级分类全量。
     */
    fun byGroup(
        all: List<ProductInfo>,
        firstId: String?,
        secondId: String?
    ): List<ProductInfo> {
        if (!secondId.isNullOrBlank()) {
            return all.filter { it.groupSecondId == secondId }
        }
        if (!firstId.isNullOrBlank()) {
            return all.filter { it.groupFirstId == firstId }
        }
        return all
    }

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
                specs = specsFromHtml(html),
                resModule = el.string("res_module", "module").orEmpty(),
                mode = el.string("mode").orEmpty(),
                groupId = el.string("product_group_id").orEmpty(),
                stock = el.string("qty").orEmpty(),
                payOnTrial = el.arrayAt("pay_ontrial")?.size()?.takeIf { it > 0 } != null,
                recommend = el.string("recommend") == "1"
            )
        } ?: emptyList()

    /**
     * 商品详情 GET /product/{id} → data.product。
     * v1.4.3：补齐列表接口没有的指标（分组 id、供应商、上架状态、模块、模式），
     * 用于配置页展示「计费方式 / 起价 / 商品类型」，避免只靠列表字段猜测。
     */
    fun productDetail(data: JsonElement?): ProductInfo? {
        val el = data?.objectAt("product") ?: data?.takeIf { it.isJsonObject } ?: return null
        val id = el.string("id") ?: return null
        return ProductInfo(
            id = id,
            name = el.string("name") ?: "未命名商品",
            price = el.string("price").orEmpty(),
            cycle = el.string("cycle").orEmpty(),
            payType = el.string("pay_type").orEmpty(),
            groupId = el.string("product_group_id").orEmpty(),
            groupFirstId = el.string("product_group_id_first").orEmpty(),
            resModule = el.string("module").orEmpty(),
            mode = el.string("mode").orEmpty(),
            supplierName = el.string("supplier_name").orEmpty(),
            show = el.string("show") == "1"
        )
    }

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

    /** 公告详情 GET /news/{id} → data.news（v1.4.10）。 */
    fun newsDetail(data: JsonElement?): NewsDetail? {
        val el = data?.objectAt("news") ?: data?.takeIf { it.isJsonObject } ?: return null
        val id = el.string("id") ?: return null
        return NewsDetail(
            id = id,
            title = el.string("title") ?: "公告详情",
            type = el.string("type").orEmpty(),
            createTime = el.string("create_time")?.toLongOrNull() ?: 0L,
            paragraphs = paragraphsFromHtml(el.string("content").orEmpty())
        )
    }

    /**
     * 公告正文 HTML → 段落列表（v1.4.10）。与 [specsFromHtml] 共用清洗设施，
     * 但面向全文阅读：块级标签（p/div/h1-6/li/tr/section/article/br）统一转段落换行，
     * 不限行宽、不截断、不去重，保留公告原有段落顺序。
     */
    fun paragraphsFromHtml(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        var text = DOC_TAG.replace(html, "")
        text = SCRIPT_BLOCK.replace(text, "")
        text = STRUCT_TAG.replace(text, "")
        text = BLOCK_CLOSE.replace(text, "\n")
        text = BR_TAG.replace(text, "\n")
        text = BLOCK_OPEN.replace(text, "\n")
        text = ANY_TAG.replace(text, "")
        text = ENTITIES.fold(text) { acc, (entity, value) -> acc.replace(entity, value, true) }
        text = NUMERIC_ENTITY.replace(text) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
        }
        return text.split("\n")
            .map { it.trim().replace(Regex("""\s{2,}"""), " ") }
            .filter { it.isNotBlank() }
    }

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
