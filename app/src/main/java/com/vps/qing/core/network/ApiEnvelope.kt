package com.vps.qing.core.network

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
import java.io.IOException

/**
 * 站点统一响应包装。注意：
 * 1) HTTP 状态码几乎恒为 200，业务状态在 body.status；
 * 2) data 可能是对象 / 数组 / null / 字符串，因此这里用 JsonElement 接收，
 *    由 Repository 按需宽松解析，避免字段未确认时 Gson 直接抛类型转换异常。
 */
data class ApiEnvelope(
    @SerializedName("status") val status: Int = -1,
    @SerializedName("msg") val msg: String = "",
    @SerializedName("data") val data: JsonElement? = null,
    /**
     * v1.4.18：部分接口的业务 code 在**响应体顶层**，与 status/msg 并列，不在 data 里。
     * 官方前端 `GET /pay/{id}/status` 轮询就是读 `res.data.code === "Paid"`；
     * 旧版 ApiEnvelope 没有这个字段，App 只能读 data.code → 永远拿不到 Paid，
     * 于是「我已完成支付」永远检测不到支付成功。
     */
    @SerializedName("code") val code: String = ""
)

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val code: Int, val msg: String, val data: JsonElement? = null) : ApiResult<Nothing>
    data class Error(val throwable: Throwable) : ApiResult<Nothing>
}

/** 接口返回了 HTML（插件未启用 / 方法不匹配 / 路由回退到 SPA 首页）。 */
class HtmlFallbackException(message: String) : IOException(message)

suspend fun <T> safeApiCall(
    block: suspend () -> ApiEnvelope,
    map: suspend (JsonElement?) -> T
): ApiResult<T> = safeApiCallEnvelope(block) { envelope -> map(envelope.data) }

/**
 * v1.4.18：把完整 [ApiEnvelope] 交给解析器的版本，用于顶层 `code` 等业务字段
 * （如支付状态）。与 [safeApiCall] 共用同一套状态判定与异常处理，避免行为分叉。
 */
suspend fun <T> safeApiCallEnvelope(
    block: suspend () -> ApiEnvelope,
    map: suspend (ApiEnvelope) -> T
): ApiResult<T> = try {
    val envelope = block()
    when {
        envelope.status == 200 -> ApiResult.Success(map(envelope))
        // v1.4.4：业务 401 在这里统一识别（此处已持有解析后的 envelope），
        // 不再需要 AuthInterceptor 对每个响应 peekBody + Gson 反序列化。
        envelope.status == 401 -> {
            UnauthorizedBus.fire()
            ApiResult.Failure(envelope.status, envelope.msg, envelope.data)
        }
        else -> ApiResult.Failure(envelope.status, envelope.msg, envelope.data)
    }
} catch (e: HttpException) {
    ApiResult.Failure(e.code(), e.message())
} catch (e: Exception) {
    ApiResult.Error(e)
}

/** 宽松取值：字段缺失、类型不符、JsonNull 都返回 null，不崩。 */
fun JsonElement?.string(vararg keys: String): String? {
    val obj = this?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    for (key in keys) {
        val e = obj.get(key) ?: continue
        if (e.isJsonNull || !e.isJsonPrimitive) continue
        return runCatching { e.asString }.getOrNull()
    }
    return null
}

fun JsonElement?.number(vararg keys: String): Double? {
    val obj = this?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    for (key in keys) {
        val e = obj.get(key) ?: continue
        if (e.isJsonNull || !e.isJsonPrimitive) continue
        return runCatching { e.asDouble }.getOrNull()
    }
    return null
}

fun JsonElement?.objectAt(vararg keys: String): JsonObject? {
    var current: JsonElement? = this
    for (key in keys) {
        current = current?.takeIf { it.isJsonObject }?.asJsonObject?.get(key) ?: return null
    }
    return current?.takeIf { it.isJsonObject }?.asJsonObject
}

fun JsonElement?.arrayAt(vararg keys: String): JsonArray? {
    var current: JsonElement? = this
    for (key in keys) {
        current = current?.takeIf { it.isJsonObject }?.asJsonObject?.get(key) ?: return null
    }
    return current?.takeIf { it.isJsonArray }?.asJsonArray
}

fun JsonObject.toBodyMap(): MutableMap<String, String> =
    entrySet().associateTo(mutableMapOf()) { (k, v) ->
        k to if (v.isJsonPrimitive) v.asString else v.toString()
    }
