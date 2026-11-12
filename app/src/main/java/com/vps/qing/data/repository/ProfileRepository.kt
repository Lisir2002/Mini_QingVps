package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.TokenManager
import com.vps.qing.core.network.objectAt
import com.vps.qing.core.network.safeApiCall
import com.vps.qing.core.network.string
import com.vps.qing.data.api.QingVpsApiService

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

    suspend fun logout(): ApiResult<Unit> {
        val result = safeApiCall({ api.logout() }) { Unit }
        tokens.clear() // 无论服务端结果如何，本地登录态都要清掉
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
            is ApiResult.Error -> result
        }
    }
}
