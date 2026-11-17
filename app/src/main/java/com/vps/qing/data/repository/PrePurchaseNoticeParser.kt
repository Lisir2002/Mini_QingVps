package com.vps.qing.data.repository

/**
 * 购前须知解析器（v1.4.27）。
 *
 * 依据：魔方 V10 站点专区选购页 `cart/goodsList.htm?fpg_id=&spg_id=` 的 SSR HTML。
 * 真正的「购前须知」是页面顶部「公告 · 购买前必读」区块——一段名为「基础服务条例」的
 * 结构化条款（一、退款规则 / 二、使用须知 …），而非全站公告列表（那只是放假/升级公告）。
 *
 * HTML 结构（PC 与手机各一份，内容一致，优先 PC）：
 * ```
 * <div id="announcementContentPC" ...>
 *   <p class="font-bold">基础服务条例（...）</p>
 *   <p>一、退款规则</p>
 *   <p>1. 常规区：...</p>
 *   ...
 * </div>
 * ```
 *
 * v1.4.20 曾错误地把 `GET /announcement` 前 3 条当购前须知（显示为中秋放假等公告），
 * 本解析器纠正数据源，直接解析站点真实 SSR 区块。
 */
data class NoticeParagraph(
    /** 段落文本（已清洗 HTML / 样式残留）。 */
    val text: String,
    /** 是否为章节标题（如「一、退款规则」「二、使用须知」或首个加粗总标题）。 */
    val heading: Boolean = false
)

object PrePurchaseNoticeParser {

    // 公告正文 div：优先 PC，回退手机端。用 div 深度配对提取完整内容。
    private val CONTENT_ID = Regex(
        """id=["']announcementContent(?:PC)?["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val SCRIPT_STYLE = Regex("""<(script|style)\b[\s\S]*?</\1>""", RegexOption.IGNORE_CASE)
    private val ANY_TAG = Regex("""<[^>]+>""")
    private val BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val BLOCK_TAG = Regex("""</?(p|h[1-6]|div|li)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val WS = Regex("""[ \t]+""")
    private val BOLD_P = Regex("""<p\b[^>]*\bfont-bold\b[^>]*>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)

    /** 章节标题：一、xxx / 二、xxx / 第N条 等中文序号开头。 */
    private val HEADING = Regex("""^\s*[一二三四五六七八九十]+[、.．]""")

    /**
     * 从专区页 HTML 解析购买前必读。
     * @return 结构化段落；找不到公告块返回空列表。
     */
    fun parse(html: String): List<NoticeParagraph> {
        if (html.isBlank()) return emptyList()
        val idMatch = CONTENT_ID.find(html) ?: return emptyList()
        val body = extractDivBody(html, idMatch.range.last + 1)
        if (body.isBlank()) return emptyList()

        // 逐块标签转行：<p>/<h2>/<div>/<li> 都是一段；<br> 也断行
        val withBreaks = BLOCK_TAG.replace(SCRIPT_STYLE.replace(body, ""), "\n")
        val replacedBr = BR.replace(withBreaks, "\n")
        val boldSpans = BOLD_P.findAll(body).map { clean(it.groupValues.getOrNull(1).orEmpty()) }.toSet()

        return replacedBr.split("\n")
            .map { clean(it) }
            .filter { it.length >= 2 }
            .distinct()
            .take(40) // 防止异常 HTML 导致内容过长
            .map { text ->
                val isHeading = HEADING.containsMatchIn(text) || text in boldSpans ||
                    text.startsWith("基础服务条例") || text.startsWith("使用须知")
                NoticeParagraph(text = text, heading = isHeading)
            }
    }

    /** 从 <div ...> 之后的 position 开始，按 div 嵌套深度配对取出正文。 */
    private fun extractDivBody(html: String, start: Int): String {
        val divTag = Regex("""<(/?)div\b""", RegexOption.IGNORE_CASE)
        var depth = 1
        var pos = start
        val bodyStart = start
        while (pos < html.length && depth > 0) {
            val m = divTag.find(html, pos) ?: break
            if (m.groupValues[1].isEmpty()) depth++ else depth--
            pos = m.range.last + 1
        }
        if (depth != 0) return html.substring(bodyStart, minOf(bodyStart + 8000, html.length))
        return html.substring(bodyStart, maxOf(bodyStart, pos - 6))
    }

    /** 清洗单个文本片段：去标签、去 HTML 实体、压缩空白。 */
    private fun clean(raw: String): String {
        var t = ANY_TAG.replace(raw, "")
        t = t.replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'")
        t = WS.replace(t, " ").trim()
        return t.trim(' ', '　', '\u00a0')
    }
}
