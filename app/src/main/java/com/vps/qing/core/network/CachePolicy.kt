package com.vps.qing.core.network

/**
 * 客户端主导的缓存策略（v1.4.4 网络层重构）。
 *
 * 站点实测结论（2026-11，vps.qingyayun.cn /console/v1）：
 *  - 支持 HTTP/2 与 gzip，48KB 商品列表压缩后仅 3.5KB（约 13.6 倍）；
 *  - **完全不返回** Cache-Control / ETag / Last-Modified / Expires；
 *  - If-None-Match 条件请求无效，服务端仍返回 200 全量，拿不到 304；
 *  - TTFB 约 1.6s、整请求约 3.6s，因此「少发一次请求」远比压缩更划算。
 *
 * 所以缓存必须由客户端按接口语义分级 TTL：
 *  - [CacheScope.PUBLIC]：与登录态无关的公共数据（商品、分类、公告、菜单、站点配置），
 *    允许落 OkHttp 磁盘缓存，跨冷启动复用；
 *  - [CacheScope.PRIVATE]：账号相关私有数据，只进内存缓存并按会话命名空间隔离，绝不落盘；
 *  - [CacheScope.NONE]：写操作、算价、验证码、登录等，一律不缓存。
 */
enum class CacheScope {
    PUBLIC,
    PRIVATE,
    NONE
}

data class CacheRule(
    val scope: CacheScope,
    val ttlMs: Long,
    /** 网络失败时是否允许返回过期缓存（stale-while-error），提升弱网体验。 */
    val staleOnError: Boolean = true,
    val label: String = ""
) {
    val cacheable: Boolean get() = scope != CacheScope.NONE && ttlMs > 0L

    /** 给 HTTP Cache-Control 用的秒数。 */
    val seconds: Int
        get() = (ttlMs / 1000L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}

object CachePolicy {

    /** 字典 / 配置 / 表单元数据：变动极少，缓存 30 分钟。 */
    const val TTL_DICT = 30 * 60 * 1000L

    /**
     * 商品列表：缓存 60 秒。
     * 用户明确反馈过「站点上新商品，软件内没有变动」，因此商品不能像字典那样缓存几十分钟；
     * 60 秒与选购页原有的静默同步节流窗口一致，切换 Tab 时命中缓存，一分钟后自动回源。
     */
    const val TTL_CATALOG = 60 * 1000L

    /** 公告与菜单：缓存 10 分钟。 */
    const val TTL_NEWS = 10 * 60 * 1000L

    /** 已购 / 订单 / 工单等私有列表：缓存 60 秒。 */
    const val TTL_PRIVATE = 60 * 1000L

    /** 详情页：缓存 5 分钟。 */
    const val TTL_DETAIL = 5 * 60 * 1000L

    /** 工单统计等动态数字：缓存 30 秒。 */
    const val TTL_STAT = 30 * 1000L

    private const val API_PREFIX = "/console/v1/"

    private val NONE = CacheRule(CacheScope.NONE, 0L, false, "no-store")

    /** H5 兜底页的静态资源：JS/CSS/字体/图片，缓存一天，复用同一份磁盘缓存。 */
    private val STATIC_SUFFIX = Regex(
        """\.(js|css|png|jpe?g|gif|webp|svg|ico|woff2?|ttf|eot|map)(\?.*)?$""",
        RegexOption.IGNORE_CASE
    )

    const val STATIC_TTL_SECONDS = 24 * 60 * 60

    fun isStaticAsset(encodedPath: String): Boolean =
        !encodedPath.contains(API_PREFIX) && STATIC_SUFFIX.containsMatchIn(encodedPath)

    /** 取相对 API 前缀的路径；非 API 请求（验证码、WebView 静态资源）返回 null。 */
    fun relativePath(encodedPath: String): String? {
        val i = encodedPath.indexOf(API_PREFIX)
        if (i < 0) return null
        return encodedPath.substring(i + API_PREFIX.length).trimStart('/').ifBlank { null }
    }

    /** 按 HTTP 方法与路径判定缓存规则。写操作与算价一律不缓存。 */
    fun of(method: String, encodedPath: String): CacheRule {
        if (!method.equals("GET", ignoreCase = true)) return NONE
        val rel = relativePath(encodedPath) ?: return NONE

        // 显式排除：验证码、算价、购物车结算相关的动态 GET
        if (rel.contains("captcha", true) ||
            rel.endsWith("/config_option") ||
            rel.endsWith("/duration")
        ) return NONE

        return when {
            // ---- 公共数据（可落磁盘） ----
            rel == "common" || rel == "menu" || rel == "country" ->
                CacheRule(CacheScope.PUBLIC, TTL_DICT, label = "site-config")
            rel == "news/index" || rel == "index" ->
                CacheRule(CacheScope.PUBLIC, TTL_NEWS, label = "news-menu")
            rel == "product" -> CacheRule(CacheScope.PUBLIC, TTL_CATALOG, label = "products")
            rel.startsWith("product/group/") ->
                CacheRule(CacheScope.PUBLIC, TTL_DICT, label = "product-groups")
            rel.startsWith("product/") || rel.startsWith("reidcsmart_common/") ->
                CacheRule(CacheScope.PUBLIC, TTL_DETAIL, label = "product-meta")

            // ---- 私有字典（登录态，仅内存） ----
            rel == "auth" || rel == "module" || rel == "project" || rel == "host/all" ->
                CacheRule(CacheScope.PRIVATE, TTL_DICT, label = "form-meta")
            rel == "ticket/type" || rel == "ticket/status" ||
                rel == "ticket/department" || rel == "ticket/config" ->
                CacheRule(CacheScope.PRIVATE, TTL_DICT, label = "ticket-dict")
            rel == "ticket/statistic" ->
                CacheRule(CacheScope.PRIVATE, TTL_STAT, label = "ticket-stat")

            // ---- 私有详情 ----
            rel.startsWith("host/") || rel.startsWith("order/") ||
                rel.startsWith("ticket/") || rel.startsWith("sub_account/") ||
                rel.startsWith("remf_cloud/") || rel.startsWith("certification/") ->
                CacheRule(CacheScope.PRIVATE, TTL_DETAIL, label = "detail")

            // ---- 私有列表 ----
            rel == "account" || rel == "host" || rel == "order" ||
                rel == "transaction" || rel == "ticket" || rel == "sub_account" ||
                rel == "cart" || rel == "log" || rel.startsWith("client_care/") ->
                CacheRule(CacheScope.PRIVATE, TTL_PRIVATE, label = "private-list")

            else -> NONE
        }
    }

    /**
     * 写操作成功后需要失效的缓存 key 前缀。
     * 返回空串表示清空全部缓存（登录/登出会切换会话）。
     */
    fun invalidationPrefixes(method: String, encodedPath: String): List<String> {
        if (method.equals("GET", ignoreCase = true)) return emptyList()
        val rel = relativePath(encodedPath) ?: return emptyList()
        return when {
            rel == "login" || rel == "logout" || rel.startsWith("account/password_reset") ->
                listOf("")
            rel.startsWith("cart") -> listOf("cart")
            rel.startsWith("ticket") -> listOf("ticket")
            rel.startsWith("sub_account") -> listOf("sub_account")
            else -> emptyList()
        }
    }
}
