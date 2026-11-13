package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.10 公告详情解析测试：GET /news/{id} → data.news，
 * 正文整页 HTML 按块级标签分段为纯文本段落。
 */
class AnnouncementDetailParserTest {

    private fun el(json: String) = JsonParser.parseString(json)

    @Test
    fun `newsDetail parses data-news object`() {
        val json = """
        {"status":200,"msg":"ok","data":{"news":{
            "id":1,"addon_idcsmart_news_type_id":1,"type":"新闻公告",
            "title":"示例","content":"<p>这是一篇示例</p>",
            "keywords":"","attachment":[],"create_time":1673576058,
            "prev":{},"next":{}
        }}}
        """.trimIndent()
        // 解析器接收 envelope.data（仓库层已剥掉外层 status/msg）
        val data = el(json).asJsonObject.get("data")
        val detail = CatalogParser.newsDetail(data)
        assertNotNull(detail)
        assertEquals("1", detail!!.id)
        assertEquals("示例", detail.title)
        assertEquals("新闻公告", detail.type)
        assertEquals(1673576058L, detail.createTime)
        assertEquals(listOf("这是一篇示例"), detail.paragraphs)
    }

    @Test
    fun `newsDetail returns null when news missing`() {
        assertNull(CatalogParser.newsDetail(el("""{"status":404,"msg":"不存在"}""")))
        assertNull(CatalogParser.newsDetail(null))
    }

    @Test
    fun `paragraphs split on p div br and headings`() {
        val html = "<p>第一段</p><div>第二段</div><br><h3>标题行</h3><li>列表项</li>"
        val paragraphs = CatalogParser.paragraphsFromHtml(html)
        assertEquals(listOf("第一段", "第二段", "标题行", "列表项"), paragraphs)
    }

    @Test
    fun `paragraphs keep full document with nested body html`() {
        val html = """
        <!DOCTYPE html><html><head></head><body>
        <div><p>尊敬的用户：</p></div>
        <p style="text-align: justify;"><span style="font-family: 宋体">维修期间实例无法访问。</span></p>
        <script>ignore me</script>
        </body></html>
        """.trimIndent()
        val paragraphs = CatalogParser.paragraphsFromHtml(html)
        assertTrue(paragraphs.contains("尊敬的用户："))
        assertTrue(paragraphs.contains("维修期间实例无法访问。"))
        assertTrue(paragraphs.none { it.contains("ignore me") })
        assertTrue(paragraphs.none { it.contains("<") })
    }

    @Test
    fun `paragraphs decode named and numeric entities`() {
        val html = "<p>A &amp; B&nbsp;C &#39;quote&#39; &#65;</p>"
        val paragraphs = CatalogParser.paragraphsFromHtml(html)
        assertEquals(listOf("A & B C 'quote' A"), paragraphs)
    }

    @Test
    fun `paragraphs collapse whitespace and drop blank lines`() {
        val html = "<p>  多   空格   行  </p>\n<p></p><p>   </p><p>尾段</p>"
        val paragraphs = CatalogParser.paragraphsFromHtml(html)
        assertEquals(listOf("多 空格 行", "尾段"), paragraphs)
    }

    @Test
    fun `paragraphs empty for blank or plain missing content`() {
        assertTrue(CatalogParser.paragraphsFromHtml("").isEmpty())
        assertTrue(CatalogParser.paragraphsFromHtml("   ").isEmpty())
    }
}
