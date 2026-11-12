package com.vps.qing.core

import android.content.Context
import android.net.Uri
import com.vps.qing.core.network.ApiEnvelope
import com.vps.qing.core.network.AuthInterceptor
import com.vps.qing.core.network.ContentTypeGuardInterceptor
import com.vps.qing.core.network.MemoryCookieJar
import com.vps.qing.core.network.TokenManager
import com.vps.qing.core.network.TokenProvider
import com.vps.qing.core.network.UnauthorizedBus
import com.vps.qing.data.api.QingVpsApiService
import com.vps.qing.data.repository.AuthRepository
import com.vps.qing.data.repository.ProfileRepository
import com.vps.qing.data.repository.SiteConfigRepository
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 手动 DI 容器。骨架阶段只在这里组装依赖，接 Hilt 时替换为 @Module 即可。
 *
 * 拦截器顺序：Auth（请求加头 + 业务 401 识别）→ ContentTypeGuard（HTML 回退拦截）→ Logging。
 */
object AppContainer {

    const val SITE_ORIGIN = "https://vps.qingyayun.cn/"
    const val BASE_API = "https://vps.qingyayun.cn/console/v1/"

    /** 图形验证码的刷新/校验不在 /console/v1 下，走站点根路径。 */
    const val CAPTCHA_REFRESH_URL = SITE_ORIGIN + "captcha/tp_captcha/index/refresh"
    const val CAPTCHA_VERIFY_URL = SITE_ORIGIN + "captcha/tp_captcha/index/verify"

    lateinit var tokenManager: TokenManager
        private set

    fun init(context: Context) {
        if (::tokenManager.isInitialized) return
        tokenManager = TokenManager(context.applicationContext)
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(MemoryCookieJar())
            .addInterceptor(
                AuthInterceptor(
                    tokenProvider = TokenProvider { tokenManager.cachedToken },
                    onUnauthorized = {
                        tokenManager.clearImmediate()
                        UnauthorizedBus.fire()
                    }
                )
            )
            .addInterceptor(ContentTypeGuardInterceptor())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // 调试时改成 BODY；默认 BASIC 不打印 token 与请求体
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val api: QingVpsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_API)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(QingVpsApiService::class.java)
    }

    val authRepository: AuthRepository by lazy { AuthRepository(api, tokenManager) }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(api, tokenManager) }
    val siteConfigRepository: SiteConfigRepository by lazy { SiteConfigRepository(api) }

    fun isLoggedIn(): Boolean = !tokenManager.cachedToken.isNullOrBlank()

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
