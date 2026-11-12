package com.vps.qing.data.repository

/**
 * GET /console/v1/captcha 返回的 `data.html` 是一段自包含 HTML 片段：
 * - `<img id="captcha-img" src="data:image/png;base64,....">`
 * - `var tokenTag = 'xxxxxxxx'`
 * 站点自己用 jQuery 调 `/captcha/tp_captcha/index/verify` 校验，成功后回传 (captcha, token)。
 * App 走原生直连，因此这里把图片与 token 解析出来，由 Compose 直接渲染。
 *
 * 纯 JVM，无 Android 依赖，便于本地单元测试。
 */
data class CaptchaChallenge(val token: String, val imageBase64: String)

/** 图形验证码校验通过后，随业务请求一起提交的凭证。 */
data class CaptchaTicket(val captcha: String, val token: String)

object CaptchaParser {

    private val TOKEN = Regex("""tokenTag\s*=\s*['"]([^'"]+)['"]""")
    private val IMAGE = Regex("""src\s*=\s*['"]data:image/[^;]+;base64,([^'"]+)['"]""")

    /** 从 HTML 片段中提取 token 与图片 base64；任一缺失返回 null。 */
    fun parseHtml(html: String): CaptchaChallenge? {
        val token = TOKEN.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: return null
        val image = IMAGE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: return null
        return CaptchaChallenge(token, image)
    }

    /** refresh 接口返回的 `data:image/png;base64,....` 去掉前缀。 */
    fun stripDataUri(value: String?): String? =
        value?.substringAfter("base64,", "")?.takeIf { it.isNotBlank() }
}
