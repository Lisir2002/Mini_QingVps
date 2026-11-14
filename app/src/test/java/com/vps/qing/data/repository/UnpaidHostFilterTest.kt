package com.vps.qing.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.11 已购机器过滤测试：待支付/未支付状态的机器不进
 * 首页「已购服务」与「我的-已购产品」列表。
 */
class UnpaidHostFilterTest {

    @Test
    fun `unpaid statuses in english are filtered`() {
        assertTrue(isUnpaidHostStatus("Unpaid"))
        assertTrue(isUnpaidHostStatus("unpaid"))
        assertTrue(isUnpaidHostStatus("UNPAID"))
    }

    @Test
    fun `unpaid statuses in chinese are filtered`() {
        assertTrue(isUnpaidHostStatus("待支付"))
        assertTrue(isUnpaidHostStatus("待付款"))
        assertTrue(isUnpaidHostStatus("未支付"))
    }

    @Test
    fun `paid and active statuses pass through`() {
        assertFalse(isUnpaidHostStatus("Active"))
        assertFalse(isUnpaidHostStatus("active"))
        assertFalse(isUnpaidHostStatus("已开通"))
        assertFalse(isUnpaidHostStatus("Deleted"))
        assertFalse(isUnpaidHostStatus(""))
    }

    @Test
    fun `null status is not treated as unpaid`() {
        assertFalse(isUnpaidHostStatus(null))
    }

    @Test
    fun `unpaid substring inside longer status still filtered`() {
        assertTrue(isUnpaidHostStatus("Unpaid_Order"))
        assertTrue(isUnpaidHostStatus("状态：待支付"))
    }
}
