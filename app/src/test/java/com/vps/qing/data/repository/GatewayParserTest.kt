package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.17：支付方式必须由站点 GET /gateway 实时参数驱动。
 * 用站点真实返回结构（EpayAli + EpayWx，base64 图标）验证保真解析，
 * 确保新增网关自动出现、原始字段不丢失、App 不做任何硬编码映射。
 */
class GatewayParserTest {

    @Test
    fun `站点实时网关列表原样解析并保留原始字段`() {
        val json = """{"list":[
            {"id":36,"name":"EpayAli","title":"支付宝","url":"data:image/png;base64,AAA"},
            {"id":44,"name":"EpayWx","title":"微信支付","url":"data:image/png;base64,BBB","sandbox":"0"}
        ],"count":2}"""
        val list = CheckoutParser.gateways(JsonParser.parseString(json))
        assertEquals(2, list.size)
        assertEquals("EpayAli", list[0].name)
        assertEquals("支付宝", list[0].title)
        assertEquals("36", list[0].id)
        assertEquals("EpayWx", list[1].name)
        assertEquals("微信支付", list[1].title)
        assertEquals("44", list[1].id)
        assertEquals("0", list[1].raw["sandbox"])
        assertTrue(list[1].iconUrl.startsWith("data:image"))
        assertFalse("超长 base64 图标不进原始参数表", list[1].raw.containsKey("url"))
    }

    @Test
    fun `站点新增第三种网关时自动出现不做白名单过滤`() {
        val json = """{"list":[
            {"name":"EpayAli","title":"支付宝"},
            {"name":"EpayWx","title":"微信支付"},
            {"name":"Balance","title":"余额支付"}
        ]}"""
        val list = CheckoutParser.gateways(JsonParser.parseString(json))
        assertEquals(3, list.size)
        assertEquals("余额支付", list[2].title)
        assertEquals("Balance", list[2].name)
    }

    @Test
    fun `兼容 gateways 字段与顶层数组`() {
        assertEquals(1, CheckoutParser.gateways(
            JsonParser.parseString("""{"gateways":[{"name":"A","title":"甲"}]}""")
        ).size)
        assertEquals(1, CheckoutParser.gateways(
            JsonParser.parseString("""[{"name":"A","title":"甲"}]""")
        ).size)
        assertTrue(CheckoutParser.gateways(JsonParser.parseString("""{"list":[]}""")).isEmpty())
        assertTrue(CheckoutParser.gateways(JsonParser.parseString("null")).isEmpty())
        assertTrue(CheckoutParser.gateways(null).isEmpty())
    }

    @Test
    fun `缺少 name 的项被跳过但 title 缺失时回退到 name`() {
        val list = CheckoutParser.gateways(JsonParser.parseString(
            """{"list":[{"title":"没有标识"},{"name":"EpayWx"}]}"""
        ))
        assertEquals(1, list.size)
        assertEquals("EpayWx", list[0].title)
    }

    @Test
    fun `发起支付把站点 name 原样作为 gateway 参数`() {
        val body = CheckoutBodies.pay("47", "EpayWx")
        assertEquals(47, body.get("id").asInt)
        assertEquals("EpayWx", body.get("gateway").asString)
        val nonNumeric = CheckoutBodies.pay("order-abc", "Balance")
        assertEquals("order-abc", nonNumeric.get("id").asString)
    }
}
