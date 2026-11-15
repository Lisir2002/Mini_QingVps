package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.20：消息中心 + 选购页辅助信息的解析回归。
 *
 * 关键点全部来自站点真实响应结构（2026-11 实测）：
 *  - 公告 / 新闻 / 帮助的数据都在 `data.list`，不是 data 本身即数组；
 *  - 站内信已读字段为 is_read，1=已读，0=未读；
 *  - 优惠券金额/门槛/到期字段名多候选；
 *  - 区域机器须知藏在商品 description 的 div.pd-br 里（地区/IP/说明）。
 */
class MessageCenterParserTest {

    private fun json(s: String) = JsonParser.parseString(s)

    @Test
    fun `公告解析 data list 不再为空`() {
        val data = json("""{"list":[{"id":21,"title":"中秋放假公告","create_time":"1680240772"},{"id":20,"title":"支付升级公告","create_time":"1680240744"}]}""")
        val items = MessageParser.announcements(data)
        assertEquals(2, items.size)
        assertEquals("21", items[0].id)
        assertEquals("中秋放假公告", items[0].title)
        assertEquals(MessageKind.ANNOUNCEMENT, items[0].kind)
        assertEquals(1680240772L, items[0].createTime)
    }

    @Test
    fun `新闻解析 data list`() {
        val data = json("""{"list":[{"id":1,"title":"示例","type":"新闻公告","create_time":"1673576058"}],"count":1}""")
        val items = MessageParser.news(data)
        assertEquals(1, items.size)
        assertEquals(MessageKind.NEWS, items[0].kind)
        assertEquals("新闻公告", items[0].type)
    }

    @Test
    fun `帮助资源展平分组下的 helps`() {
        val data = json("""{"list":[{"id":1,"name":"隐私服务协议","helps":[{"id":1,"title":"隐私协议"},{"id":2,"title":"服务协议"}]},{"id":2,"name":"注册与登陆","helps":[{"id":3,"title":"如何注册账号"}]}]}""")
        val items = MessageParser.resources(data)
        assertEquals(3, items.size)
        assertEquals("隐私服务协议", items[0].groupName)
        assertEquals("如何注册账号", items[2].title)
        assertEquals(MessageKind.RESOURCE, items[2].kind)
    }

    @Test
    fun `公告详情从 data announcement 提取并解析段落`() {
        val data = json("""{"announcement":{"id":"21","title":"放假公告","content":"<p>尊敬的用户</p><p>假期安排见下</p>"}}""")
        val detail = MessageParser.articleDetail(data, MessageKind.ANNOUNCEMENT) { html ->
            html.split("<p>").drop(1).map { it.substringBefore("</p>").trim() }.filter { it.isNotBlank() }
        }
        assertEquals("21", detail!!.id)
        assertEquals("放假公告", detail.title)
        assertEquals(2, detail.paragraphs.size)
    }

    @Test
    fun `站内信已读未读按 is_read 解析`() {
        val data = json("""{"list":[{"id":"1","title":"工单回复","content":"<p>已回复</p>","is_read":"0","create_time":"1680240772"},{"id":"2","title":"支付成功","message":"已完成","is_read":"1"}]}""")
        val mails = MessageParser.mails(data)
        assertEquals(2, mails.size)
        assertFalse(mails[0].read)
        assertTrue(mails[1].read)
        assertEquals("已回复", mails[0].summary)
    }

    @Test
    fun `站内信概要未读数回退按列表计数`() {
        val list = MessageParser.mails(json("""{"list":[{"id":"1","is_read":"0"},{"id":"2","is_read":"1"},{"id":"3","is_read":"0"}]}"""))
        val summary = MessageParser.mailSummary(null, list)
        assertEquals(2, summary.unread)
        assertEquals(3, summary.total)
    }

    @Test
    fun `优惠券多候选字段宽松解析`() {
        val data = json("""{"list":[{"id":"7","name":"满减券","amount":"10.00","condition":"100","expire_time":"2026-12-31","code":"ABC"}]}""")
        val coupons = MessageParser.coupons(data)
        assertEquals(1, coupons.size)
        assertEquals("满减券", coupons[0].name)
        assertEquals("10.00", coupons[0].amount)
        assertEquals("100", coupons[0].condition)
        assertEquals("ABC", coupons[0].code)
    }

    @Test
    fun `优惠券 data 本身为数组也兼容`() {
        val data = json("""[{"id":"8","title":"折扣券","money":"5"}]""")
        val coupons = MessageParser.coupons(data)
        assertEquals(1, coupons.size)
        assertEquals("折扣券", coupons[0].name)
        assertEquals("5", coupons[0].amount)
    }

    @Test
    fun `区域机器须知从商品 description 提取`() {
        val html = """
            <!DOCTYPE html><html><body>
            处理器：Platinum铂金系列
            <div class="pd-br">核心内存：4核8G</div>
            <div class="pd-br">独立IP：1个[100G防御]<br />IP性质：IPV4[域名人工白名单]<br />说明：BGP三网可通|电信专线|送Beian域名</div>
            </body></html>
        """.trimIndent()
        val notices = CatalogParser.regionNoticesFromHtml(html)
        assertTrue(notices.any { it.contains("独立IP") || it.contains("IP性质") || it.contains("BGP三网可通") })
        // 纯规格行（核心内存）不应算作区域须知
        assertFalse(notices.any { it.startsWith("核心内存") })
    }
}
