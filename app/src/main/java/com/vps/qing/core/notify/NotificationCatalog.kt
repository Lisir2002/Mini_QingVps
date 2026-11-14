package com.vps.qing.core.notify

/**
 * 通知渠道目录（v1.4.17 完整通知体系）。
 *
 * 一个渠道 = 一类业务消息 + 一个独立开关 + 一个重要性级别 + 一个点击落点。
 * 新增业务只需在此登记，通知管理页会自动出现对应开关，不需要改 UI。
 */
data class NotifyChannelSpec(
    val id: String,
    val title: String,
    val description: String,
    /** Android 通知渠道重要性（默认 = 无声音，高 = 有提示音/悬浮）。 */
    val importanceHigh: Boolean = false,
    /** 点击通知后的应用内落点（DeepLinkBus token）。 */
    val deepLink: String
)

object NotificationCatalog {
    const val ORDER = "qing_order"
    const val TICKET = "qing_ticket"
    const val NEWS = "qing_news"
    const val RENEW = "qing_renew"
    const val SECURITY = "qing_security"

    /** 通知点击后带到哪个原生页，全部是 App 内路由，不跳网站。 */
    const val LINK_ORDERS = "orders"
    const val LINK_TICKETS = "tickets"
    const val LINK_ANNOUNCEMENTS = "announcements"
    const val LINK_HOSTS = "hosts"
    const val LINK_SECURITY = "security"

    val ALL: List<NotifyChannelSpec> = listOf(
        NotifyChannelSpec(
            id = ORDER,
            title = "订单与支付",
            description = "支付成功、待支付订单提醒、订单状态变化",
            importanceHigh = true,
            deepLink = LINK_ORDERS
        ),
        NotifyChannelSpec(
            id = TICKET,
            title = "工单与回复",
            description = "工单有新回复、工单状态变更",
            deepLink = LINK_TICKETS
        ),
        NotifyChannelSpec(
            id = NEWS,
            title = "公告与活动",
            description = "站点发布新公告、活动信息",
            deepLink = LINK_ANNOUNCEMENTS
        ),
        NotifyChannelSpec(
            id = RENEW,
            title = "续费与到期",
            description = "服务即将到期、续费成功提醒",
            importanceHigh = true,
            deepLink = LINK_HOSTS
        ),
        NotifyChannelSpec(
            id = SECURITY,
            title = "账号安全",
            description = "密码/邮箱变更、异地登录等安全提醒",
            importanceHigh = true,
            deepLink = LINK_SECURITY
        )
    )

    fun byId(id: String): NotifyChannelSpec? = ALL.firstOrNull { it.id == id }

    /**
     * 免打扰判定（纯函数，可单测）。跨午夜区间（如 23:00–07:00）同样正确。
     * 起止相等视为全天免打扰。
     */
    fun isInDnd(startMinute: Int, endMinute: Int, nowMinute: Int): Boolean {
        val s = startMinute.coerceIn(0, 1439)
        val e = endMinute.coerceIn(0, 1439)
        val n = nowMinute.coerceIn(0, 1439)
        if (s == e) return true
        return if (s < e) n in s until e else n >= s || n < e
    }

    /** 同一业务事件的去重键：同一条公告 / 同一张待支付订单只提醒一次。 */
    fun dedupeKey(channelId: String, businessId: String): String = "$channelId:$businessId"
}
