package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 已购产品详情解析测试（v1.4.2）：样例来自 2026-11 对站点
 * GET /host/4、GET /remf_cloud/4、GET /host/4/specific_info 的登录态实测。
 * 详情页展示「已订购的配置 + 支付信息」，不是购买页。
 */
class PurchasedDetailParserTest {

    private fun parse(s: String) = JsonParser.parseString(s).asJsonObject

    private val hostJson = """
        {"host":{
          "id":4,"order_id":25,"product_id":24,"name":"ZY74044",
          "product_name":"US | 美国大陆优化大宽带8核8G",
          "first_payment_amount":"29.50","renew_amount":"29.50",
          "billing_cycle_name":"月付","pay_type":"recurring_prepayment",
          "active_time":1794399603,"due_time":1796991790,"status":"Active",
          "base_info":"8核-8G-70G","auto_renew":0,
          "addition":{"country_id":226,"city":"美国","area":"美国4837",
            "image_name":"debian13-定制版","username":"root","password":"secret","port":22}
        },"self_defined_field":[]}
    """.trimIndent()

    private val cloudJson = """
        {"type":"host","ip":"38.55.157.115","ip_num":0,"ipv6_num":0,
         "power_status":"on","cpu":8,"memory":8,
         "system_disk":{"size":70,"type":""},"data_disk":{"count":0,"total_size":0},
         "line":{"id":746,"name":"宽带"},"bw":300,"gpu":"",
         "username":"root","password":"secret",
         "image":{"id":10241,"name":"debian13-定制版"},
         "security_group":{"id":1,"name":"安全组"}}
    """.trimIndent()

    private val specificJson = """
        {"id":4,"name":"ZY74044","renew_amount":"29.5000","billing_cycle_name":"月付",
         "due_time":1796991790,"ip_num":1,"dedicate_ip":"38.55.157.115",
         "assign_ip":"","country":"美国","country_code":"US","country_id":226,
         "city":"美国","area":"美国4837"}
    """.trimIndent()

    @Test
    fun `合并三接口得到已订购配置与支付信息`() {
        val d = ServiceParser.purchasedDetail(
            parse(hostJson), parse(cloudJson), parse(specificJson)
        )
        assertNotNull(d)
        d!!
        // 支付信息
        assertEquals("4", d.id)
        assertEquals("ZY74044", d.name)
        assertEquals("25", d.orderId)
        assertEquals("29.50", d.firstPaymentAmount)
        assertEquals("29.50", d.renewAmount)
        assertEquals("月付", d.billingCycleName)
        assertFalse(d.autoRenew)
        assertTrue(d.activeTime.isNotBlank())
        assertTrue(d.dueTime.isNotBlank())
        // 配置信息（图二第一张卡片）
        assertTrue(d.hasCloudConfig)
        assertEquals("8", d.cpu)
        assertEquals("8", d.memory)
        assertEquals("300", d.bw)
        assertEquals("70", d.systemDisk)
        assertEquals("debian13-定制版", d.imageName)
        assertEquals("on", d.powerStatus)
        assertEquals("宽带", d.lineName)
        assertEquals("安全组", d.securityGroup)
        assertEquals("1", d.ipNum)
        assertEquals("38.55.157.115", d.ip)
        assertEquals("root", d.username)
        assertEquals("secret", d.password)
        assertEquals("22", d.port)
        assertEquals("美国", d.country)
        assertEquals("美国4837", d.area)
    }

    @Test
    fun `非 mf_cloud 商品无实例配置时降级只展示支付信息`() {
        val d = ServiceParser.purchasedDetail(parse(hostJson), null, null)
        assertNotNull(d)
        d!!
        assertFalse(d.hasCloudConfig)
        assertEquals("", d.cpu)
        assertEquals("29.50", d.renewAmount)
        assertEquals("月付", d.billingCycleName)
        // addition 里的信息仍然可用
        assertEquals("root", d.username)
        assertEquals("debian13-定制版", d.imageName)
        assertEquals("22", d.port)
        assertEquals("美国", d.city)
    }

    @Test
    fun `host 主体缺失时返回 null`() {
        assertNull(ServiceParser.purchasedDetail(null, parse(cloudJson), null))
        assertNull(ServiceParser.purchasedDetail(JsonParser.parseString("{}").asJsonObject, null, null))
    }
}
