package com.vps.qing.data.repository

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 个人中心「账号与服务」解析测试：样例来自 2026-11 对站点 /host /order /transaction 的登录态实测。
 */
class ServiceParserTest {

    private fun hostJson(id: Int, status: String = "Active"): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", "ZY74044")
        addProperty("product_name", "US | 美国大陆优化大宽带8核8G")
        addProperty("status", status)
        addProperty("active_time", 1794399603L)
        addProperty("due_time", 1796991790L)
        addProperty("billing_cycle", "月付")
        addProperty("renew_amount", "29.50")
        addProperty("dedicate_ip", "38.55.157.115")
        addProperty("base_info", "8核-8G-70G")
        addProperty("image_name", "debian13-定制版")
        addProperty("renew_forbidden", 0)
    }

    private fun orderJson(id: Int, status: String = "Paid"): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("type", "new")
        addProperty("amount", "29.50")
        addProperty("status", status)
        addProperty("gateway", "支付宝")
        addProperty("create_time", 1794399425L)
        addProperty("pay_time", 1794399471L)
        addProperty("host_name", "ZY74044")
        add("product_names", com.google.gson.JsonArray().apply { add(com.google.gson.JsonPrimitive("US | 美国大陆优化大宽带8核8G")) })
    }

    @Test
    fun `hosts parse basic fields`() {
        val d = listWrapper(hostJson(4))
        val list = ServiceParser.hosts(d)
        assertEquals(1, list.size)
        val h = list[0]
        assertEquals("4", h.id)
        assertEquals("ZY74044", h.name)
        assertEquals("38.55.157.115", h.ip)
        assertEquals("8核-8G-70G", h.baseInfo)
        assertEquals("月付", h.billingCycle)
        assertTrue(h.dueTime.isNotBlank())
    }

    @Test
    fun `hosts count respected - pagination total comes from count not list`() {
        // count=42 但只返回 1 条：解析不报错，解析层不做翻页（翻页在请求层保证）
        val d = JsonObject().apply {
            add("list", com.google.gson.JsonArray().apply { add(hostJson(4)) })
            addProperty("count", 42)
        }
        assertEquals(1, ServiceParser.hosts(d).size)
    }

    @Test
    fun `orders parse product names and status`() {
        val d = listWrapper(orderJson(25), orderJson(26, status = "Unpaid"))
        val list = ServiceParser.orders(d)
        assertEquals(2, list.size)
        assertEquals(listOf("US | 美国大陆优化大宽带8核8G"), list[0].productNames)
        assertEquals("支付宝", list[0].gateway)
        assertEquals("Unpaid", list[1].status)
    }

    @Test
    fun `transactions parse number and gateway`() {
        val txn = JsonObject().apply {
            addProperty("id", 17)
            addProperty("amount", "29.50")
            addProperty("gateway", "支付宝")
            addProperty("transaction_number", "2026111120171577980")
            addProperty("order_id", 25)
            addProperty("create_time", 1794399471L)
            addProperty("type", "new")
        }
        val list = ServiceParser.transactions(listWrapper(txn))
        assertEquals(1, list.size)
        assertEquals("2026111120171577980", list[0].number)
        assertEquals("25", list[0].orderId)
    }

    @Test
    fun `empty list tolerated`() {
        assertTrue(ServiceParser.hosts(listWrapper()).isEmpty())
        assertTrue(ServiceParser.orders(listWrapper()).isEmpty())
        assertTrue(ServiceParser.tickets(listWrapper()).isEmpty())
        assertTrue(ServiceParser.subAccounts(listWrapper()).isEmpty())
    }

    @Test
    fun `bad payload tolerated - html string data`() {
        assertTrue(ServiceParser.hosts(com.google.gson.JsonPrimitive("<html>")).isEmpty())
    }
}
