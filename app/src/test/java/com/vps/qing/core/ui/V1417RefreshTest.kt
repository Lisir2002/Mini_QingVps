package com.vps.qing.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.4.17：刷新结果提示 3-5 秒自动隐藏、成功与失败都给出明确反馈。 */
class V1417RefreshTest {

    @Test
    fun `结果提示时长落在 3-5 秒区间`() {
        assertTrue(REFRESH_DONE_VISIBLE_MS in 3000L..5000L)
    }

    @Test
    fun `刷新反馈文案包含成功与失败两种`() {
        assertEquals("已刷新", Strings.REFRESH_DONE)
        assertTrue(Strings.REFRESH_FAILED.contains("失败"))
        assertTrue(Strings.REFRESHING.isNotBlank())
    }

    @Test
    fun `版本号已推进到 1_4_17`() {
        assertEquals("1.4.17", Strings.APP_VERSION)
    }
}
