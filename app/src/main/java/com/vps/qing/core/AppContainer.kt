package com.vps.qing.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.google.gson.Gson
import com.vps.qing.core.network.ApiCache
import com.vps.qing.core.network.ApiCacheInterceptor
import com.vps.qing.core.network.ApiEnvelope
import com.vps.qing.core.network.AuthInterceptor
import com.vps.qing.core.network.ContentTypeGuardInterceptor
import com.vps.qing.core.network.HttpCacheResponseInterceptor
import com.vps.qing.core.network.IdempotentRetryInterceptor
import com.vps.qing.core.network.MemoryCookieJar
import com.vps.qing.core.network.TokenManager
import com.vps.qing.core.network.TokenProvider
import com.vps.qing.core.network.UnauthorizedBus
import com.vps.qing.core.network.WebAssetCacheInterceptor
import com.vps.qing.data.api.QingVpsApiService
import com.vps.qing.data.repository.AuthRepository
import com.vps.qing.data.repository.CatalogRepository
import com.vps.qing.data.repository.ProfileRepository
import com.vps.qing.data.repository.SiteConfigRepository
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 手动 DI 容器。骨架阶段只在这里组装依赖，接 Hilt 时替换为 @Module 即可。
 *
 * v1.4.4 网络层分层：
 * ```
 * 重试(幂等 GET) → Auth 加头 → L1 内存缓存/去重/失效 + 请求 Cache-Control → 类型守卫 → 日志
 *      → [OkHttp CacheInterceptor: L2 磁盘缓存] → 连接池(HTTP/2) → 响应头注入 → 服务端
 * ```
 * 站点零缓存头、条件请求无效且 TTFB 约 1.6s，所以「少发请求」是首要优化目标。
 */
object AppContainer {

    const val SITE_ORIGIN = "https://vps.qingyayun.cn/"
    const val BASE_API = "https://vps.qingyayun.cn/console/v1/"

    /** 站点购物车页，实测 goodsList.htm 里的链接就是这个路径，结算走 WebView 兜底。 */
    const val CART_WEB_PATH = "cart/shoppingCar.htm"

    /** 图形验证码的刷新/校验不在 /console/v1 下，走站点根路径。 */
    const val CAPTCHA_REFRESH_URL = SITE_ORIGIN + "captcha/tp_captcha/index/refresh"
    const val CAPTCHA_VERIFY_URL = SITE_ORIGIN + "captcha/tp_captcha/index/verify"

    private lateinit var appContext: Context

    lateinit var tokenManager: TokenManager
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        if (::tokenManager.isInitialized) return
        tokenManager = TokenManager(appContext)
        // 业务 401 时清掉本地 token（由 safeApiCall 统一触发），同时清内存缓存
        UnauthorizedBus.onUnauthorized = {
            tokenManager.clearImmediate()
            apiCache.clear()
        }
    }

    /** debug 构建才打网络日志；用 FLAG_DEBUGGABLE 判断，无需开启 buildConfig 生成。 */
    private val isDebuggable: Boolean
        get() = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** 复用同一个 Gson：内部缓存 TypeAdapter，避免重复构建反射元数据。 */
    private val gson: Gson by lazy { Gson() }

    /** L2 磁盘缓存：只存 PUBLIC 数据，8MB LRU，跨冷启动复用。 */
    private val httpCache: Cache by lazy {
        Cache(File(appContext.cacheDir, "qingvps_http"), 8L * 1024 * 1024)
    }

    /** L1 内存缓存：所有只读接口共享，按会话隔离。 */
    val apiCache: ApiCache by lazy { ApiCache() }

    /** 会话指纹：不把明文 JWT 放进缓存 key，切换账号后 key 必然不同。 */
    private fun sessionKey(): String {
        val t = tokenManager.cachedToken ?: return "anon"
        return "s${t.length}-${t.hashCode().toString(36)}"
    }

    private fun isOffline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** API 与 WebView 共用连接池，HTTP/2 下同一条 TLS 连接可承载多路请求。 */
    private val connectionPool: ConnectionPool by lazy {
        ConnectionPool(8, 5, TimeUnit.MINUTES)
    }

    private val dispatcher: Dispatcher by lazy {
        Dispatcher().apply {
            // 站点 TTFB 高，允许足够并发做首屏并行预取；同时限制单主机突发数量避免打爆服务端
            maxRequests = 48
            maxRequestsPerHost = 12
        }
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(httpCache)
            .cookieJar(MemoryCookieJar())
            .connectionPool(connectionPool)
            .dispatcher(dispatcher)
            // 站点支持 HTTP/2，多路复用让并发请求共用一条 TLS 连接，省掉重复握手
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(IdempotentRetryInterceptor(maxRetries = 1))
            .addInterceptor(AuthInterceptor(TokenProvider { tokenManager.cachedToken }))
            .addInterceptor(
                ApiCacheInterceptor(
                    cache = apiCache,
                    sessionKey = ::sessionKey,
                    isOffline = ::isOffline,
                    onSessionReset = { httpCache.evictAll() }
                )
            )
            .addInterceptor(ContentTypeGuardInterceptor())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // release 不打日志，避免生产环境无谓的字符串拼接与 I/O
                    level = if (isDebuggable) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
                }
            )
            // 站点不返回 Cache-Control，必须在 network 层补头，OkHttp Cache 才会落盘
            .addNetworkInterceptor(HttpCacheResponseInterceptor())
            .build()
    }

    /**
     * WebView 静态资源专用客户端：复用同一个连接池与磁盘缓存，
     * 但不带业务拦截器（Auth / L1 / 类型守卫），避免给 JS/CSS 请求塞 JSON 头。
     */
    val webHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(httpCache)
            .connectionPool(connectionPool)
            .dispatcher(dispatcher)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(WebAssetCacheInterceptor())
            .addNetworkInterceptor(HttpCacheResponseInterceptor())
            .build()
    }

    val api: QingVpsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_API)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(QingVpsApiService::class.java)
    }

    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val authRepository: AuthRepository by lazy { AuthRepository(api, tokenManager) }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(api, tokenManager) }
    val siteConfigRepository: SiteConfigRepository by lazy { SiteConfigRepository(api) }
    val catalogRepository: CatalogRepository by lazy { CatalogRepository(api) }

    fun isLoggedIn(): Boolean = !tokenManager.cachedToken.isNullOrBlank()

    /**
     * 供下拉刷新/手动刷新使用：绕过 L1 与 L2 强制回源。
     * prefix 非空时按 URL 精确清理磁盘缓存，避免为了刷新一个页面把整盘缓存都清掉。
     */
    fun invalidateCache(prefix: String = "") {
        apiCache.invalidate(prefix)
        if (prefix.isEmpty()) {
            httpCache.evictAll()
        } else {
            runCatching {
                val urls = httpCache.urls()
                while (urls.hasNext()) {
                    if (urls.next().contains("/console/v1/$prefix")) urls.remove()
                }
            }
        }
    }

    /**
     * WebView 兜底地址：站点 request.js 识别 ?queryParam=<jwt> 后会自动写入本地存储，
     * 因此 H5 页面无需单独维护会话。
     */
    fun webUrl(path: String): String {
        val base = SITE_ORIGIN + path.trimStart('/')
        val token = tokenManager.cachedToken
        if (token.isNullOrBlank()) return base
        val separator = if (base.contains("?")) "&" else "?"
        return base + separator + "queryParam=" + Uri.encode(token)
    }

    /** 匿名接口冒烟测试用：确认站点还返回 JSON 而不是 HTML 首页。 */
    suspend fun smoke(): Boolean = try {
        val envelope: ApiEnvelope = api.common()
        envelope.status == 200
    } catch (e: Exception) {
        false
    }
}
