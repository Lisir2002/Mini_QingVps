package com.vps.qing.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** 业务 401 总线：MainActivity 监听后清栈跳登录。 */
object UnauthorizedBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    /**
     * 由 AppContainer 注册清理本地 token；放在这里避免 core/network 反向依赖 DI 容器。
     * v1.4.4：业务 401 改由 [com.vps.qing.core.network.safeApiCall] 统一识别，
     * 拦截器不再对每个响应做 peekBody + Gson 反序列化。
     */
    @Volatile
    var onUnauthorized: (() -> Unit)? = null

    fun fire() {
        onUnauthorized?.invoke()
        _events.tryEmit(Unit)
    }
}

/** 保留 sl-session 会话 Cookie（站点响应头带 SameSite=None; Secure; HttpOnly）。 */
class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, List<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isNotEmpty()) store[url.host] = cookies
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host].orEmpty().filter { it.expiresAt > System.currentTimeMillis() }
}

/** 提供当前 JWT。抽成接口便于纯 JVM 单元测试（生产代码由 TokenManager 提供）。 */
fun interface TokenProvider {
    fun current(): String?
}

/** 注入 Bearer / 语言头。业务 401 由 safeApiCall 识别，这里保持零解析开销。 */
class AuthInterceptor(
    private val tokenProvider: TokenProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("language", "zh-cn")
        tokenProvider.current()?.let { builder.header("Authorization", "Bearer $it") }
        return chain.proceed(builder.build())
    }
}

/** HTML 回退兜底：非 JSON 响应直接抛业务异常，避免 Gson 解析崩溃。 */
class ContentTypeGuardInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val mediaType = response.body?.contentType()
        val rawType = mediaType?.toString()?.substringBefore(";")?.trim().orEmpty()
        if (response.isSuccessful && mediaType != null && !rawType.endsWith("/json", true)) {
            throw HtmlFallbackException(
                "接口返回 $rawType，通常是路由回退到 HTML 首页或插件未启用"
            )
        }
        return response
    }
}
