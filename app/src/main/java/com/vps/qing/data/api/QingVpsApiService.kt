package com.vps.qing.data.api

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.vps.qing.core.network.ApiEnvelope
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 站点接口。基址 `https://vps.qingyayun.cn/console/v1/`（见 AppContainer.BASE_API）。
 *
 * 端点与字段来自站点自身前端 JS（api/common.js、login.js、regist.js、forget.js）
 * 与匿名实测，不是猜测。图形验证码的 refresh/verify 不在 /console/v1 下，用 @Url 走绝对地址。
 */
interface QingVpsApiService {

    // ---- 站点配置 / 公共 ----
    @GET("common")
    suspend fun common(): ApiEnvelope

    @GET("country")
    suspend fun countries(): ApiEnvelope

    @GET("menu")
    suspend fun menu(): ApiEnvelope

    // ---- 图形验证码（GET /captcha 返回 data.html 片段）----
    @GET("captcha")
    suspend fun captcha(): ApiEnvelope

    /** 换一张验证码：GET /captcha/tp_captcha/index/refresh → {base64, token} */
    @GET
    suspend fun refreshCaptcha(@Url url: String): CaptchaRefreshResponse

    /** 校验：POST /captcha/tp_captcha/index/verify，表单字段 captcha / token */
    @FormUrlEncoded
    @POST
    suspend fun verifyCaptcha(
        @Url url: String,
        @Field("captcha") captcha: String,
        @Field("token") token: String
    ): ApiEnvelope

    // ---- 短信 / 邮箱验证码 ----
    @POST("phone/code")
    suspend fun phoneCode(@Body body: JsonObject): ApiEnvelope

    @POST("email/code")
    suspend fun emailCode(@Body body: JsonObject): ApiEnvelope

    // ---- 登录 / 注册 ----
    @POST("login")
    suspend fun login(@Body body: JsonObject): ApiEnvelope

    @POST("register")
    suspend fun register(@Body body: JsonObject): ApiEnvelope

    @GET("register/custom_field")
    suspend fun registerCustomFields(): ApiEnvelope

    @POST("account/password_reset")
    suspend fun resetPassword(@Body body: JsonObject): ApiEnvelope

    @POST("logout")
    suspend fun logout(): ApiEnvelope

    // ---- 个人中心 ----
    @GET("account")
    suspend fun account(): ApiEnvelope

    @GET("auth")
    suspend fun auth(): ApiEnvelope

    @GET("index")
    suspend fun index(): ApiEnvelope

    @GET("order")
    suspend fun orders(@Query("page") page: Int = 1, @Query("limit") limit: Int = 20): ApiEnvelope

    @GET("host")
    suspend fun hosts(@Query("page") page: Int = 1, @Query("limit") limit: Int = 20): ApiEnvelope

    @GET("transaction")
    suspend fun transactions(@Query("page") page: Int = 1, @Query("limit") limit: Int = 20): ApiEnvelope

    @GET("log")
    suspend fun logs(@Query("page") page: Int = 1, @Query("limit") limit: Int = 20): ApiEnvelope

    @GET("client_care/mail/list")
    suspend fun messages(@Query("page") page: Int = 1, @Query("limit") limit: Int = 20): ApiEnvelope
}

/** /captcha/tp_captcha/index/refresh 的响应。 */
data class CaptchaRefreshResponse(
    @SerializedName("base64") val base64: String? = null,
    @SerializedName("token") val token: String? = null
)
