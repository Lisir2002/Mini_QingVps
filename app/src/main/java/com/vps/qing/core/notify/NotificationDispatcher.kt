package com.vps.qing.core.notify

import android.content.Context
import com.vps.qing.data.repository.HostSummary
import com.vps.qing.data.repository.NewsItem
import com.vps.qing.data.repository.isUnpaidHostStatus
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil

/**
 * 业务事件 → 通知的调度器（v1.4.17）。
 *
 * 站点没有推送通道，因此采用「数据快照差异检测」：首页每次成功拉取数据后调用
 * [onDashboardSnapshot]，与上次快照对比，只对新增事件发通知；首次启动只建立静默基线，
 * 不会在安装后第一次打开就弹出一堆历史消息。支付成功等即时事件由 QingNotifier 直发。
 */
object NotificationDispatcher {

    private const val PREF = "qingvps_notify_snapshot"

    /**
     * @param news 站点公告（id + 标题）
     * @param hosts 已购主机（含待支付状态与到期时间）
     */
    fun onDashboardSnapshot(
        context: Context,
        news: List<NewsItem>,
        hosts: List<HostSummary>,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val baselineReady = sp.getBoolean("baseline_ready", false)

        // ---- 新公告：只对本次快照中新出现的 id 提醒，最多 3 条避免刷屏 ----
        val previousIds = sp.getStringSet("news_ids", emptySet()).orEmpty()
        if (baselineReady) {
            news.filter { it.id.isNotBlank() && it.id !in previousIds }
                .take(3)
                .forEach { QingNotifier.newAnnouncement(context, it.id, it.title) }
        }

        // ---- 待支付订单 / 即将到期（去重由 QingNotifier 的 dedupeKey 保证）----
        if (baselineReady) {
            hosts.forEach { host ->
                val id = host.id.orEmpty()
                if (id.isBlank()) return@forEach
                val name = host.productName ?: host.name ?: "云服务"
                if (isUnpaidHostStatus(host.status)) {
                    QingNotifier.unpaidOrder(context, id, name)
                } else {
                    val days = daysToDue(host.dueTime, nowMs)
                    if (days != null && days in 0..7) {
                        QingNotifier.renewSoon(context, id, name, days)
                    }
                }
            }
        }

        sp.edit()
            .putStringSet("news_ids", news.map { it.id }.toSet())
            .putBoolean("baseline_ready", true)
            .apply()
    }

    /**
     * v1.4.18：供工单详情刷新时调用。
     * 旧版这个方法**没有任何调用点**，工单通知渠道形同虚设；现在由
     * ProfileViewModel.fetchTicketDetail 对比本次与上次的回复列表，只有出现
     * 新增的客服回复才触发，并用该回复 id 作为去重键（后续新回复仍能提醒）。
     */
    fun onTicketChanged(
        context: Context,
        ticketId: String,
        subject: String,
        hasNewReply: Boolean,
        replyKey: String = ""
    ) {
        if (hasNewReply && ticketId.isNotBlank()) {
            QingNotifier.ticketReply(context, ticketId, subject, replyKey)
        }
    }

    /**
     * 距到期天数（纯函数，可单测）。兼容 Unix 秒 / 毫秒时间戳与常见日期格式。
     * 返回 null 表示无法解析或已过期；0 表示今天到期。
     */
    fun daysToDue(dueTime: String?, nowMs: Long): Int? {
        val raw = dueTime?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val dueMs = raw.toLongOrNull()?.let { n ->
            when {
                n > 100_000_000_000L -> n              // 毫秒
                n > 100_000_000L -> n * 1000L        // 秒
                else -> null
            }
        } ?: parseDate(raw) ?: return null
        val diff = dueMs - nowMs
        if (diff < 0L) return null
        return ceil(diff / 86_400_000.0).toInt().coerceAtLeast(0)
    }

    private fun parseDate(raw: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd"
        )
        for (p in patterns) {
            runCatching {
                return SimpleDateFormat(p, Locale.CHINA).parse(raw)?.time
            }
        }
        return null
    }
}
