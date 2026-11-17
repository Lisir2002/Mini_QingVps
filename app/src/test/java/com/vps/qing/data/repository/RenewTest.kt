package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 续费解析单测（v1.4.26）：依据魔方 V10 官方 addon idcsmart_renew 返回结构。
 */
class RenewTest {

    @Test
    fun `续费周期列表_解析官方host数组全字段`() {
        val data = JsonParser.parseString(
            """{"host":[
                {"id":"7","billing_cycle":"month","billing_cycle_name":"月付","duration":2592000,
                 "price":"10.00","base_price":"13.33","renew_amount":"8.00","max_renew":false},
                {"id":"8","billing_cycle":"year","billing_cycle_name":"年付","duration":31536000,
                 "price":"100.00","max_renew":"1"}
            ]}"""
        )
        val cycles = RenewParser.cycles(data)
        assertEquals(2, cycles.size)
        assertEquals("month", cycles[0].billingCycle)
        assertEquals("月付", cycles[0].description)
        assertEquals(2592000L, cycles[0].duration)
        assertEquals(10.0, cycles[0].price, 0.001)
        assertEquals(13.33, cycles[0].basePrice, 0.001)
        assertFalse(cycles[0].maxRenew)
        assertEquals("year", cycles[1].billingCycle)
        assertEquals(100.0, cycles[1].price, 0.001)
        assertTrue(cycles[1].maxRenew) // "1" 视为 true
    }

    @Test
    fun `续费周期列表_兼容data直接是数组`() {
        val data = JsonParser.parseString(
            """[{"id":"1","billing_cycle":"小时","duration":3600,"price":1.5}]"""
        )
        val cycles = RenewParser.cycles(data)
        assertEquals(1, cycles.size)
        assertEquals("小时", cycles[0].billingCycle)
        assertEquals(1.5, cycles[0].price, 0.001)
    }

    @Test
    fun `续费周期列表_空数据返回空列表`() {
        assertTrue(RenewParser.cycles(null).isEmpty())
        assertTrue(RenewParser.cycles(JsonParser.parseString("{}")).isEmpty())
        assertTrue(RenewParser.cycles(JsonParser.parseString("{\"host\":[]}")).isEmpty())
    }

    @Test
    fun `提交续费_顶层code为Unpaid时返回订单ID`() {
        val data = JsonParser.parseString("""{"id":"98765"}""")
        val r = RenewParser.result("Unpaid", data, "续费成功")
        assertFalse(r.paid)
        assertEquals("Unpaid", r.code)
        assertEquals("98765", r.orderId)
        assertEquals("续费成功", r.message)
    }

    @Test
    fun `提交续费_顶层code为Paid时标记已支付`() {
        val r = RenewParser.result("Paid", JsonParser.parseString("{}"), "续费成功")
        assertTrue(r.paid)
        assertEquals("Paid", r.code)
    }

    @Test
    fun `提交续费_data内code为Paid也识别为已支付`() {
        val data = JsonParser.parseString("""{"code":"Paid"}""")
        val r = RenewParser.result("", data, "余额续费")
        assertTrue(r.paid)
    }

    @Test
    fun `自动续费状态_1为真0为假`() {
        assertTrue(RenewParser.autoStatus(JsonParser.parseString("""{"status":"1"}""")))
        assertTrue(RenewParser.autoStatus(JsonParser.parseString("""{"status":"true"}""")))
        assertFalse(RenewParser.autoStatus(JsonParser.parseString("""{"status":"0"}""")))
        assertFalse(RenewParser.autoStatus(null))
    }
}
