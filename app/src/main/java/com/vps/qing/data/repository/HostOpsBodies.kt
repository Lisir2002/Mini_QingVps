package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.number
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

    // ---- v1.4.23：统计图表 / 磁盘 / 网络 / 备份快照 解析 ----
    // 字段依据：魔方官方 CloudController.php @return 注释 + cloudDetail.js 实际解析。

    /**
     * 解析 GET remf_cloud/{id}/chart 的 data.list[]。
     * 四类 type 字段不同：cpu=value；bw=in_bw/out_bw；
     * disk_io=read_bytes/write_bytes/read_iops/write_iops；memory=total/used（字节）。
     */
    fun parseChart(data: JsonElement?): List<ChartPoint> =
        data.arrayAt("list")?.mapNotNull { el ->
            val time = el.number("time")?.toLong() ?: return@mapNotNull null
            ChartPoint(
                time = time,
                value = el.number("value"),
                inBw = el.number("in_bw"),
                outBw = el.number("out_bw"),
                readBytes = el.number("read_bytes"),
                writeBytes = el.number("write_bytes"),
                readIops = el.number("read_iops"),
                writeIops = el.number("write_iops"),
                total = el.number("total"),
                used = el.number("used")
            )
        }.orEmpty()

    /** 解析 GET remf_cloud/{id}/flow：total/used/leave/reset_flow_date。 */
    fun parseFlow(data: JsonElement?) = FlowInfo(
        total = data.string("total", "total_flow").orEmpty(),
        used = data.string("used", "used_flow").orEmpty(),
        leave = data.string("leave", "leave_flow", "remain").orEmpty(),
        resetDate = data.string("reset_flow_date", "reset_date").orEmpty()
    )

    /** 解析 GET remf_cloud/{id}/real_data。 */
    fun parseRealData(data: JsonElement?) = RealData(
        cpuUsage = data.string("cpu_usage").orEmpty(),
        memoryTotal = data.string("memory_total").orEmpty(),
        memoryUsable = data.string("memory_usable").orEmpty(),
        memoryUsage = data.string("memory_usage").orEmpty()
    )

    /** 解析 GET remf_cloud/{id}/disk 的 data.list[]。 */
    fun parseDisks(data: JsonElement?): List<DiskInfo> =
        data.arrayAt("list")?.mapNotNull { el ->
            val name = el.string("name").orEmpty()
            if (name.isBlank() && el.string("id").isNullOrBlank()) return@mapNotNull null
            DiskInfo(
                id = el.string("id").orEmpty(),
                name = name.ifBlank { "磁盘" },
                sizeGb = el.number("size")?.toLong() ?: 0L,
                createTime = el.number("create_time")?.toLong() ?: 0L,
                type = el.string("type").orEmpty(),
                type2 = el.string("type2").orEmpty(),
                status = el.number("status")?.toInt() ?: -1
            )
        }.orEmpty()

    /** 解析快照/备份通用列表（字段一致：id/name/create_time/notes）。 */
    fun parseBackups(data: JsonElement?): List<BackupItem> =
        data.arrayAt("list")?.mapNotNull { el ->
            val name = el.string("name")
            if (name.isNullOrBlank()) return@mapNotNull null
            BackupItem(
                id = el.string("id").orEmpty(),
                name = name,
                createTime = el.number("create_time")?.toLong() ?: 0L,
                notes = el.string("notes", "remark").orEmpty()
            )
        }.orEmpty()

    /** 解析 GET remf_cloud/{id}/ip 的 data.list[]。 */
    fun parseIps(data: JsonElement?): List<IpInfo> =
        data.arrayAt("list")?.mapNotNull { el ->
            val ip = el.string("ip")
            if (ip.isNullOrBlank()) return@mapNotNull null
            IpInfo(
                ip = ip,
                subnetMask = el.string("subnet_mask", "mask").orEmpty(),
                gateway = el.string("gateway").orEmpty()
            )
        }.orEmpty()
}

/** 实例操作日志条目。 */
data class HostOpLog(val text: String, val time: String, val ip: String)

/** 单个图表采样点（各 type 只用得到其中部分字段）。 */
data class ChartPoint(
    val time: Long,
    val value: Double? = null,
    val inBw: Double? = null,
    val outBw: Double? = null,
    val readBytes: Double? = null,
    val writeBytes: Double? = null,
    val readIops: Double? = null,
    val writeIops: Double? = null,
    val total: Double? = null,
    val used: Double? = null
)

/** 流量包信息。 */
data class FlowInfo(
    val total: String,
    val used: String,
    val leave: String,
    val resetDate: String
)

/** 实时用量。 */
data class RealData(
    val cpuUsage: String,
    val memoryTotal: String,
    val memoryUsable: String,
    val memoryUsage: String
)

/** 磁盘。 */
data class DiskInfo(
    val id: String,
    val name: String,
    val sizeGb: Long,
    val createTime: Long,
    val type: String,
    val type2: String,
    val status: Int
)

/** 快照/备份条目。 */
data class BackupItem(
    val id: String,
    val name: String,
    val createTime: Long,
    val notes: String
)

/** IP 条目。 */
data class IpInfo(val ip: String, val subnetMask: String, val gateway: String)
