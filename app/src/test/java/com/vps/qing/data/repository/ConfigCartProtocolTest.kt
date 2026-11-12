package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.0 新协议单测：配置项解析、算价解析、加购请求体。
 * 样本结构来自 2026-11 站点网页端实测：
 *  - GET /reidcsmart_common/product/45/configoption
 *  - POST /product/45/config_option
 *  - POST /cart（实测匿名可真实入云）
 */
class ConfigCartProtocolTest {

    private val configJson = """
    {
      "common_product": {"id": 45, "name": "APAC | 亚太-体验版"},
      "configoptions": [
        {
          "id": "12568", "option_name": "流量", "option_type": "select",
          "description": "", "unit": "GiB", "qty_min": 0, "qty_max": 0,
          "subs": [
            {"id": 45697, "option_name": "50G", "country": "", "price": 0},
            {"id": 45698, "option_name": "100G", "country": "", "price": 10}
          ]
        },
        {
          "id": "12570", "option_name": "系统", "option_type": "os",
          "subs": [
            {"os": "CentOS", "version": [{"id": 45800, "option_name": "7.9"}]},
            {"os": "Ubuntu", "version": [{"id": 45801, "option_name": "22.04"}]}
          ]
        },
        {
          "id": "12572", "option_name": "数量", "option_type": "quantity",
          "qty_min": 1, "qty_max": 10, "subs": []
        }
      ],
      "custom_cycles": [
        {"id": 3706, "name": "月付", "cycle_time": 1, "cycle_unit": "month", "cycle_amount": 25, "is_default": 1},
        {"id": 3707, "name": "年付", "cycle_time": 12, "cycle_unit": "month", "cycle_amount": 250, "is_default": 0}
      ]
    }
    """.trimIndent()

    @Test
    fun `config parser extracts options subs and cycles`() {
        val cfg = ConfigParser.parse("45", JsonParser.parseString(configJson))

        assertEquals("APAC | 亚太-体验版", cfg.productName)
        assertEquals(3, cfg.options.size)
        assertEquals(2, cfg.cycles.size)

        val flow = cfg.options[0]
        assertEquals("12568", flow.id)
        assertEquals("流量", flow.name)
        assertEquals(OptionType.SELECT, flow.type)
        assertEquals(2, flow.subs.size)
        assertEquals("45697", flow.defaultSubId)

        val os = cfg.options[1]
        assertEquals("os", os.type)
        // os 类型候选值要从 version[] 展开，并带分组名
        assertEquals(listOf("CentOS", "Ubuntu"), os.subs.map { it.group }.distinct())
        assertEquals("7.9", os.subs[0].name)

        val qty = cfg.options[2]
        assertEquals(1, qty.qtyMin)
        assertEquals(10, qty.qtyMax)
    }

    @Test
    fun `default cycle prefers is_default flag`() {
        val cfg = ConfigParser.parse("45", JsonParser.parseString(configJson))
        assertEquals("3706", cfg.defaultCycleId)
        assertEquals("月付", cfg.cycles[0].name)
        assertEquals(25.0, cfg.cycles[0].amount, 0.001)
    }

    @Test
    fun `option type classification`() {
        assertTrue(OptionType.isChoice(OptionType.SELECT))
        assertTrue(OptionType.isChoice(OptionType.OS))
        assertTrue(OptionType.isChoice(OptionType.AREA))
        assertFalse(OptionType.isChoice(OptionType.QUANTITY))
        // quantity / multi_select 需要包成单元素数组
        assertTrue(OptionType.needsArray(OptionType.QUANTITY))
        assertTrue(OptionType.needsArray(OptionType.MULTI_SELECT))
        assertFalse(OptionType.needsArray(OptionType.SELECT))
    }

    @Test
    fun `cart body mirrors site addCart protocol`() {
        val body = CartBodies.build(
            productId = "45",
            selections = listOf(
                CartBodies.Selection("12568", "45697"),
                CartBodies.Selection("12572", "3", array = true)
            ),
            cycleId = "3706",
            qty = 2
        )

        assertEquals(45, body.get("product_id").asInt)
        assertEquals(2, body.get("qty").asInt)
        assertTrue(body.has("customfield"))
        assertTrue(body.has("self_defined_field"))

        val co = body.getAsJsonObject("config_options")
        assertEquals(3706L, co.get("cycle").asLong)
        assertTrue(co.getAsJsonObject("cascade_configoption").entrySet().isEmpty())

        val configoption = co.getAsJsonObject("configoption")
        assertEquals(45697L, configoption.get("12568").asLong)
        // quantity 类型必须是 [3] 数组包装（站点 formatData 语义）
        assertEquals(3, configoption.getAsJsonArray("12572")[0].asInt)
    }

    @Test
    fun `cart body coerces qty and keeps string ids when not numeric`() {
        val body = CartBodies.build("x", emptyList(), null, 0)
        assertEquals(1, body.get("qty").asInt)
        assertEquals("x", body.get("product_id").asString)
        val co = body.getAsJsonObject("config_options")
        assertFalse(co.has("cycle"))

        val textBody = CartBodies.build(
            "45",
            listOf(CartBodies.Selection("12568", "custom-text")),
            "monthly", 1
        )
        // 非数字值原样保留为字符串
        assertEquals("custom-text", textBody.getAsJsonObject("config_options")
            .getAsJsonObject("configoption").get("12568").asString)
        assertEquals("monthly", textBody.getAsJsonObject("config_options").get("cycle").asString)
    }

    @Test
    fun `qty body clamps to minimum 1`() {
        assertEquals(5, CartBodies.qty(5).get("qty").asInt)
        assertEquals(1, CartBodies.qty(0).get("qty").asInt)
    }

    @Test
    fun `price parser reads price duration and preview`() {
        val data = JsonParser.parseString("""
        {
          "price": 25, "billing_cycle": "月付", "duration": 30,
          "description": "APAC 体验版",
          "preview": [{"name": "流量", "value": "50G"}, {"name": "", "value": ""}]
        }
        """.trimIndent())
        val quote = ConfigParser.price(data)
        assertEquals(25.0, quote.price, 0.001)
        assertEquals("月付", quote.billingCycle)
        assertEquals(30L, quote.duration)
        assertEquals(listOf("流量：50G"), quote.preview)
    }

    @Test
    fun `price parser falls back through candidate keys`() {
        val quote = ConfigParser.price(JsonParser.parseString("""{"discount_order_price": 18.5}"""))
        assertEquals(18.5, quote.price, 0.001)
        assertNull(quote.billingCycle.ifEmpty { null } ?: "月付".takeIf { false })
    }
}
