package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工单中心解析测试（v1.4.3）。
 * 样例来自 2026-11 对站点插件 idcsmart_ticket 的登录态实测：
 * GET /ticket/statistic、/ticket/status、/ticket/{id} 等。
 */
class TicketParserTest {

    private fun el(s: String): JsonElement = JsonParser.parseString(s)

    @Test
    fun `状态字典解析颜色默认值与归类`() {
        val list = TicketParser.statuses(
            el(
                """{"list":[
                    {"id":1,"name":"待接单","color":"#ff0000","status":0,"default":1},
                    {"id":4,"name":"已关闭","color":"#999999","status":1,"default":0}
                ]}""".trimIndent()
            )
        )
        assertEquals(2, list.size)
        assertEquals("1", list[0].id)
        assertEquals("待接单", list[0].name)
        assertTrue(list[0].isDefault)
        assertEquals(0, list[0].groupStatus)
        assertEquals(1, list[1].groupStatus)
        assertFalse(list[1].isDefault)
        // ARGB 解析：#ff0000 -> 0xFFFF0000
        assertEquals(4294901760L, list[0].colorArgb()!!)
        assertNull(TicketOption("1", "", color = "bad").colorArgb())
    }

    @Test
    fun `类型与部门字典兼容数字 id`() {
        val types = TicketParser.types(el("""{"list":[{"id":2,"name":"技术问题"},{"id":3,"name":"财务"}]}"""))
        val departments = TicketParser.departments(el("""{"list":[{"id":1,"name":"客服部"}]}"""))
        assertEquals(2, types.size)
        assertEquals("技术问题", types[0].name)
        assertEquals("3", types[1].id)
        assertEquals(1, departments.size)
        assertEquals("客服部", departments[0].name)
    }

    @Test
    fun `统计映射汇总与在办数`() {
        val s = TicketParser.statistic(el("""{"1":3,"2":1,"3":2,"4":5}"""))
        assertEquals(11, s.total)
        assertEquals(3, s.countOf("1"))
        assertEquals(0, s.countOf("99"))
        // 状态 4 视为已关闭，不计入在办
        assertEquals(6, s.openCount)
        assertTrue(TicketParser.statistic(null).total == 0)
    }

    @Test
    fun `通知配置兼容数字与字符串`() {
        val a = TicketParser.config(el("""{"ticket_notice_open":1,"ticket_notice_description":"请勿重复提交"}"""))
        assertTrue(a.noticeOpen)
        assertEquals("请勿重复提交", a.noticeDescription)
        val b = TicketParser.config(el("""{"ticket_notice_open":"0"}"""))
        assertFalse(b.noticeOpen)
    }

    @Test
    fun `HTML 正文清洗为纯文本`() {
        assertEquals("", TicketParser.plainText(""))
        assertEquals(
            "你好\n世界 !",
            TicketParser.plainText("<p>你好<br>世界&nbsp;!</p><script>alert(1)</script>")
        )
        assertTrue(!TicketParser.plainText("<div>A</div><div>B</div>").contains("<"))
        assertEquals("a&b", TicketParser.plainText("a&amp;b"))
    }

    @Test
    fun `详情解析回复时间轴与引用回复`() {
        val d = TicketParser.detail(
            el(
                """{"ticket":{
                    "id":12,"ticket_num":"TK20261113001","title":"无法开机",
                    "status":"已回复","status_id":"3","color":"#ff9800",
                    "ticket_type_id":"2","create_time":1794399603,"can_operate":1,
                    "host_ids":[4,5],
                    "replies":[
                      {"id":101,"type":"Client","client_name":"我",
                       "content":"<p>机器起不来<br>麻烦看下</p>","create_time":1794399603,
                       "attachment":["a.png"],"quote_reply_id":"0"},
                      {"id":102,"type":"Admin","admin_name":"客服01",
                       "content":"已重启","create_time":1794400000,"quote_reply_id":"101",
                       "quote_info":{"id":101,"sender_name":"我","create_time":1794399603,
                         "content":"机器起不来","is_deleted":0}}
                    ]
                }}""".trimIndent()
            )
        )
        assertTrue(d != null)
        d!!
        assertEquals("12", d.id)
        assertEquals("TK20261113001", d.displayNum)
        assertEquals("无法开机", d.title)
        assertFalse(d.isClosed)
        assertTrue(d.canOperate)
        assertEquals(listOf("4", "5"), d.hostIds)
        assertTrue(d.createTime.contains("-"))
        assertEquals(2, d.replies.size)
        assertFalse(d.replies[0].isAdmin)
        assertEquals("我", d.replies[0].authorName)
        assertEquals("机器起不来\n麻烦看下", d.replies[0].content)
        assertEquals(listOf("a.png"), d.replies[0].attachments)
        assertTrue(d.replies[1].isAdmin)
        assertEquals("客服01", d.replies[1].authorName)
        assertEquals("已重启", d.replies[1].content)
        assertEquals("101", d.replies[1].quoteReplyId)
        assertEquals("机器起不来", d.replies[1].quote?.content)
        assertFalse(d.replies[1].quote!!.isDeleted)
        assertNull(TicketParser.detail(el("""{"ticket":{}}""")))
    }

    @Test
    fun `关闭态由 status_id 或中文状态判定`() {
        val d = TicketDetail(id = "1", statusId = "4")
        assertTrue(d.isClosed)
        assertTrue(TicketDetail(id = "2", status = "已关闭").isClosed)
        assertFalse(TicketDetail(id = "3", statusId = "3", status = "处理中").isClosed)
        assertEquals("1", TicketDetail(id = "1").displayNum)
    }

    @Test
    fun `创建与回复请求体对齐站点协议`() {
        val create = TicketBodies.create("标题", "2", "内容", listOf("4", "5"), listOf("f.png"))
        assertEquals("标题", create.get("title").asString)
        assertEquals(2L, create.get("ticket_type_id").asLong)
        assertEquals(2, create.getAsJsonArray("host_ids").size())
        assertEquals("f.png", create.getAsJsonArray("attachment").get(0).asString)
        assertEquals("内容", create.get("content").asString)

        val reply = TicketBodies.reply("第一行\n第二行", "101", listOf("x.png"))
        assertEquals("第一行<br>第二行", reply.get("content").asString)
        assertEquals(101L, reply.get("quote_reply_id").asLong)
        assertEquals(1, reply.getAsJsonArray("attachment").size())

        assertEquals(9L, TicketBodies.action("9").get("id").asLong)
        assertEquals("abc", TicketBodies.action("abc").get("id").asString)
    }
}
