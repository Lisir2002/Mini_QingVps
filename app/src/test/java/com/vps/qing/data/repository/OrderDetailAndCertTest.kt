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
    fun `cert status text mapping follows official semantics`() {
        // 官方插件源码语义：1=已认证，2=未通过，3=待审核，4=已提交资料
        assertEquals("未通过", ServiceParser.certInfo(certJson(2, false))!!.person!!.statusText)
        assertEquals("待审核", ServiceParser.certInfo(certJson(3, false))!!.person!!.statusText)
        assertEquals("已提交资料", ServiceParser.certInfo(certJson(4, false))!!.person!!.statusText)
        assertEquals("未实名", ServiceParser.certInfo(certJson(0, false))!!.person!!.statusText)
    }

    @Test
    fun `cert info parses new switches`() {
        val json = certJson(personStatus = 1, isCertified = true).apply {
            addProperty("certification_company_open", 0)
            addProperty("certification_upload", 0)
            addProperty("certification_uncertified_cannot_buy_product", 1)
        }
        val c = ServiceParser.certInfo(json)!!
        assertFalse(c.companyOpen)
        assertFalse(c.uploadRequired)
        assertTrue(c.buyBlocked)
        // 开关缺失时：companyOpen 视为开启；upload/buyBlocked 为 opt-in，缺省按未开启
        val missing = ServiceParser.certInfo(certJson(1, true))!!
        assertTrue(missing.companyOpen)
        assertFalse(missing.uploadRequired)
        assertFalse(missing.buyBlocked)
    }

    @Test
    fun `cert plugin config parses fee info`() {
        val data = JsonParser.parseString(
            """{"free":1,"amount":1,"pay":1,"order":[]}"""
        ).asJsonObject
        val config = ServiceParser.certPluginConfig(data)
        assertEquals(1, config.free)
        assertEquals("1", config.amount)
        assertTrue(config.pay)
        assertEquals("享有 1 次免费认证，超出后每次 ¥1", config.feeHint)
        // 免费场景
        val free = ServiceParser.certPluginConfig(
            JsonParser.parseString("""{"free":3,"amount":"2.00","pay":0}""").asJsonObject
        )
        assertEquals("当前认证方式免费", free.feeHint)
        // 无免费额度需付费
        val paid = ServiceParser.certPluginConfig(
            JsonParser.parseString("""{"free":0,"amount":"2.00","pay":1}""").asJsonObject
        )
        assertEquals("本次认证需支付 ¥2.00", paid.feeHint)
        // 异常数据回退为免费
        assertEquals("当前认证方式免费", ServiceParser.certPluginConfig(null).feeHint)
        assertEquals("当前认证方式免费", ServiceParser.certPluginConfig(JsonObject()).feeHint)
    }

    @Test
    fun `cert poll status parses code and refresh`() {
        val passed = ServiceParser.certPollStatus(
            JsonParser.parseString("""{"code":1,"refresh":1}""").asJsonObject
        )
        assertEquals(1, passed.code)
        assertEquals(1, passed.refresh)
        // 官方语义：仅 code==2 && refresh==0 继续轮询
        val rejected = ServiceParser.certPollStatus(
            JsonParser.parseString("""{"code":2,"refresh":0}""").asJsonObject
        )
        assertTrue(rejected.code == 2 && rejected.refresh == 0)
        val garbage = ServiceParser.certPollStatus(JsonObject())
        assertEquals(0, garbage.code)
    }

    @Test
    fun `cert auth page parses html or redirect code`() {
        val page = ServiceParser.certAuthPage(
            200, JsonParser.parseString("""{"html":"<div>face</div>"}""").asJsonObject
        )
        assertEquals(0, page.code)
        assertEquals("<div>face</div>", page.html)
        val redirect = ServiceParser.certAuthPage(
            400, JsonParser.parseString("""{"code":10001}""").asJsonObject
        )
        assertEquals(10001, redirect.code)
        assertTrue(redirect.html.isEmpty())
    }

    @Test
    fun `cert custom fields parse and tolerate empty`() {
        // 本站实测：custom_fields 为空数组
        val empty = ServiceParser.certCustomFields(
            JsonParser.parseString("""{"custom_fields":[]}""").asJsonObject
        )
        assertTrue(empty.isEmpty())
        val data = JsonParser.parseString(
            """{"custom_fields":[{"title":"证件类型","field":"cert_type","type":"select",
                "options":["IDENTITY_CARD","PASSPORT"]},{"title":"备注","field":"note"}]}"""
        ).asJsonObject
        val fields = ServiceParser.certCustomFields(data)
        assertEquals(2, fields.size)
        assertEquals("证件类型", fields[0].title)
        assertEquals("cert_type", fields[0].field)
        assertEquals(listOf("IDENTITY_CARD", "PASSPORT"), fields[0].options)
        assertEquals("备注", fields[1].title)
        assertTrue(fields[1].options.isEmpty())
    }

    @Test
    fun `card types dictionary matches official docs`() {
        assertEquals(8, ServiceParser.CARD_TYPES.size)
        assertEquals("1" to "身份证", ServiceParser.CARD_TYPES.first())
        assertEquals("8" to "其他", ServiceParser.CARD_TYPES.last())
        assertTrue(ServiceParser.CARD_TYPES.any { it.first == "6" && it.second == "海外护照" })
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
    fun `theme palettes expose twelve advanced themes`() {
        // v1.4.14：预设主题由 6 套扩充到 12 套
        assertEquals(12, ThemePalettes.entries.size)
        assertEquals(12, ThemePalettes.entries.map { it.id }.toSet().size)
        // 每个 id 唯一且可反查
        ThemePalettes.entries.forEach { spec ->
            assertEquals(spec, ThemePalettes.byId(spec.id))
        }
    }
}
