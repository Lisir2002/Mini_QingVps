package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.vps.qing.core.network.string

/**
 * v1.4.21 独立优惠码中心。
 *
 * 背景（见 docs/mofang-v10-promocode-research.md）：魔方 V10 的优惠码是官方插件
 * `addon/promo_code`，C 端只有「提交码 → 校验抵扣」接口（POST /promo_code/apply），
 * **没有**「列出全站码 + 每个码对应专区」的接口。商家把码写进专区公告 HTML 文案
 * （如 `cart/goodsList.htm?fpg_id=X&spg_id=Y` 的「产品区公告」）对外公布。
 *
 * 所以中心的数据分两层：
 *  - 发现层：解析各专区 SSR 公告 HTML，提取码 + 专区（[PromoParser.fromZoneHtml]）；
 *  - 校验层：用户选中码后调官方 [apply]，以服务端返回的 discount 为准。
 */
data class PromoCodeItem(
    /** 优惠码字符串（如 Qwy202652）。 */
    val code: String,
    /** 公告里描述的折扣（如「9折」），仅作展示，可信度以服务端校验为准。 */
    val discountText: String = "",
    /** 使用限制说明（如「一个账号只能用一次，长期有效」）。 */
    val note: String = "",
    /** 该码所在专区名（来自公告标题，如「国内NAT云-1区」）。 */
    val zoneName: String = "",
    /** 专区二级分组 id（spg_id），用于「去选购」直达。 */
    val spgId: String = "",
    /** 专区一级分组 id（fpg_id）。 */
    val fpgId: String = "",
    /** 公告发布日期。 */
    val date: String = "",
    /** 来源页面 URL（调试/「查看详情」用）。 */
    val sourceUrl: String = ""
)

/** 官方 POST /promo_code/apply 的校验结果。 */
data class PromoApplyResult(
    /** 服务端返回的抵扣金额（元）。0 表示不可用或零折扣。 */
    val discount: Double = 0.0,
    /** 服务端原始消息（失败时是本地化错误码，如 addon_promo_code_has_expired）。 */
    val msg: String = ""
) {
    val usable: Boolean get() = discount > 0.0
}

/** 发现的码 + 校验态（UI 列表项）。 */
data class PromoDiscovered(
    val item: PromoCodeItem,
    val applying: Boolean = false,
    val applyResult: PromoApplyResult? = null
)

/**
 * 纯 JVM 解析器：从专区 `goodsList.htm` 的 SSR HTML 提取公告中的优惠码。
 * 全部用正则 + 文本清洗，无 Android 依赖，便于单测。
 */
object PromoParser {

    // 公告块：<!-- 产品区公告 --> ... <!-- 产品区公告End -->（PC 与移动各一份，内容重复，按 code 去重）
    private val NOTICE_BLOCK = Regex(
        """产品区公告\s*-->([\s\S]*?)产品区公告End""",
        RegexOption.IGNORE_CASE
    )
    private val ANY_TAG = Regex("""<[^>]+>""")
    private val BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val WS = Regex("""\s+""")

    // 码 token：字母开头，含至少一个数字，6~18 位 [A-Za-z0-9]。如 Qwy202652 / QWY202652。
    private val CODE_TOKEN = Regex("""\b[A-Za-z][A-Za-z0-9]{4,16}\d[A-Za-z0-9]*\b""")

    /** 需要避免被误识别为码的高频英文词（公告里的普通文案）。 */
    private val STOP_WORDS = setOf(
        "https", "http", "www", "com", "cn", "net", "html", "php", "class", "div",
        "text", "break", "flex", "min", "shrink", "bg", "border", "font", "bold",
        "items", "justify", "rounded", "white", "gray", "blue", "outline", "none"
    )

    /**
     * 解析单个专区页面 HTML。
     * @param html goodsList.htm 的完整 HTML
     * @param fpgId 一级分组 id
     * @param spgId 二级分组 id
     * @param sourceUrl 来源 URL
     */
    fun fromZoneHtml(html: String, fpgId: String, spgId: String, sourceUrl: String): List<PromoCodeItem> {
        if (html.isBlank()) return emptyList()
        val out = LinkedHashMap<String, PromoCodeItem>()
        NOTICE_BLOCK.findAll(html).forEach { match ->
            val block = match.groupValues.getOrNull(1).orEmpty()
            val plain = clean(block)
            if (!plain.contains("优惠", true)) return@forEach
            val zoneName = zoneNameOf(block)
            val date = dateOf(plain)
            // 逐行找含「优惠码」的句子，从该句里抽取码与折扣描述
            plain.split(Regex("""[。；;]""")).map { it.trim() }.filter { it.isNotEmpty() }
                .filter { it.contains("码", true) || it.contains("优惠", true) }
                .forEach { sentence ->
                    val code = codeOf(sentence) ?: return@forEach
                    val discount = discountOf(sentence)
                    val note = sentence
                        .replace(code, "")
                        .replace(discount, "")
                        .trim(' ', '：', ':', '，', ',', '。')
                    // 去重：同一码在不同专区出现时保留首次（PC/移动重复块也在此合并）
                    if (!out.containsKey(code)) {
                        out[code] = PromoCodeItem(
                            code = code,
                            discountText = discount,
                            note = note.take(80),
                            zoneName = zoneName,
                            spgId = spgId,
                            fpgId = fpgId,
                            date = date,
                            sourceUrl = sourceUrl
                        )
                    }
                }
        }
        return out.values.toList()
    }

    /** 从公告块提取专区名：取 <h2> 文本，去掉「▪ 公告」及前后缀。 */
    private fun zoneNameOf(block: String): String {
        val h2 = Regex("""<h2[^>]*>([\s\S]*?)</h2>""", RegexOption.IGNORE_CASE)
            .find(block)?.groupValues?.getOrNull(1).orEmpty()
        var name = clean(h2)
        name = name.replace(Regex("""^\s*[▪·•\-—]+\s*"""), "")
        name = name.replace(Regex("""\s*[▪·•]\s*公告\s*$"""), "")
        name = name.replace(Regex("""\s*公告\s*$"""), "")
        return name.trim().take(40)
    }

    /** 从句子里抽取优惠码：优先「优惠码：XXX / 优惠码 XXX」模式，退而取首个合法 token。 */
    private fun codeOf(sentence: String): String? {
        val labeled = Regex("""(?:优惠码|折扣码|promo\s*code)\s*[:：是为]?\s*([A-Za-z][A-Za-z0-9]{4,16})""", RegexOption.IGNORE_CASE)
            .find(sentence)?.groupValues?.getOrNull(1)
        val candidates = labeled?.let { listOf(it) } ?: CODE_TOKEN.findAll(sentence).map { it.value }.toList()
        return candidates.firstOrNull { valid(it) }
    }

    private fun valid(token: String): Boolean {
        if (token.length < 6 || token.length > 18) return false
        if (token.lowercase() in STOP_WORDS) return false
        if (!token.any { it.isDigit() }) return false
        if (!token.any { it.isLetter() }) return false
        // 标准魔方码同时含大小写字母+数字（Qwy202652）；放宽为「含字母+数字」即可覆盖老码
        return true
    }

    /** 折扣描述：9折 / 8.5折 / 立减XX 等。 */
    private fun discountOf(sentence: String): String {
        Regex("""(\d+(?:\.\d+)?\s*折)""").find(sentence)?.let { return it.groupValues[1] }
        Regex("""(立减\s*\d+(?:\.\d+)?\s*元?)""").find(sentence)?.let { return it.groupValues[1] }
        return ""
    }

    /** 从公告块文本提取日期（2026-11-15）。 */
    private fun dateOf(plain: String): String =
        Regex("""\b(20\d{2}[-/]\d{1,2}[-/]\d{1,2})\b""").find(plain)?.groupValues?.getOrNull(1).orEmpty()

    /** HTML → 纯文本，块级标签换行，压缩空白。 */
    private fun clean(html: String): String = html
        .replace(BR, " ")
        .replace(Regex("""</(p|div|h[1-6]|li|tr)\s*>""", RegexOption.IGNORE_CASE), " ")
        .replace(ANY_TAG, " ")
        .let { WebEntities.decode(it) }
        .let { WS.replace(it, " ").trim() }

    /** 常见 HTML 实体解码（纯 JVM，避免依赖 Android 的 Html 类）。 */
    private object WebEntities {
        fun decode(s: String): String = s
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("""&#(\d+);""")) { m ->
                runCatching { m.groupValues[1].toInt().toChar().toString() }.getOrDefault(m.value)
            }
    }

    /** 解析官方 promo_code/apply 响应 data{...}。 */
    fun applyResult(data: JsonElement?, status: Int, msg: String): PromoApplyResult {
        val discount = data?.let {
            it.string("discount")?.toDoubleOrNull()
                ?: runCatching { if (it.isJsonPrimitive) it.asDouble else null }.getOrNull()
        } ?: 0.0
        return PromoApplyResult(discount = discount, msg = if (status == 200) msg else msg)
    }
}
