package com.vps.qing.core.network

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 软件更新（v1.4.14）：对接 GitHub Releases 检查最新版本。
 * 关于页 →「软件更新」点击后调用 [checkLatest]，展示版本对比与更新入口。
 *
 * 采用独立 OkHttpClient（GitHub API 与站点 /console/v1 是不同主机，且无需登录态），
 * 短超时 + 失败返回 null，UI 降级为「打开 GitHub Releases 页面」而不是卡住。
 */
object UpdateChecker {

    const val REPO = "Lisir2002/Mini_QingVps"
    const val RELEASES_URL = "https://github.com/$REPO/releases"
    private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"

    data class ReleaseInfo(
        val tag: String,
        val name: String,
        val body: String,
        val htmlUrl: String,
        val apkUrl: String?,
        val publishedAt: String
    )

    /**
     * 语义版本比较：remote 是否比 local 新。
     * 忽略前缀 v/V 与预发布后缀，按 "." 逐段比较数字，缺段按 0。
     * 例如 isNewerVersion("v1.4.15", "1.4.14")=true；相同版本=false。
     */
    fun isNewerVersion(remote: String, local: String): Boolean {
        fun parts(v: String): List<Int> = v.trim()
            .removePrefix("v").removePrefix("V")
            .substringBefore("-")
            .split(".")
            .map { seg -> seg.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    /** 从 GitHub releases/latest 的 JSON 解析出更新信息（纯函数，便于单测）。 */
    fun parse(json: String): ReleaseInfo? = runCatching {
        val o = JsonParser.parseString(json).asJsonObject
        val tag = o.get("tag_name")?.asString.orEmpty()
        if (tag.isBlank()) {
            null
        } else {
            val apkUrl = o.getAsJsonArray("assets")?.let { arr ->
                arr.firstOrNull { it.asJsonObject.get("name")?.asString?.endsWith(".apk", true) == true }
                    ?.asJsonObject?.get("browser_download_url")?.asString
            }
            ReleaseInfo(
                tag = tag,
                name = o.get("name")?.asString?.takeIf { it.isNotBlank() } ?: tag,
                body = o.get("body")?.asString.orEmpty(),
                htmlUrl = o.get("html_url")?.asString?.takeIf { it.isNotBlank() } ?: RELEASES_URL,
                apkUrl = apkUrl,
                publishedAt = o.get("published_at")?.asString.orEmpty()
            )
        }
    }.getOrNull()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(14, TimeUnit.SECONDS)
            .build()
    }

    /** 拉取最新版本；网络/解析失败返回 null（调用方降级到网页入口）。 */
    suspend fun checkLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(LATEST_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "QingVPS-App")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                parse(resp.body?.string().orEmpty())
            }
        }.getOrNull()
    }
}
