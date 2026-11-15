package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.21 独立优惠码中心解析器回归。
 * HTML 片段取自 vps.qingyayun.cn/cart/goodsList.htm 的真实「产品区公告」结构（2026-11 实测）。
 */
class PromoParserTest {

    /** 构造一个真实结构的专区公告块（PC 与移动两份重复，测去重）。 */
    private fun zoneHtml(zone: String, code: String, extra: String = "一个账号只能用一次，长期有效"): String {
        val block = """
            <!-- 产品区公告 -->
            <div class="mx-6 border rounded-t-md px-4 py-2.5 mt-4 bg-gradient-to-b from-blue-50 to-white">
                <h2 class="text-lg font-bold"><i class="far fa-bell mr-2 bg-blue-600"></i>$zone ▪ 公告</h2>
            </div>
            <div class="mx-6 bg-white px-4 py-2.5 border flex justify-between items-center gap-2">
                <p class="shrink-0 text-xs border bg-gray-100 font-bold">优惠</p>
                <p class="min-w-0 flex-1 break-all text-wrap text-gray-600 text-sm">
                    9折优惠码：$code，$extra（续费也是优惠后的价格）
                </p>
                <p class="shrink-0 text-xs">2026-11-15</p>
            </div>
            <!-- 产品区公告End -->
        """.trimIndent()
        return "<html><body>$block$block</body></html>" // 故意重复两次
    }

    @Test
    fun `解析真实专区公告里的优惠码`() {
        val html = zoneHtml("国内NAT云-1区", "Qwy202652")
        val items = PromoParser.fromZoneHtml(html, "14", "15", "https://example/goodsList.htm?fpg_id=14&spg_id=15")
        assertEquals(1, items.size) // 重复块被去重
        val item = items[0]
        assertEquals("Qwy202652", item.code)
        assertEquals("9折", item.discountText)
        assertEquals("国内NAT云-1区", item.zoneName)
        assertEquals("15", item.spgId)
        assertEquals("14", item.fpgId)
        assertEquals("2026-11-15", item.date)
        assertTrue(item.note.contains("一个账号只能用一次"))
    }

    @Test
    fun `解析站点真实公告块原文`() {
        val realHtml = """
            <!-- 产品区公告 -->
                    <div>
                    <div class="mx-6 border rounded-t-md px-4 py-2.5 mt-4 bg-gradient-to-b from-blue-50 to-white">
                        <h2 class="text-lg font-bold"><i class="far fa-bell mr-2 bg-blue-600 text-white px-2 py-1.5 rounded-md"></i>国内NAT云-1区 ▪ 公告</h2>
                    </div>
                            <div class="mx-6 bg-white px-4 py-2.5 border flex justify-between items-center gap-2">
                        <p class="shrink-0 text-xs border bg-gray-100 py-1 px-1.5 rounded-md font-bold">优惠</p>
                        <p class="min-w-0 flex-1 break-all text-wrap text-gray-600 text-sm">
                                            9折优惠码：Qwy202652，一个账号只能用一次，长期有效（续费也是优惠后的价格）                            </p>
                        <p class="shrink-0 text-xs">2026-11-15</p>
                    </div>
                        </div>
                    <!-- 产品区公告End -->
        """.trimIndent()
        val items = PromoParser.fromZoneHtml(realHtml, "14", "15", "url")
        assertEquals(1, items.size)
        assertEquals("Qwy202652", items[0].code)
        assertEquals("9折", items[0].discountText)
        assertEquals("国内NAT云-1区", items[0].zoneName)
        assertEquals("2026-11-15", items[0].date)
    }

    @Test
    fun `多个专区公告各自解析`() {
        val html = zoneHtml("宁波精品云", "Qwy202616", "可以重复使用，长期有效")
        val items = PromoParser.fromZoneHtml(html, "18", "19", "url")
        assertEquals(1, items.size)
        assertEquals("Qwy202616", items[0].code)
        assertTrue(items[0].note.contains("可以重复使用"))
    }

    @Test
    fun `无公告块返回空`() {
        val items = PromoParser.fromZoneHtml("<html><body>该分类下暂无产品</body></html>", "14", "17", "url")
        assertTrue(items.isEmpty())
    }

    @Test
    fun `公告块无优惠码返回空`() {
        val html = """
            <!-- 产品区公告 -->
            <div><h2>某专区 ▪ 公告</h2><p>本周维护通知，请知悉</p></div>
            <!-- 产品区公告End -->
        """.trimIndent()
        val items = PromoParser.fromZoneHtml(html, "1", "3", "url")
        assertTrue(items.isEmpty())
    }

    @Test
    fun `apply成功解析抵扣金额`() {
        val data = JsonParser.parseString("""{"discount":12.5}""")
        val result = PromoParser.applyResult(data, 200, "success")
        assertTrue(result.usable)
        assertEquals(12.5, result.discount, 0.001)
    }

    @Test
    fun `apply失败 discount为零`() {
        val data = JsonParser.parseString("""{}""")
        val result = PromoParser.applyResult(data, 400, "addon_promo_code_has_expired")
        assertFalse(result.usable)
        assertEquals("addon_promo_code_has_expired", result.msg)
    }
}
