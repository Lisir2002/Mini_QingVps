package com.vps.qing.data.api

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.vps.qing.core.network.ApiEnvelope
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 站点接口。基址 `https://vps.qingyayun.cn/console/v1/`（见 AppContainer.BASE_API）。
 *
 * 端点与字段来自站点自身前端 JS（api/common.js、login.js、regist.js、
 * cart/template/pc/wry/api/product.js、api/shopping.js、
 * plugins/reserver/idcsmart_common/.../api/common_product.js、.../js/common_product.js）
 * 与 2026-11 网页端实测，不是猜测。
 */
interface QingVpsApiService {

    // ---- 站点配置 / 公共 ----
    @GET("common")
    suspend fun common(): ApiEnvelope

    @GET("country")
    suspend fun countries(): ApiEnvelope

    @GET("menu")
    suspend fun menu(): ApiEnvelope

    @GET("news/index")
    suspend fun news(): ApiEnvelope

    // ---- 商品与分类（选购页）----
    @GET("product/group/first")
    suspend fun firstGroups(): ApiEnvelope

    @GET("product/group/second")
    suspend fun secondGroups(@Query("id") id: String): ApiEnvelope

    /**
     * 全量商品。站点实测会忽略 fpg_id/spg_id 恒返回全量 20 条，
     * 因此 App 只拉一次全量，分类用 CatalogParser.byGroup 本地过滤。
     * 保留 fpg_id/spg_id 参数仅为与站点前端一致（不影响结果）。
     */
    @GET("product")
    suspend fun allProducts(
        @Query("fpg_id") fpgId: String = "0",
        @Query("spg_id") spgId: String = "0"
    ): ApiEnvelope

    /** 兼容旧调用：按站点参数请求商品（返回仍是全量，由调用方本地过滤）。 */
    @GET("product")
    suspend fun products(
        @Query("fpg_id") fpgId: String,
        @Query("spg_id") spgId: String
    ): ApiEnvelope

    /** 商品详情（pay_type / product_group_id 等）。 */
    @GET("product/{id}")
    suspend fun productDetail(@Path("id") id: String): ApiEnvelope

    /**
     * 通用商品自定义配置：data.configoptions / data.custom_cycles。
     * 套餐型商品（mf_cloud）返回 status=400「商品未关联通用产品模块接口」。
     */
    @GET("reidcsmart_common/product/{id}/configoption")
    suspend fun productConfig(@Path("id") id: String): ApiEnvelope

    /** 实时算价：body 与加购一致，data.price / data.preview / data.billing_cycle。 */
    @POST("product/{id}/config_option")
    suspend fun calcPrice(@Path("id") id: String, @Body body: JsonObject): ApiEnvelope

    /**  mf_cloud 套餐型商品订购页配置（区域/线路/CPU/内存/磁盘）。 */
    @GET("product/{id}/remf_cloud/order_page")
    suspend fun packageOrderPage(@Path("id") id: String): ApiEnvelope

    /** mf_cloud 操作系统两级列表（系统分组 → 镜像）。 */
    @GET("product/{id}/remf_cloud/image")
    suspend fun mfImages(@Path("id") id: String): ApiEnvelope

    /** mf_cloud 线路详情（bill_type + bw/flow 档位）。 */
    @GET("product/{id}/remf_cloud/line/{lineId}")
    suspend fun mfLineDetail(@Path("id") id: String, @Path("lineId") lineId: Int): ApiEnvelope

    /**
     * mf_cloud 周期列表：body = 配置参数平铺 + id。
     * 返回 data[{id,name,price,is_default}]。
     */
    @POST("product/{id}/remf_cloud/duration")
    suspend fun mfDuration(@Path("id") id: String, @Body body: JsonObject): ApiEnvelope

    // ---- 站点云端购物车（匿名 cart_cookie 会话即可，无需登录态）----
    /** 真实加入站点购物车：实测「添加成功」，GET /cart 立即可见。 */
    @POST("cart")
    suspend fun addToCart(@Body body: JsonObject): ApiEnvelope

    /** 站点购物车列表：data.list[]，含 config_options 与 qty。 */
    @GET("cart")
    suspend fun cart(): ApiEnvelope

    /** 修改某一行数量（position = list 下标）。 */
    @PUT("cart/{position}/qty")
    suspend fun updateCartQty(@Path("position") position: Int, @Body body: JsonObject): ApiEnvelope

    /** 删除某一行。 */
    @DELETE("cart/{position}")
    suspend fun deleteCartLine(@Path("position") position: Int): ApiEnvelope

    // ---- 图形验证码 ----
    @GET("captcha")
    suspend fun captcha(): ApiEnvelope

    @GET
    suspend fun refreshCaptcha(@Url url: String): CaptchaRefreshResponse

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
