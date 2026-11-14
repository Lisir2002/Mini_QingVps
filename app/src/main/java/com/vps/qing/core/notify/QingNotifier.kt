package com.vps.qing.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vps.qing.MainActivity
import com.vps.qing.R
import com.vps.qing.core.AppContainer
import com.vps.qing.core.permissions.PermissionCatalog
import java.util.Calendar

/**
 * 通知发送门面（v1.4.17 完整通知体系）。
 *
 * 发一条通知前依次校验四道闸，任何一道不通过都静默放弃，绝不「未经同意就打扰」：
 *  1) 权限管理页里用户是否同意「通知权限」（PermissionPrefs）；
 *  2) 通知管理页的总开关与该分类开关（NotificationPrefs）；
 *  3) 系统通知权限（NotificationManagerCompat + POST_NOTIFICATIONS）；
 *  4) 免打扰时段与事件去重。
 *
 * 点击通知通过 [DeepLinkBus] 落到 App 内原生页面，不跳网页。
 */
object QingNotifier {

    const val EXTRA_ROUTE = "qing_route"

    /** Application 启动时调用：按目录创建渠道（minSdk 26，渠道必填）。 */
    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        NotificationCatalog.ALL.forEach { spec ->
            val importance = if (spec.importanceHigh)
                NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(spec.id, spec.title, importance).apply {
                description = spec.description
                enableVibration(spec.importanceHigh)
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** 系统层面是否已经允许本应用发通知。 */
    fun systemAllowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /** 当前是否允许发送某分类通知（供 UI 显示状态，也供 send 内部复用）。 */
    fun canSend(
        context: Context,
        channelId: String,
        nowMinute: Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    ): Boolean {
        val permissions = AppContainer.permissionPrefs
        if (!permissions.isEnabled(PermissionCatalog.NOTIFICATIONS)) return false
        val prefs = AppContainer.notificationPrefs
        if (!prefs.master.value || !prefs.isChannelEnabled(channelId)) return false
        if (!systemAllowed(context)) return false
        if (prefs.dnd.value && NotificationCatalog.isInDnd(
                prefs.dndStart.value, prefs.dndEnd.value, nowMinute
            )
        ) return false
        return true
    }

    /**
     * 发通知。dedupeId 非空时同一业务事件只提醒一次（如同一条公告 / 同一张待支付订单）。
     * 返回是否真正发出，便于「测试通知」按钮给用户明确反馈。
     */
    fun send(
        context: Context,
        channelId: String,
        title: String,
        text: String,
        deepLink: String = NotificationCatalog.byId(channelId)?.deepLink ?: NotificationCatalog.LINK_ORDERS,
        dedupeId: String? = null,
        nowMinute: Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    ): Boolean {
        if (!canSend(context, channelId, nowMinute)) return false
        val dedupe = context.getSharedPreferences("qingvps_notify_dedupe", Context.MODE_PRIVATE)
        if (dedupeId != null) {
            val key = NotificationCatalog.dedupeKey(channelId, dedupeId)
            if (dedupe.getBoolean(key, false)) return false
        }

        val routeIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_ROUTE, deepLink)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val requestCode = (deepLink + (dedupeId ?: title)).hashCode() and 0x7fffffff
        val pi = PendingIntent.getActivity(
            context, requestCode, routeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val prefs = AppContainer.notificationPrefs
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_qingvps)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                if (NotificationCatalog.byId(channelId)?.importanceHigh == true)
                    NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
        if (!prefs.sound.value) builder.setSilent(true)
        if (prefs.vibrate.value) builder.setVibrate(longArrayOf(0L, 120L, 80L, 120L))

        return try {
            NotificationManagerCompat.from(context).notify(requestCode, builder.build())
            if (dedupeId != null) {
                dedupe.edit()
                    .putBoolean(NotificationCatalog.dedupeKey(channelId, dedupeId), true)
                    .apply()
            }
            true
        } catch (_: SecurityException) {
            false
        }
    }

    // ---- 业务快捷入口（各 ViewModel 只依赖这些方法，不碰 Android 通知 API）----

    fun orderPaid(context: Context, orderId: String, amount: String) = send(
        context, NotificationCatalog.ORDER,
        title = "支付成功",
        text = "订单 #$orderId 已支付 $amount，服务正在开通",
        dedupeId = "paid-$orderId"
    )

    fun unpaidOrder(context: Context, hostId: String, productName: String) = send(
        context, NotificationCatalog.ORDER,
        title = "有待支付订单",
        text = "$productName 尚未支付，点击前往订单完成付款",
        dedupeId = "unpaid-$hostId"
    )

    fun newAnnouncement(context: Context, id: String, title: String) = send(
        context, NotificationCatalog.NEWS,
        title = "站点新公告",
        text = title,
        deepLink = "announcement:$id",
        dedupeId = "news-$id"
    )

    fun ticketReply(context: Context, ticketId: String, subject: String) = send(
        context, NotificationCatalog.TICKET,
        title = "工单有新回复",
        text = subject,
        deepLink = "ticket:$ticketId",
        dedupeId = "ticket-$ticketId"
    )

    fun renewSoon(context: Context, hostId: String, productName: String, days: Int) = send(
        context, NotificationCatalog.RENEW,
        title = "服务即将到期",
        text = "$productName 将在 $days 天后到期，记得续费",
        dedupeId = "renew-$hostId-$days"
    )

    fun security(context: Context, text: String) = send(
        context, NotificationCatalog.SECURITY,
        title = "账号安全提醒",
        text = text,
        dedupeId = "security-${System.currentTimeMillis() / 60_000L}"
    )

    /** 通知管理页「发送测试通知」。 */
    fun test(context: Context) = send(
        context, NotificationCatalog.ORDER,
        title = "QingVPS 通知测试",
        text = "通知链路已打通，点击可回到应用内的订单页",
        dedupeId = null
    )
}
