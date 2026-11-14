package com.vps.qing.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** v1.4.17 通知体系：渠道目录、免打扰、去重、到期解析的纯函数验证。 */
class NotificationInfraTest {

    @Test
    fun `五类渠道 id 唯一且都有应用内落点`() {
        assertEquals(5, NotificationCatalog.ALL.size)
        assertEquals(5, NotificationCatalog.ALL.map { it.id }.toSet().size)
        assertTrue(NotificationCatalog.ALL.all { it.deepLink.isNotBlank() })
        assertTrue(NotificationCatalog.ALL.all { it.title.isNotBlank() && it.description.isNotBlank() })
        assertEquals("orders", NotificationCatalog.byId(NotificationCatalog.ORDER)?.deepLink)
        assertEquals("security", NotificationCatalog.byId(NotificationCatalog.SECURITY)?.deepLink)
    }

    @Test
    fun `免打扰支持跨午夜区间与普通区间`() {
        val night = 23 * 60
        val morning = 7 * 60
        assertTrue(NotificationCatalog.isInDnd(night, morning, 23 * 60 + 30))
        assertTrue(NotificationCatalog.isInDnd(night, morning, 6 * 60))
        assertFalse(NotificationCatalog.isInDnd(night, morning, 12 * 60))
        assertTrue(NotificationCatalog.isInDnd(22 * 60, 8 * 60, 22 * 60))
        assertFalse(NotificationCatalog.isInDnd(9 * 60, 18 * 60, 8 * 60))
        assertFalse(NotificationCatalog.isInDnd(9 * 60, 18 * 60, 18 * 60))
        // 起止相等 = 全天免打扰
        assertTrue(NotificationCatalog.isInDnd(9 * 60, 9 * 60, 12 * 60))
    }

    @Test
    fun `去重键包含渠道与业务 id`() {
        assertEquals("qing_news:42", NotificationCatalog.dedupeKey(NotificationCatalog.NEWS, "42"))
        assertFalse(
            NotificationCatalog.dedupeKey(NotificationCatalog.ORDER, "42") ==
                NotificationCatalog.dedupeKey(NotificationCatalog.NEWS, "42")
        )
    }

    @Test
    fun `到期天数兼容时间戳与日期格式`() {
        val now = 1_700_000_000_000L
        assertEquals(1, NotificationDispatcher.daysToDue("1700086400000", now)) // 毫秒
        assertEquals(1, NotificationDispatcher.daysToDue("1700086400", now))    // 秒
        assertEquals(0, NotificationDispatcher.daysToDue("1700000000000", now)) // 今天
        assertNull(NotificationDispatcher.daysToDue("1699000000000", now))      // 已过期
        assertNull(NotificationDispatcher.daysToDue(null, now))
        assertNull(NotificationDispatcher.daysToDue("", now))
        assertNull(NotificationDispatcher.daysToDue("不是日期", now))

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val tomorrow = fmt.format(Date(now + 86_400_000L))
        val days = NotificationDispatcher.daysToDue(tomorrow, now)
        assertTrue("日期格式应能解析出 0~2 天，实际 $days", days != null && days in 0..2)
    }
}
