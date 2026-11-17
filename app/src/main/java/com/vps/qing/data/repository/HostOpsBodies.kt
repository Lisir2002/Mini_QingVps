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
    /**
     * 解析 GET remf_cloud/{id}/status。
     * 官方 CloudController::status @return：data.status(on/off/operating/fault) + data.desc。
     * 部分版本 desc 在 data 顶层或嵌套，做多级兼容。
     */
    fun parseStatus(data: JsonElement?): PowerState {
        val status = data.string("status", "power_status", "state").orEmpty().lowercase()
        val desc = data.string("desc", "description", "status_text", "msg").orEmpty()
        // 官方 CloudLogic::status 映射后只会返回 5 态：on/off/suspend/operating/fault。
        // 稳定态 = 除 operating 外的已知终态（on/off/fault/suspend）；suspend（暂停/欠费停机）
        // 也是终态，v1.4.24 漏掉它会导致实例处于暂停时轮询永不结束、一直转圈。
        // 空值（解析异常）视为非稳定，让上层继续重试。
        val stable = status in setOf("on", "off", "fault", "suspend")
        return PowerState(raw = status, desc = desc, stable = stable)
    }

    /**
     * 由「服务器原始状态 + 本地最近发起的动作」推导电源显示文案。
     *
     * 官方 status 只有 on/off/operating/fault 四态，网站端却能显示
     * 「正在开机/正在关机/正在重启-关机中/正在重启-开机中」这类细分，
     * 是通过「operating + 用户动作 + 状态迁移轨迹」推断。这里复刻该逻辑：
     * - 稳定态直接显示 已开机/已关机/故障；
     * - operating（或请求刚提交、状态尚未翻转）时按最近动作细分；
     * - 重启依据是否观察到 off 中间态，分为「关机中」「开机中」。
     *
     * @param state 服务器当前状态
     * @param pendingAction 最近发起且尚未确认结束的动作
     * @param sawOff 本次重启是否已观察到 off 中间态（轨迹 on→off→on）
     */
    fun powerLabel(state: PowerState, pendingAction: String?, sawOff: Boolean): String {
        if (state.stable) {
            return when (state.raw) {
                "on" -> "已开机"
                "off" -> "已关机"
                "fault" -> "故障"
                "suspend" -> "已暂停"
                else -> state.desc.ifBlank { state.raw.ifBlank { "未知" } }
            }
        }
        // operating 或请求刚提交（尚未翻转）时，按最近动作给出即时细分反馈
        when (pendingAction) {
            "on" -> return "正在开机"
            "off", "hard_off" -> return "正在关机"
            "reboot", "hard_reboot" ->
                return if (sawOff) "正在重启-开机中" else "正在重启-关机中"
        }
        return state.desc.ifBlank { "操作中" }
    }

    /** 重启过程中 status 短暂为 off 时，最多再等这几轮（每轮约 1.2s）便视为已落定，防止死转圈。 */
    const val REBOOT_OFF_GRACE_ROUNDS = 5

    /**
     * 判断一次电源轮询是否可以结束（v1.4.25，对齐官方 cloudDetail.js:getCloudStatus）。
     *
     * 官方逻辑：只要 status=="operating" 就继续自查，落到稳定态就停止并把 desc 渲染出来。
     * - on/off/hard_off 等操作：任意稳定态（on/off/fault/suspend）即结束；
     * - 重启：off 是「关机再开机」过程中的瞬时态，不能当作完成，需继续等到 on；
     *   但若 off 持续 [REBOOT_OFF_GRACE_ROUNDS] 轮（机器重启后停在关机态），也判为落定，避免无限轮询。
     *
     * @param offRounds 本次轮询中连续观察到 off 的轮数（仅重启用到）。
     * @param rounds 已轮询轮数；操作后首轮可能读到动作生效前的旧稳定态，故动作驱动时至少等 2 轮再落定。
     */
    fun isPowerSettled(raw: String, pendingAction: String?, offRounds: Int, rounds: Int): Boolean {
        if (raw.isBlank() || raw == "operating") return false
        // 防止「刚提交就落到动作发生前的旧状态」而过早结束：动作驱动轮询至少 2 轮。
        if (pendingAction != null && rounds < 2) return false
        return when (pendingAction) {
            "reboot", "hard_reboot" ->
                if (raw == "off") offRounds >= REBOOT_OFF_GRACE_ROUNDS else true
            else -> true
        }
    }

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

/**
 * 电源实时状态（GET remf_cloud/{id}/status）。
 * raw：服务器原始值 on/off/operating/fault；desc：服务器中文描述；stable：是否为稳定态。
 */
data class PowerState(
    val raw: String = "",
    val desc: String = "",
    val stable: Boolean = false
)

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
