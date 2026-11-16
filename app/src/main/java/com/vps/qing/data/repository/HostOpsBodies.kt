package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.string

/**
 * 已购机器操作请求体与连接信息格式化（v1.4.22）。
 *
 * 重装接口来自魔方 V10 官方源码 `plugins/reserver/mf_cloud` + `plugins/server/mf_cloud`
 * 的 route.php / CloudController / CloudLogic，站点实测存在（未登录返回 401）。
 * 详见 docs/mofang-v10-promocode-research.md 同目录的操作调研。
 */
object HostOpsBodies {

    /**
     * 重装系统 body。
     * 官方 CloudLogic::reinstall 要求：image_id（镜像 id）、port（端口）必填；
     * password 与 ssh_key_id 二选一，App 统一用密码方式（password_type=0）。
     */
    fun reinstall(imageId: Int, password: String, port: String): JsonObject = JsonObject().apply {
        addProperty("image_id", imageId)
        addProperty("password", password)
        addProperty("port", port.ifBlank { "22" })
    }

    /** 重置密码 body：官方 reset_password 要求 password + port。 */
    fun resetPassword(password: String, port: String): JsonObject = JsonObject().apply {
        addProperty("password", password)
        addProperty("port", port.ifBlank { "22" })
    }

    /**
     * 一键复制「全部连接信息」的文本（SSH 场景）。
     * 按用户要求的五项：IP、机器名、端口、root 账户、密码，逐行展示。
     */
    fun connectionText(
        ip: String,
        name: String,
        port: String,
        username: String,
        password: String
    ): String = buildString {
        appendLine("IP：${ip.ifBlank { "--" }}")
        appendLine("机器名：${name.ifBlank { "--" }}")
        appendLine("端口：${port.ifBlank { "22" }}")
        appendLine("账户：${username.ifBlank { "root" }}")
        append("密码：${password.ifBlank { "--" }}")
    }

    /**
     * SSH 连接命令（如 ssh root@1.2.3.4 -p 22）。IP 为空时返回空串，调用方据此隐藏。
     */
    fun sshCommand(ip: String, port: String, username: String): String {
        if (ip.isBlank()) return ""
        val user = username.ifBlank { "root" }
        val p = port.ifBlank { "22" }
        return if (p == "22") "ssh $user@$ip" else "ssh $user@$ip -p $p"
    }

    /** 解析 GET /remf_cloud/{id}/log 的 data.list[]（字段兼容 name/description/time）。 */
    fun parseLog(data: JsonElement?): List<HostOpLog> =
        data.arrayAt("list")?.mapNotNull { el ->
            val text = el.string("description", "name", "content", "remark")
            if (text.isNullOrBlank()) return@mapNotNull null
            HostOpLog(
                text = text,
                time = el.string("create_time", "time", "add_time", "log_time").orEmpty(),
                ip = el.string("ip").orEmpty()
            )
        }.orEmpty()
}

/** 实例操作日志条目。 */
data class HostOpLog(val text: String, val time: String, val ip: String)
