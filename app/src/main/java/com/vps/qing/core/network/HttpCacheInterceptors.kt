package com.vps.qing.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 网络层拦截器链（v1.4.4）。
 *
 * OkHttp 调用链顺序（决定缓存分层的正确性）：
 * ```
 * Application Interceptors  <- 本文件的拦截器：L1 内存缓存 / 请求改写 / 重试
 * RetryAndFollowUp / Bridge
 * CacheInterceptor          <- L2 磁盘缓存（读策略看请求 Cache-Control，写策略看响应 Cache-Control）
 * Connect
 * Network Interceptors      <- [HttpCacheResponseInterceptor]：改写响应头让站点响应可缓存
 * CallServer
 * ```
 * 站点不返回任何缓存头，因此必须在 network interceptor 里补 `Cache-Control: public`，
 * CacheInterceptor 才会把响应落盘（这一步在 Bridge/Cache 之后、响应回传之前生效）。
 */
private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

private fun syntheticResponse(request: Request, body: String, source: String): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .header("Content-Type", JSON_MEDIA.toString())
        .header("X-QingVPS-Cache", source)
        .body(body.toResponseBody(JSON_MEDIA))
        .build()

/**
 * L1 内存缓存 + 并发去重 + 写操作失效 + 请求侧 Cache-Control 改写。
 * 缓存的是响应体字符串，与业务模型解耦，Repository 完全无需改动。
 */
class ApiCacheInterceptor(
    private val cache: ApiCache,
    private val sessionKey: () -> String,
    private val isOffline: () -> Boolean = { false },
    /** 会话级失效（登录/登出）时回调，用于清空 OkHttp 磁盘缓存。 */
    private val onSessionReset: () -> Unit = {}
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = CachePolicy.relativePath(request.url.encodedPath)
        val rule = CachePolicy.of(request.method, request.url.encodedPath)

        // ---- 写操作：不缓存，成功后失效相关缓存 ----
        if (!rule.cacheable && !request.method.equals("GET", true)) {
            val response = chain.proceed(request.newBuilder().header("Cache-Control", "no-store").build())
            if (response.isSuccessful) {
                val prefixes = CachePolicy.invalidationPrefixes(request.method, request.url.encodedPath)
                prefixes.forEach(cache::invalidate)
                if (prefixes.any { it.isEmpty() }) onSessionReset()
            }
            return response
        }

        // ---- 不缓存的 GET（验证码、算价等）----
        if (!rule.cacheable) {
            // H5 静态资源不进 L1 内存缓存，但保留 OkHttp 磁盘缓存能力
            if (CachePolicy.isStaticAsset(request.url.encodedPath)) {
                return chain.proceed(request)
            }
            return chain.proceed(request.newBuilder().header("Cache-Control", "no-store").build())
        }

        val key = cache.key(request.method, request.url.toString(), sessionKey(), path.orEmpty())

        // ---- 1. 新鲜内存缓存：完全不发网络 ----
        cache.fresh(key)?.let { return syntheticResponse(request, it, "L1-memory") }

        // ---- 2. in-flight 去重：同一 URL 的并发请求只放一次网络 ----
        val (deferred, joined) = cache.joinOrCreate(key)
        if (joined) {
            val shared = try {
                runBlocking { withTimeout(WAIT_TIMEOUT_MS) { deferred.await() } }
            } catch (e: Exception) {
                cache.stale(key)?.let { return syntheticResponse(request, it, "L1-stale") }
                throw IOException("共享请求失败", e)
            }
            return syntheticResponse(request, shared, "L1-dedup")
        }

        // ---- 3. 真正发起请求：请求侧 Cache-Control 让 OkHttp 磁盘缓存生效 ----
        val outbound = request.newBuilder()
        when {
            rule.scope != CacheScope.PUBLIC ->
                outbound.header("Cache-Control", "no-store")
            isOffline() ->
                outbound.cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(7, TimeUnit.DAYS)
                        .build()
                )
            else ->
                outbound.header("Cache-Control", "max-age=${rule.seconds}")
        }

        val response = try {
            chain.proceed(outbound.build())
        } catch (e: Exception) {
            cache.fail(key, deferred, e)
            cache.stale(key)?.let { return syntheticResponse(request, it, "L1-stale") }
            throw e
        }

        // 离线且无磁盘缓存：OkHttp 会返回 504，用过期内存副本兜底
        if (response.code == 504 && !response.isSuccessful) {
            val stale = cache.stale(key)
            if (stale != null) {
                cache.succeed(key, deferred, stale)
                response.close()
                return syntheticResponse(request, stale, "L1-stale")
            }
            cache.fail(key, deferred, IOException("离线且无可用缓存"))
            return response
        }

        if (!response.isSuccessful || response.body == null ||
            !isJson(response)
        ) {
            // 失败 / 非 JSON 响应不缓存；等它的人拿到同一个失败结果
            val err = IOException("HTTP ${response.code}，不缓存")
            cache.fail(key, deferred, err)
            return response
        }

        val body = runCatching {
            response.peekBody(cache.maxBodyChars.toLong()).string()
        }.getOrNull()

        // body 读取失败或超大：不能把截断内容共享给等待者
        if (body == null || body.length >= cache.maxBodyChars) {
            cache.fail(key, deferred, IOException("响应体不可用或超出缓存上限"))
            return response
        }

        // 业务失败（如 401）不写入缓存，但仍把真实 body 共享给并发等待者，
        // 让它们走 safeApiCall 的 Failure 分支，而不是拿到一个假的空 200。
        if (ApiCache.businessStatus(body) != 200) {
            cache.succeed(key, deferred, body)
            return response
        }

        cache.put(key, path.orEmpty(), body, rule)
        cache.succeed(key, deferred, body)
        return response
    }

    private fun isJson(response: Response): Boolean {
        val raw = response.body?.contentType()?.toString()?.substringBefore(";")?.trim().orEmpty()
        return raw.isEmpty() || raw.endsWith("/json", true)
    }

    private companion object {
        const val WAIT_TIMEOUT_MS = 35_000L
    }
}

/**
 * L2 磁盘缓存的响应头注入（必须注册为 **network interceptor**）。
 * 站点零缓存头，注入 `public, max-age=N` 后 OkHttp CacheInterceptor 才会落盘；
 * 仅对 PUBLIC 作用域生效，私有数据绝不写磁盘，规避串号与隐私风险。
 */
class HttpCacheResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        // 路由回退的 HTML 首页不落盘，避免把错误页面缓存下来
        val raw = response.body?.contentType()?.toString()?.substringBefore(";")?.trim().orEmpty()
        if (raw.contains("html", true)) return response

        val path = request.url.encodedPath
        val rule = CachePolicy.of(request.method, path)
        val maxAge = when {
            CachePolicy.isStaticAsset(path) -> CachePolicy.STATIC_TTL_SECONDS
            // 私有数据与非 JSON 的 API 响应绝不落盘
            rule.scope == CacheScope.PUBLIC && (raw.isEmpty() || raw.endsWith("/json", true)) ->
                rule.seconds
            else -> return response
        }

        return response.newBuilder()
            .removeHeader("Cache-Control")
            .removeHeader("Pragma")
            .header("Cache-Control", "public, max-age=$maxAge")
            .build()
    }
}

/**
 * WebView 静态资源专用：请求侧声明一天有效期，让 OkHttp 在缓存新鲜时直接命中、
 * 完全不发网络（站点对静态资源同样不返回缓存头）。
 */
class WebAssetCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val outbound = if (CachePolicy.isStaticAsset(request.url.encodedPath)) {
            request.newBuilder().header("Cache-Control", "max-age=${CachePolicy.STATIC_TTL_SECONDS}").build()
        } else request
        return chain.proceed(outbound)
    }
}

/**
 * 幂等 GET 的轻量重试。OkHttp 默认只重试连接失败，这里补上 408/429/5xx。
 * 写操作一律不重试（避免重复下单 / 重复提交工单）。
 */
class IdempotentRetryInterceptor(private val maxRetries: Int = 1) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.method.equals("GET", true)) return chain.proceed(request)

        var attempt = 0
        while (true) {
            try {
                val response = chain.proceed(request)
                if (attempt >= maxRetries || !retryable(response.code)) return response
                response.close()
                attempt++
            } catch (e: IOException) {
                if (attempt >= maxRetries) throw e
                attempt++
                try {
                    Thread.sleep(250L * attempt)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
            }
        }
    }

    private fun retryable(code: Int): Boolean =
        code == 408 || code == 429 || code == 502 || code == 503
}
