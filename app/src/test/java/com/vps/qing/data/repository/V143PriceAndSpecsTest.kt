package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.3 选配页价格算法 / 商品规格抓取回归测试。
 *
 * 关键实测结论（2026-11，站点 POST /product/28/config_option）：
 *  - data.price 已包含当前 qty 与当前周期，UI 严禁再乘数量；
 *  - custom_cycles[].cycle_amount 是站点标价，不等于成交价（月付标价 13.33，算价返回 10.00）；
 *  - preview[] 带逐项加价，可用于核对「配置 ↔ 价格」是否对得上。
 */
class V143PriceAndSpecsTest {

    private fun el(s: String): JsonElement = JsonParser.parseString(s)

    private val discountedQuote = """{
        "price":"10.00",
        "billing_cycle":"月付",
        "duration":1,
        "description":"首月优惠",
        "preview":[
          {"name":"CPU","value":"8核","price":"2.00"},
          {"name":"内存","value":"8G","price":"0"},
          {"name":"带宽","value":"300M","price":"1.50"}
        ],
        "base_price":"13.33",
        "discount_order_price":"10.00",
        "setup_fee":"0",
        "renew_price":"13.33",
        "renew_price_total":"13.33",
        "price_total":"10.00",
        "due_time":1796991790,
        "is_natural_month_prepaid":0
    }""".trimIndent()

    @Test
    fun `算价解析折扣续费开通费与逐项明细`() {
        val q = ConfigParser.price(el(discountedQuote))
        assertEquals(10.0, q.price, 0.001)
        assertEquals(10.0, q.total, 0.001)
        assertEquals(10.0, q.perUnit, 0.001)
        assertEquals(13.33, q.basePrice, 0.001)
        assertEquals(0.0, q.setupFee, 0.001)
        assertEquals(13.33, q.renewPrice, 0.001)
        assertEquals(13.33, q.renewTotal, 0.001)
        assertEquals(10.0, q.priceTotal, 0.001)
        assertEquals(1796991790L, q.dueTime)
        assertEquals("月付", q.billingCycle)
        assertEquals("首月优惠", q.description)
        assertFalse(q.isNaturalMonthPrepaid)
        assertTrue(q.discounted)
        assertTrue(q.renewDiffers)
        assertEquals(3, q.lines.size)
        assertEquals("CPU", q.lines[0].name)
        assertEquals("8核", q.lines[0].value)
        assertEquals(2.0, q.lines[0].price, 0.001)
        assertEquals(0.0, q.lines[1].price, 0.001)
        assertEquals(1.5, q.lines[2].price, 0.001)
        assertEquals(listOf("CPU：8核", "内存：8G", "带宽：300M"), q.preview)
    }

    @Test
    fun `缺字段时价格与单价安全回退且不误判折扣`() {
        val q = ConfigParser.price(el("""{"price":"20.01","billing_cycle":"月付"}"""))
        // price_total / discount_order_price / base_price 均缺失 → 全部回退到 price
        assertEquals(20.01, q.total, 0.001)
        assertEquals(20.01, q.perUnit, 0.001)
        assertFalse(q.discounted)
        assertFalse(q.renewDiffers)
        assertTrue(q.lines.isEmpty())
        assertEquals(0L, q.dueTime)
    }

    @Test
    fun `price 已含数量不得再乘 qty`() {
        // 站点实测：qty=1 -> 10.00, qty=2 -> 20.01, qty=3 -> 30.02, qty=5 -> 50.04
        val one = ConfigParser.price(el("""{"price":"10.00","price_total":"10.00"}"""))
        val two = ConfigParser.price(el("""{"price":"20.01","price_total":"20.01"}"""))
        val five = ConfigParser.price(el("""{"price":"50.04","price_total":"50.04"}"""))
        assertEquals(10.0, one.total, 0.001)
        assertEquals(20.01, two.total, 0.001)
        assertEquals(50.04, five.total, 0.001)
        // UI 直接展示 total，不应出现 20.01 * 2 = 40.02 这类翻倍
        assertTrue(two.total < 21.0)
        assertTrue(five.total < 51.0)
    }

    @Test
    fun `自然月预付与自然月标记`() {
        val q = ConfigParser.price(
            el("""{"price":"100","price_total":"100","renew_price":"100","is_natural_month_prepaid":1}""")
        )
        assertTrue(q.isNaturalMonthPrepaid)
        assertFalse(q.renewDiffers)
        assertFalse(q.discounted)
    }

    @Test
    fun `商品规格 HTML 剥离脚本结构标签并提取配置项`() {
        val html = """
            <!DOCTYPE html><html><head><style>.pd{}</style></head><body>
            <div class="pd-br">处理器：8核</div>
            <div>内存：8G<br />带宽：300M</div>
            <p>系统盘：70G&nbsp;SSD</p>
            <script>var a = 1;</script>
            </body></html>
        """.trimIndent()
        val specs = CatalogParser.specsFromHtml(html)
        assertTrue(specs.contains("处理器：8核"))
        assertTrue(specs.contains("内存：8G"))
        assertTrue(specs.contains("带宽：300M"))
        assertTrue(specs.any { it.contains("系统盘：70G") })
        assertFalse(specs.any { it.contains("<") })
        assertFalse(specs.any { it.contains("var a") })
        assertTrue(specs.size <= 16)
        assertEquals(emptyList<String>(), CatalogParser.specsFromHtml(""))
    }

    @Test
    fun `商品指标解析带 res_module 与分组字段`() {
        val products = CatalogParser.products(
            el(
                """{"list":[
                    {"id":28,"name":"美国大宽带","res_module":"idcsmart_common",
                     "product_group_id":2,"qty":10,"pay_ontrial":0,"recommend":1},
                    {"id":31,"name":"云服务器","res_module":"mf_cloud"}
                ]}""".trimIndent()
            )
        )
        assertEquals(2, products.size)
        val common = products.first { it.id == "28" }
        assertFalse(common.isMfCloud)
        assertEquals("2", common.groupId)
        val cloud = products.first { it.id == "31" }
        assertTrue(cloud.isMfCloud)
    }
}
