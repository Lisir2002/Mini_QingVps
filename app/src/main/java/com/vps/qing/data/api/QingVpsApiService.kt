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
     * 商品列表（分页）。⚠️ 站点 /product 是分页接口，默认 limit=20 —— 不带 limit
     * 只能拿到前 20 个商品，上新/旧款商品会"抓不到"（v1.2.4 修复）。
     * 传大 limit 一次取回；仓库层仍按 count 兜底翻页。站点忽略 fpg_id/spg_id，
     * 分类在本地过滤（CatalogParser.byGroup）。
     */
    @GET("product")
    suspend fun allProducts(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): ApiEnvelope

    // ---- 账号与服务（v1.3.0 原生化，不再跳网页）----
    // ⚠️ 站点列表接口默认 limit=20，全部显式传大 limit，避免重蹈 /product 分页遗漏的坑。
    // hosts/orders/transactions 的端点定义在下方「仪表盘」区块（v1.0 已有），limit 已提升到 100。

    @GET("ticket")
    suspend fun tickets(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("keywords") keywords: String? = null,
        @Query("ticket_type_id") ticketTypeId: String? = null
    ): ApiEnvelope

    @GET("sub_account")
    suspend fun subAccounts(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("keywords") keywords: String? = null
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
    suspend fun orders(@Query("page") page: Int = 1, @Query("limit") limit: Int = 100): ApiEnvelope

    @GET("host")
    suspend fun hosts(@Query("page") page: Int = 1, @Query("limit") limit: Int = 100): ApiEnvelope

    @GET("transaction")
    suspend fun transactions(@Query("page") page: Int = 1, @Query("limit") limit: Int = 100): ApiEnvelope

    @GET("log")
    suspend fun logs(@Query("page") page: Int = 1, @Query("limit") limit: Int = 20): ApiEnvelope

    @GET("client_care/mail/list")
    suspend fun messages(@Query("page") page: Int = 1, @Query("limit") limit: Int = 20): ApiEnvelope

    // ---- v1.4.0：订单详情 + 实名认证 ----

    /** 订单详情：data.order 含 items[] 明细（每项配置、关联主机、金额）。 */
    @GET("order/{id}")
    suspend fun orderDetail(@Path("id") id: String): ApiEnvelope

    /** 实名认证信息（开关 + person/company 认证资料，卡号姓名已脱敏）。 */
    @GET("certification/info")
    suspend fun certificationInfo(): ApiEnvelope

    /** 实名认证方式列表（如「人脸实名认证」）。 */
    @GET("certification/plugin")
    suspend fun certificationPlugins(): ApiEnvelope

    // ---- v1.4.5：实名认证提交与进度（协议取自魔方官方插件 idcsmart_certification 路由表）----

    /** 自定义字段：data.custom_fields[]（title/field/type 等），按插件与类型查询。 */
    @GET("certification/custom_fields")
    suspend fun certificationCustomFields(
        @Query("name") name: String,
        @Query("type") type: String
    ): ApiEnvelope

    /** 个人认证提交：plugin_name/card_name/phone/card_type/card_number(+img/custom_fields)。 */
    @POST("certification/person")
    suspend fun certificationPerson(@Body body: JsonObject): ApiEnvelope

    /** 企业认证提交：同上 + company/company_organ_code。 */
    @POST("certification/company")
    suspend fun certificationCompany(@Body body: JsonObject): ApiEnvelope

    /** 认证验证页：200 → data.html（人脸页，内含轮询）；400 → data.code 10000/10001。 */
    @GET("certification/auth")
    suspend fun certificationAuth(): ApiEnvelope

    /** 状态轮询：data{code:1通过/2未通过/3待审核/4已提交, refresh}；仅 code==2&&refresh==0 继续轮询。 */
    @GET("certification/status")
    suspend fun certificationStatus(): ApiEnvelope

    /** 插件收费配置：data{free 免费次数, amount 金额, pay 是否需支付, order 订单}。 */
    @GET("certification/plugin/config")
    suspend fun certificationPluginConfig(
        @Query("name") name: String,
        @Query("type") type: String
    ): ApiEnvelope

    /** 生成认证订单（需支付时先调用）：data.order_id。 */
    @POST("certification/plugin/order")
    suspend fun certificationPluginOrder(@Body body: JsonObject): ApiEnvelope

    // ---- v1.4.2：已购产品详情（只读，展示已订购的配置与支付信息）----

    /** 主机详情：data.host 含支付信息、addition（用户名/密码/端口/地区）。 */
    @GET("host/{id}")
    suspend fun hostDetail(@Path("id") id: String): ApiEnvelope

    /** 主机附加信息：IP、IP 数量、国家/地区。 */
    @GET("host/{id}/specific_info")
    suspend fun hostSpecificInfo(@Path("id") id: String): ApiEnvelope

    /**
     * mf_cloud 实例详情：data 含 cpu/memory/bw/system_disk/image/power_status 等，
     * 即网页产品管理页「配置信息」卡片的数据源（cloud.js cloudDetail）。
     * 非 mf_cloud 模块商品返回 400，仓库层会容忍并降级为只展示支付信息。
     */
    @GET("remf_cloud/{id}")
    suspend fun cloudDetail(@Path("id") id: String): ApiEnvelope

    // ---- v1.4.3：工单中心（插件 idcsmart_ticket）----
    // 端点全部来自站点前端 ticket.js / addTicket.js / ticketDetails.js 实测，非猜测。
    // 插件页面：plugin/27/ticket.htm、addTicket.htm、ticketDetails.htm

    /** 工单统计：data 是 {状态id: 数量} 映射。 */
    @GET("ticket/statistic")
    suspend fun ticketStatistic(): ApiEnvelope

    /** 工单类型：data.list[{id,name}]。 */
    @GET("ticket/type")
    suspend fun ticketTypes(): ApiEnvelope

    /** 工单状态字典：data.list[{id,name,color,status,default}]。 */
    @GET("ticket/status")
    suspend fun ticketStatuses(): ApiEnvelope

    /** 工单部门：data.list[{id,name}]。 */
    @GET("ticket/department")
    suspend fun ticketDepartments(): ApiEnvelope

    /** 工单通知设置：data{ticket_notice_open,ticket_notice_description}。 */
    @GET("ticket/config")
    suspend fun ticketConfig(): ApiEnvelope

    /** 工单详情：data.ticket 含 replies[] 回复时间轴。 */
    @GET("ticket/{id}")
    suspend fun ticketDetail(@Path("id") id: String): ApiEnvelope

    /** 创建工单：body{title,ticket_type_id,host_ids,content,attachment}。 */
    @POST("ticket")
    suspend fun createTicket(@Body body: JsonObject): ApiEnvelope

    /** 回复工单：body{content,attachment,quote_reply_id}。 */
    @POST("ticket/{id}/reply")
    suspend fun replyTicket(@Path("id") id: String, @Body body: JsonObject): ApiEnvelope

    /** 关闭工单。 */
    @PUT("ticket/{id}/close")
    suspend fun closeTicket(@Path("id") id: String, @Body body: JsonObject): ApiEnvelope

    /** 催单（站点限制 15 分钟一次）。 */
    @PUT("ticket/{id}/urge")
    suspend fun urgeTicket(@Path("id") id: String, @Body body: JsonObject): ApiEnvelope

    // ---- v1.4.3：子账户管理（插件 idcsmart_sub_account）----
    // 端点来自站点前端 childAccount.js / addChildAccount.js 实测。
    // 插件页面：plugin/28/childAccount.htm、addChildAccount.htm

    /** 子账户详情：data.account 含基本信息与 auth 权限 id 列表。 */
    @GET("sub_account/{id}")
    suspend fun subAccountDetail(@Path("id") id: String): ApiEnvelope

    /** 创建子账户。 */
    @POST("sub_account")
    suspend fun createSubAccount(@Body body: JsonObject): ApiEnvelope

    /** 编辑子账户。 */
    @PUT("sub_account/{id}")
    suspend fun updateSubAccount(@Path("id") id: String, @Body body: JsonObject): ApiEnvelope

    /** 删除子账户。 */
    @DELETE("sub_account/{id}")
    suspend fun deleteSubAccount(@Path("id") id: String): ApiEnvelope

    /** 启用/停用子账户：body{status:0|1}。 */
    @PUT("sub_account/{id}/status")
    suspend fun updateSubAccountStatus(@Path("id") id: String, @Body body: JsonObject): ApiEnvelope

    /** 子账户权限 id 列表。 */
    @GET("sub_account/{id}/auth")
    suspend fun subAccountAuth(@Path("id") id: String): ApiEnvelope

    /** 权限树（GET /auth）：data.list[{id,title,parent_id,rules,child}]。 */
    /** 注意：/auth 已在上方声明（getAuthRole），此处复用同一方法。 */

    /** 可选模块：data.list[{name,display_name,version}]。 */
    @GET("module")
    suspend fun modules(): ApiEnvelope

    /** 关联产品精简列表：data.list[{id,product_id,product_name,name,status}]。 */
    @GET("host/all")
    suspend fun allHosts(): ApiEnvelope

    /** 项目列表（部分版本未启用，返回 HTML，仓库层容错为空）。 */
    @GET("project")
    suspend fun projects(): ApiEnvelope
}

/** /captcha/tp_captcha/index/refresh 的响应。 */
data class CaptchaRefreshResponse(
    @SerializedName("base64") val base64: String? = null,
    @SerializedName("token") val token: String? = null
)
