package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.TokenManager
import com.vps.qing.core.network.objectAt
import com.vps.qing.core.network.safeApiCall
import com.vps.qing.core.network.string
import com.vps.qing.data.api.QingVpsApiService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * GET /account 需要登录态，暂无法匿名实测完整响应。字段名参考站点 account.js / topMenu.js
 * 中出现的 `account.username / email / phone / id / language / customfield / oauth`，
 * 解析采用「多候选键 + 全可空」，缺失显示 `--`，不会因站点升级而崩溃。
 */
data class AccountInfo(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val balance: String? = null,
    val creditLimit: String? = null,
    val level: String? = null,
    val raw: JsonElement? = null
)

/** 纯 JVM 解析，便于本地单元测试。 */
object AccountParser {
    fun parse(data: JsonElement?): AccountInfo {
        // data 本身可能就是账号对象，也可能包在 account / client / data 容器里，
        // 先探测容器再回退自身（objectAt() 无参会直接返回自身，必须放最后）。
        val obj = data.objectAt("account") ?: data.objectAt("client")
            ?: data.objectAt("data") ?: data.objectAt()
        return AccountInfo(
            id = obj.string("id", "client_id", "user_id"),
            username = obj.string("username", "name", "nickname", "user"),
            email = obj.string("email", "mail"),
            phone = obj.string("phone", "mobile"),
            balance = obj.string("credit", "balance", "money", "amount"),
            creditLimit = obj.string("credit_limit", "credit_limit_amount"),
            level = obj.string("client_level", "level_name"),
            raw = data
        )
    }
}

class ProfileRepository(
    private val api: QingVpsApiService,
    private val tokens: TokenManager
) {
    suspend fun account(): ApiResult<AccountInfo> =
        safeApiCall({ api.account() }) { data -> AccountParser.parse(data) }

    // ---- 账号与服务（v1.3.0 原生化）----

    suspend fun hosts(): ApiResult<List<HostLine>> =
        safeApiCall({ api.hosts() }) { ServiceParser.hosts(it) }

    suspend fun orders(): ApiResult<List<OrderLine>> =
        safeApiCall({ api.orders() }) { ServiceParser.orders(it) }

    suspend fun transactions(): ApiResult<List<TxnLine>> =
        safeApiCall({ api.transactions() }) { ServiceParser.transactions(it) }

    suspend fun tickets(keywords: String? = null): ApiResult<List<TicketLine>> =
        safeApiCall({ api.tickets(keywords = keywords) }) { ServiceParser.tickets(it) }

    suspend fun subAccounts(keywords: String? = null): ApiResult<List<SubAccountLine>> =
        safeApiCall({ api.subAccounts(keywords = keywords) }) { ServiceParser.subAccounts(it) }

    // ---- v1.4.0：订单详情 + 实名认证 ----

    suspend fun orderDetail(orderId: String): ApiResult<OrderDetail?> =
        safeApiCall({ api.orderDetail(orderId) }) { ServiceParser.orderDetail(it) }

    suspend fun certInfo(): ApiResult<CertInfo?> =
        safeApiCall({ api.certificationInfo() }) { ServiceParser.certInfo(it) }

    suspend fun certPlugins(): ApiResult<List<CertPlugin>> =
        safeApiCall({ api.certificationPlugins() }) { ServiceParser.certPlugins(it) }

    // ---- v1.4.5：实名认证提交与进度 ----

    /** 自定义认证字段（本站实测为空数组，仍保留通用支持）。 */
    suspend fun certCustomFields(name: String, type: String): ApiResult<List<CertCustomField>> =
        safeApiCall({ api.certificationCustomFields(name, type) }) { ServiceParser.certCustomFields(it) }

    /** 插件收费配置（free/amount/pay/order）。 */
    suspend fun certPluginConfig(name: String, type: String): ApiResult<CertPluginConfig> =
        safeApiCall({ api.certificationPluginConfig(name, type) }) { ServiceParser.certPluginConfig(it) }

    /** 个人认证提交；body 字段见官方文档（plugin_name/card_name/phone/card_type/card_number…）。 */
    suspend fun submitPersonCert(body: JsonObject): ApiResult<Unit> =
        safeApiCall({ api.certificationPerson(body) }) { }

    /** 企业认证提交。 */
    suspend fun submitCompanyCert(body: JsonObject): ApiResult<Unit> =
        safeApiCall({ api.certificationCompany(body) }) { }

    /** 认证验证页：200 → data.html（人脸页）；400 → data.code 10000/10001。 */
    suspend fun certAuthPage(): ApiResult<CertAuthPage> =
        safeApiCall({ api.certificationAuth() }) { ServiceParser.certAuthPage(200, it) }

    /**
     * 状态轮询。业务 400 表示无认证信息（未提交），由调用方按 ApiResult.Failure 处理；
     * safeApiCall 会把 envelope.status!=200 归入 Failure，恰好符合官方「跳回选择页」的语义。
     */
    suspend fun certPollStatus(): ApiResult<CertPollStatus> =
        safeApiCall({ api.certificationStatus() }) { ServiceParser.certPollStatus(it) }

    // ---- v1.4.2：已购产品详情（只读）----

    /**
     * 并行请求主机详情、mf_cloud 实例配置、主机附加信息，合并为已订购配置详情页数据。
     * /host/{id} 是主体：失败即整体失败；/remf_cloud/{id} 与 /specific_info 失败只做降级
     * （非 mf_cloud 模块商品本来就会 400），不影响支付信息展示。
     */
    suspend fun productDetail(hostId: String): ApiResult<PurchasedDetail?> = coroutineScope {
        val hostCall = async { safeApiCall({ api.hostDetail(hostId) }) { it } }
        val cloudCall = async { safeApiCall({ api.cloudDetail(hostId) }) { it } }
        val specificCall = async { safeApiCall({ api.hostSpecificInfo(hostId) }) { it } }

        when (val host = hostCall.await()) {
            is ApiResult.Success -> {
                val cloud = (cloudCall.await() as? ApiResult.Success<*>)?.data as? JsonElement
                val specific = (specificCall.await() as? ApiResult.Success<*>)?.data as? JsonElement
                ApiResult.Success(ServiceParser.purchasedDetail(host.data, cloud, specific))
            }
            is ApiResult.Failure -> {
                cloudCall.await()
                specificCall.await()
                ApiResult.Failure(host.code, host.msg, host.data)
            }
            is ApiResult.Error -> {
                cloudCall.await()
                specificCall.await()
                ApiResult.Error(host.throwable)
            }
        }
    }

    // ---- v1.4.3：工单中心（插件 idcsmart_ticket 全量读写）----

    suspend fun ticketStatistic(): ApiResult<TicketStatistic> =
        safeApiCall({ api.ticketStatistic() }) { TicketParser.statistic(it) }

    suspend fun ticketTypes(): ApiResult<List<TicketOption>> =
        safeApiCall({ api.ticketTypes() }) { TicketParser.types(it) }

    suspend fun ticketStatuses(): ApiResult<List<TicketOption>> =
        safeApiCall({ api.ticketStatuses() }) { TicketParser.statuses(it) }

    suspend fun ticketDepartments(): ApiResult<List<TicketOption>> =
        safeApiCall({ api.ticketDepartments() }) { TicketParser.departments(it) }

    suspend fun ticketConfig(): ApiResult<TicketConfigInfo> =
        safeApiCall({ api.ticketConfig() }) { TicketParser.config(it) }

    suspend fun ticketDetail(id: String): ApiResult<TicketDetail?> =
        safeApiCall({ api.ticketDetail(id) }) { TicketParser.detail(it) }

    suspend fun createTicket(body: JsonObject): ApiResult<Unit> =
        safeApiCall({ api.createTicket(body) }) { Unit }

    suspend fun replyTicket(id: String, body: JsonObject): ApiResult<Unit> =
        safeApiCall({ api.replyTicket(id, body) }) { Unit }

    suspend fun closeTicket(id: String): ApiResult<Unit> =
        safeApiCall({ api.closeTicket(id, TicketBodies.action(id)) }) { Unit }

    suspend fun urgeTicket(id: String): ApiResult<Unit> =
        safeApiCall({ api.urgeTicket(id, TicketBodies.action(id)) }) { Unit }

    // ---- v1.4.3：子账户管理（插件 idcsmart_sub_account 全量读写）----

    suspend fun subAccountDetail(id: String): ApiResult<SubAccountDetail?> =
        safeApiCall({ api.subAccountDetail(id) }) { SubAccountParser.detail(it) }

    suspend fun subAccountAuth(id: String): ApiResult<JsonElement?> =
        safeApiCall({ api.subAccountAuth(id) }) { it }

    /** 权限树（GET /auth）。 */
    suspend fun authTree(): ApiResult<List<AuthNode>> =
        safeApiCall({ api.auth() }) { SubAccountParser.auth(it) }

    suspend fun modules(): ApiResult<List<ModuleOption>> =
        safeApiCall({ api.modules() }) { SubAccountParser.modules(it) }

    suspend fun allHosts(): ApiResult<List<HostOption>> =
        safeApiCall({ api.allHosts() }) { SubAccountParser.hosts(it) }

    /** 国家区号（/country 结构可能因版本不同，解析失败降级空列表）。 */
    suspend fun countryCodes(): ApiResult<List<CountryOption>> =
        safeApiCall({ api.countries() }) { SubAccountParser.countries(it) }

    suspend fun createSubAccount(body: JsonObject): ApiResult<Unit> =
        safeApiCall({ api.createSubAccount(body) }) { Unit }

    suspend fun updateSubAccount(id: String, body: JsonObject): ApiResult<Unit> =
        safeApiCall({ api.updateSubAccount(id, body) }) { Unit }

    suspend fun deleteSubAccount(id: String): ApiResult<Unit> =
        safeApiCall({ api.deleteSubAccount(id) }) { Unit }

    suspend fun updateSubAccountStatus(id: String, enabled: Boolean): ApiResult<Unit> =
        safeApiCall({ api.updateSubAccountStatus(id, SubAccountBodies.status(enabled)) }) { Unit }

    suspend fun logout(): ApiResult<Unit> {
        val result = safeApiCall({ api.logout() }) { Unit }
        tokens.clear() // 无论服务端结果如何，本地登录态都要清掉
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
            is ApiResult.Error -> result
        }
    }

    // ---- v1.4.12：安全中心可编辑项 ----

    /**
     * 发送安全验证码：verify=验证原绑定，update=验证新绑定。
     * v1.4.13：站点 captcha_client_verify / captcha_client_update=1，
     * 必须把图形验证码校验通过后的 (captcha, token) 一并提交，否则 400「请输入图形验证码」。
     */
    suspend fun sendSecurityCode(
        target: String,
        action: String,
        value: String,
        phoneCode: String = "86",
        captcha: String = "",
        captchaToken: String = ""
    ): ApiResult<Unit> {
        val body = SecurityBodies.code(target, action, value, phoneCode, captcha, captchaToken)
        return safeApiCall({
            if (target == "phone") api.phoneCode(body) else api.emailCode(body)
        }) { Unit }
    }

    /** 验证原绑定（邮箱 / 手机）的验证码。 */
    suspend fun verifyOldContact(target: String, code: String): ApiResult<Unit> {
        val body = JsonObject().apply { addProperty("code", code) }
        return safeApiCall({
            if (target == "phone") api.verifyAccountOldPhone(body) else api.verifyAccountOldEmail(body)
        }) { Unit }
    }

    /** 提交新的邮箱 / 手机。 */
    suspend fun updateContact(
        target: String,
        value: String,
        code: String,
        phoneCode: String = "86"
    ): ApiResult<Unit> {
        val body = JsonObject().apply {
            if (target == "phone") {
                addProperty("phone_code", phoneCode)
                addProperty("phone", value)
            } else {
                addProperty("email", value)
            }
            addProperty("code", code)
        }
        return safeApiCall({
            if (target == "phone") api.updateAccountPhone(body) else api.updateAccountEmail(body)
        }) { Unit }
    }

    /** 修改登录密码。 */
    suspend fun updatePassword(old: String, new: String, repeat: String): ApiResult<Unit> {
        val body = JsonObject().apply {
            addProperty("old_password", old)
            addProperty("new_password", new)
            addProperty("repassword", repeat)
        }
        return safeApiCall({ api.updateAccountPassword(body) }) { Unit }
    }
}
