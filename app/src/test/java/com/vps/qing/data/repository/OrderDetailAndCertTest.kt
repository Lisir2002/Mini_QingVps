package com.vps.qing.data.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vps.qing.core.ui.theme.ThemePalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.0 订单详情与实名认证解析测试（字段来自 2026-11 站点实测）。
 */
class OrderDetailAndCertTest {

    private fun orderDetailJson(): JsonObject {
        val item = JsonObject().apply {
            addProperty("id", 25)
            addProperty("description", "时长: 月付,价格:0\r\nCPU: 8核,价格:29.50\r\n操作系统: CentOS-9-Stream-x64,价格:0")
            addProperty("amount", "29.50")
            addProperty("host_id", 4)
            addProperty("product_name", "US | 美国大陆优化大宽带8核8G")
            addProperty("host_name", "ZY74044")
            addProperty("billing_cycle", "月付")
            addProperty("host_status", "Active")
        }
        val order = JsonObject().apply {
            addProperty("id", 25)
            addProperty("type", "new")
            addProperty("amount", "29.50")
            addProperty("amount_unpaid", "0.00")
            addProperty("refund_amount", "0.00")
            addProperty("credit", "0.00")
            addProperty("status", "Paid")
            addProperty("gateway", "支付宝")
            addProperty("notes", "")
            addProperty("create_time", 1794399425)
            addProperty("pay_time", 1794399471)
            add("items", com.google.gson.JsonArray().apply { add(item) })
        }
        return JsonObject().apply { add("order", order) }
    }

    @Test
    fun `order detail parses summary and items`() {
        val d = ServiceParser.orderDetail(orderDetailJson())!!
        assertEquals("25", d.id)
        assertEquals("Paid", d.status)
        assertEquals("29.50", d.amount)
        assertEquals("支付宝", d.gateway)
        assertEquals(1, d.items.size)
        val item = d.items.first()
        assertEquals("ZY74044", item.hostName)
        assertEquals("Active", item.hostStatus)
        assertEquals("月付", item.billingCycle)
        assertTrue(item.description.contains("CPU: 8核"))
        assertTrue(d.createTime.isNotBlank())
    }

    @Test
    fun `order detail returns null on missing order`() {
        assertNull(ServiceParser.orderDetail(JsonObject()))
        assertNull(ServiceParser.orderDetail(null))
    }

    private fun certJson(personStatus: Int, isCertified: Boolean): JsonObject {
        val person = JsonObject().apply {
            addProperty("username", "李由")
            addProperty("card_name", "李**")
            addProperty("card_number", "3****************4")
            addProperty("status", personStatus)
            addProperty("create_time", 1794397988)
            addProperty("auth_fail", "")
        }
        return JsonObject().apply {
            addProperty("certification_open", 1)
            addProperty("is_certified_flag", isCertified)
            addProperty("is_certification", isCertified)
            add("person", person)
            add("company", JsonObject())
        }
    }

    @Test
    fun `cert info parses person record`() {
        val c = ServiceParser.certInfo(certJson(personStatus = 1, isCertified = true))!!
        assertTrue(c.open)
        assertTrue(c.isCertified)
        val person = c.person!!
        assertEquals("李**", person.cardName)
        assertEquals("3****************4", person.cardNumber)
        assertEquals("已实名", person.statusText)
    }

    @Test
    fun `cert status text mapping`() {
        assertEquals("审核中", ServiceParser.certInfo(certJson(2, false))!!.person!!.statusText)
        assertEquals("未通过", ServiceParser.certInfo(certJson(3, false))!!.person!!.statusText)
        assertEquals("未实名", ServiceParser.certInfo(certJson(0, false))!!.person!!.statusText)
    }

    @Test
    fun `cert info empty object yields defaults`() {
        val c = ServiceParser.certInfo(JsonObject())!!
        assertTrue(c.open) // certification_open 缺失视为开启
        assertFalse(c.isCertified)
        assertNull(c.person)
        assertNull(c.company)
        assertNull(ServiceParser.certInfo(null))
    }

    @Test
    fun `cert plugins parse`() {
        val data = listWrapper(
            JsonObject().apply {
                addProperty("id", 40)
                addProperty("title", "人脸实名认证")
                add("certification_type", com.google.gson.JsonArray().apply { add(com.google.gson.JsonPrimitive("person")) })
            }
        )
        val plugins = ServiceParser.certPlugins(data)
        assertEquals(1, plugins.size)
        assertEquals("人脸实名认证", plugins[0].title)
        assertTrue(plugins[0].types.contains("person"))
    }

    @Test
    fun `theme palettes expose six advanced themes`() {
        assertEquals(6, ThemePalettes.entries.size)
        // 每个 id 唯一且可反查
        ThemePalettes.entries.forEach { spec ->
            assertEquals(spec, ThemePalettes.byId(spec.id))
        }
    }
}
