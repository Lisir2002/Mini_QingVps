package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.15 选配页价格链路深度验证回归测试。
 *
 * 事实来源：站点 POST /product/28/config_option 实测响应
 *   price=10.00, base_price=10.00, discount_order_price=10.00,
 *   renew_price=13.33, base_renew_price=13.33,
 *   discount_renew_price=10.00, renew_price_total=13.33, price_total=10.00
 *
 * 结论：站点的「续费单价」同时存在原价族（renew_price / base_renew_price）与
 * 实付族（discount_renew_price / 用户等级折扣），真正应展示给用户的是实付价；
 * 原价只用于划线对比，不能把未折扣原价伪装成续费价。
 *
 * 另：mf_cloud 套餐型商品的算价接口需登录态 + 完整推荐配置，匿名/配置不全时
 * 返回 400「商品不存在」，故算价失败时回退到 /product 的官方实时标价并标注
 * isEstimated，既不猜测价格，也不留空白「价格待确认」。
 */
class V1415PriceChainTest {

    private fun el(s: String): JsonElement = JsonParser.parseString(s)

    /** 站点实测 id=28 的完整算价响应，含完整续费价格族。 */
    private val realQuote = """{
        "price":"10.00",
        "billing_cycle":"月付",
        "duration":1,
        "description":"首月优惠",
        "preview":[{"name":"CPU","value":"8核","price":"2.00"}],
        "base_price":"10.00",
        "discount_order_price":"10.00",
        "setup_fee":"0",
        "renew_price":"13.33",
        "base_renew_price":"13.33",
        "discount_renew_price":"10.00",
        "renew_price_total":"13.33",
        "price_total":"10.00",
        "due_time":1796991790,
        "is_natural_month_prepaid":0
    }""".trimIndent()

    @Test
    fun `续费单价优先取折扣后实付价而非原价`() {
        val q = ConfigParser.price(el(realQuote))
        // discount_renew_price = 10.00 才是用户真正续费要付的单价
        assertEquals(10.0, q.renewPrice, 0.001)
        // renew_price = 13.33 只是原价，留作划线对比
        assertEquals(13.33, q.renewListPrice, 0.001)
        assertEquals(13.33, q.renewTotal, 0.001)
        assertTrue(q.renewDiscounted)
        // 实付续费价与首期成交单价一致，不应误报「续费涨价」
        assertFalse(q.renewDiffers)
        assertEquals(10.0, q.total, 0.001)
        assertEquals(10.0, q.perUnit, 0.001)
        assertEquals("月付", q.billingCycle)
    }

    @Test
    fun `无 discount_renew_price 时回退到 renew_price 原价且不误判优惠`() {
        val q = ConfigParser.price(
            el(
                """{
                    "price":"10.00","price_total":"10.00","base_price":"10.00",
                    "renew_price":"13.33","renew_price_total":"13.33"
                }"""
            )
        )
        assertEquals(13.33, q.renewPrice, 0.001)
        assertEquals(13.33, q.renewListPrice, 0.001)
        assertFalse(q.renewDiscounted)
        // 续费原价高于首期成交价，应如实提示不同
        assertTrue(q.renewDiffers)
    }

    @Test
    fun `仅有 base_renew_price 时续费原价正确回退`() {
        val q = ConfigParser.price(
            el(
                """{
                    "price":"18.00","base_renew_price":"20.00",
                    "discount_renew_price":"18.00"
                }"""
            )
        )
        assertEquals(18.0, q.renewPrice, 0.001)
        assertEquals(20.0, q.renewListPrice, 0.001)
        assertTrue(q.renewDiscounted)
    }

    @Test
    fun `完全缺续费字段时为零且不展示续费行`() {
        val q = ConfigParser.price(el("""{"price":"18.00"}"""))
        assertEquals(0.0, q.renewPrice, 0.001)
        assertEquals(0.0, q.renewListPrice, 0.001)
        assertFalse(q.renewDiscounted)
        assertFalse(q.renewDiffers)
    }

    @Test
    fun `站点标价兜底如实标注且不伪装成折扣`() {
        // 复刻 ShopViewModel.estimatedQuote() 构造的兜底对象（qty=2，标价 18.00）
        val q = PriceQuote(
            price = 18.0 * 2,
            unitPrice = 18.0,
            basePrice = 18.0,
            billingCycle = "月付",
            priceTotal = 18.0 * 2,
            isEstimated = true
        )
        assertTrue(q.isEstimated)
        assertEquals(36.0, q.total, 0.001)
        assertEquals(18.0, q.perUnit, 0.001)
        // 标价兜底不能伪装成「打折」或「续费优惠」
        assertFalse(q.discounted)
        assertFalse(q.renewDiscounted)
        assertEquals(0.0, q.renewPrice, 0.001)
    }
}
