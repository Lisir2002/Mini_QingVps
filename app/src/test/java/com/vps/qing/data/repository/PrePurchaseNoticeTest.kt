package com.vps.qing.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 购前须知解析单测（v1.4.27）：解析专区页 SSR「公告·购买前必读」区块。
 */
class PrePurchaseNoticeTest {

    /** 站点真实结构：PC 端 announcementContentPC + 手机端重复块。 */
    private val sampleHtml = """
        <div class="公告">
          <p>公告<br><span>购买前必读</span></p>
        </div>
        <div id="announcementContentPC" class="bg-white p-2 overflow-auto">
          <p class="font-bold">基础服务条例（没有隐藏霸王条款，全透明）</p>
          <p>一、退款规则</p>
          <p>1. 常规区：24 小时无理由退款，手续费 = 购买价/30天</p>
          <p>2. 活动区：未标注不退款机型 24 小时内可退</p>
          <p>二、使用须知</p>
          <p>1. Debian、Ubuntu 系统 SSH 连接不上，大概率是掉网卡了</p>
          <p class="text-red-600 font-bold">【重要】数据需要自己异地备份</p>
        </div>
        <div id="announcementContent" class="mobile">
          <p>一、退款规则</p>
        </div>
        <div>商品分类</div>
    """.trimIndent()

    @Test
    fun `解析购买前必读_提取全部条款`() {
        val list = PrePurchaseNoticeParser.parse(sampleHtml)
        // 优先 PC 块（7 段），不应混入手机端/商品分类
        assertTrue(list.size >= 6)
        assertTrue(list.any { it.text.contains("基础服务条例") })
        assertTrue(list.any { it.text.contains("一、退款规则") })
        assertTrue(list.any { it.text.contains("二、使用须知") })
        assertFalse(list.any { it.text.contains("商品分类") })
    }

    @Test
    fun `章节标题被标记为heading`() {
        val list = PrePurchaseNoticeParser.parse(sampleHtml)
        assertTrue(list.first { it.text.contains("一、退款规则") }.heading)
        assertTrue(list.first { it.text.contains("二、使用须知") }.heading)
        assertFalse(list.first { it.text.contains("24 小时无理由退款") }.heading)
    }

    @Test
    fun `清洗HTML标签与样式残留`() {
        val list = PrePurchaseNoticeParser.parse(sampleHtml)
        list.forEach { p ->
            assertFalse(p.text.contains("<"))
            assertFalse(p.text.contains("class="))
            assertFalse(p.text.contains("font-bold"))
        }
    }

    @Test
    fun `无公告块返回空列表`() {
        assertTrue(PrePurchaseNoticeParser.parse("").isEmpty())
        assertTrue(PrePurchaseNoticeParser.parse("<div>没有任何公告</div>").isEmpty())
    }

    @Test
    fun `手机端id也能兜底解析`() {
        val mobileOnly = """
            <div id="announcementContent"><p>一、退款规则</p><p>24小时无理由</p></div>
        """.trimIndent()
        val list = PrePurchaseNoticeParser.parse(mobileOnly)
        assertEquals(2, list.size)
        assertTrue(list[0].heading)
    }
}
