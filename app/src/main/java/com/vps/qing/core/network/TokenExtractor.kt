package com.vps.qing.core.network

import com.google.gson.JsonElement

/**
 * 从登录 / 注册响应中提取 JWT。
 *
 * 站点前端 login.js / regist.js 明确写的是 `localStorage.setItem("jwt", res.data.data.jwt)`，
 * 即响应体 `data.jwt`，已确认。这里仍保留常见候选名与一层递归，
 * 只为兼容站点未来升级（10.7.2 → 更高版本）时字段名变化。
 */
object TokenExtractor {

    private val CANDIDATES = listOf(
        "jwt", "token", "access_token", "accessToken", "id_token",
        "login_token", "authorization", "Authorization", "api_token"
    )
    private val NESTED = listOf("data", "user", "client", "info", "result")

    fun extract(data: JsonElement?, depth: Int = 0): String? {
        if (data == null || data.isJsonNull || depth > 3) return null

        if (data.isJsonPrimitive) {
            return runCatching { data.asString }
                .getOrNull()
                ?.removePrefix("Bearer ")
                ?.takeIf { it.length > 20 }
        }
        if (!data.isJsonObject) return null

        val obj = data.asJsonObject
        for (key in CANDIDATES) {
            val value = obj.get(key) ?: continue
            if (value.isJsonNull || !value.isJsonPrimitive) continue
            val candidate = runCatching { value.asString }.getOrNull()
            if (!candidate.isNullOrBlank() && candidate.length > 20) {
                return candidate.removePrefix("Bearer ")
            }
        }
        for (key in NESTED) {
            extract(obj.get(key), depth + 1)?.let { return it }
        }
        return null
    }
}
