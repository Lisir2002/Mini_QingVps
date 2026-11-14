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
    fun `版本号已推进到 1_4_18 或更高`() {
        // 断言「不低于当前发布版本」而不是写死等值，避免每次发版都因升版本号失败
        val v = Strings.APP_VERSION
        assertTrue("$v 应为三段数字", v.matches(Regex("""\d+\.\d+\.\d+""")))
        val (major, minor, patch) = v.split(".").map { it.toInt() }
        val numeric = major * 1_000_000 + minor * 1_000 + patch
        assertTrue("$v 应不低于 1.4.18", numeric >= 1_004_018)
    }
}
