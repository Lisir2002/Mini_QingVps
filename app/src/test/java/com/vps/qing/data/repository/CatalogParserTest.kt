package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 全部样本来自 2026-11 对站点的匿名实测响应，字段不是猜测。 */
class CatalogParserTest {

    private fun json(raw: String) = JsonParser.parseString(raw)

    @Test
    fun `first groups parse real payload with numeric ids`() {
        val data = json(
            """{"list":[{"id":1,"name":"🎉活动 | 2026特惠区","type":""},
               {"id":9,"name":"CDN | 站点加速-高防","type":""}],"count":"2"}"""
        )
        val groups = CatalogParser.firstGroups(data)
        assertEquals(2, groups.size)
        assertEquals("1", groups[0].id)
        assertEquals("🎉活动 | 2026特惠区", groups[0].name)
        assertEquals("9", groups[1].id)
    }

    @Test
    fun `second groups keep parent id and description`() {
        val data = json(
            """{"list":[{"id":"4","name":"✅代理加盟","parent_id":"1","type":"",
               "description":"欢迎加盟清婉云！！"},
               {"id":"3","name":"🌹 双11▪限当天","parent_id":1,"description":""}],"count":"6"}"""
        )
        val groups = CatalogParser.secondGroups(data)
        assertEquals(2, groups.size)
        assertEquals("4", groups[0].id)
        assertEquals("1", groups[0].parentId)
        assertEquals("欢迎加盟清婉云！！", groups[0].description)
        assertEquals("1", groups[1].parentId)
    }

    @Test
    fun `product parses fields and extracts specs from description html`() {
        val data = json(
            """{"list":[{"id":45,"name":"APAC | 亚太-体验版",
               "description":"<div class=\"pd-br\">节点数：35+</div> <div class=\"pd-br\">月流量：50GiB</div>",
               "stock_control":0,"qty":24,"pay_type":"recurring_prepayment","price":"1.00",
               "cycle":"月付","module":null,
               "product_group_name_second":"QY▪CDN加速|高防","product_group_id_second":10,
               "product_group_name_first":"CDN | 站点加速-高防","product_group_id_first":9}],"count":"20"}"""
        )
        val list = CatalogParser.products(data)
        assertEquals(1, list.size)
        val p = list[0]
        assertEquals("45", p.id)
        assertEquals("APAC | 亚太-体验版", p.name)
        assertEquals("1.00", p.price)
        assertEquals(1.0, p.unitPrice, 0.0001)
        assertEquals("月付", p.cycle)
        assertEquals("24", p.qty)
        assertFalse(p.stockControl)
        assertEquals("10", p.groupSecondId)
        assertEquals("QY▪CDN加速|高防", p.groupSecondName)
        assertEquals("9", p.groupFirstId)
        assertEquals(2, p.specs.size)
        assertTrue(p.specs.contains("节点数：35+"))
        assertTrue(p.specs.contains("月流量：50GiB"))
    }

    @Test
    fun `specsFromHtml splits br tags strips span and decodes entities`() {
        val html = """<div class="pd-br">节点数：35+</div>
            <div class="pd-br">机房：亚太节点<br />系统：CNAME&nbsp;接入<br />
            <span style="color:#f00">说明：仅供测试</span></div>"""
        val specs = CatalogParser.specsFromHtml(html)
        assertTrue(specs.contains("节点数：35+"))
        assertTrue(specs.contains("机房：亚太节点"))
        assertTrue(specs.contains("系统：CNAME 接入"))
        assertTrue(specs.any { it.contains("说明：仅供测试") })
        // 不能把标签本身当成配置文本
        assertFalse(specs.any { it.contains("<span") || it.contains("<div") })
    }

    @Test
    fun `news parses real payload`() {
        val data = json(
            """{"list":[{"id":1,"title":"示例","img":"","type":"新闻公告",
               "create_time":1673576058,"update_time":1675318352}],"count":"1"}"""
        )
        val news = CatalogParser.news(data)
        assertEquals(1, news.size)
        assertEquals("1", news[0].id)
        assertEquals("示例", news[0].title)
        assertEquals("新闻公告", news[0].type)
        assertEquals(1673576058L, news[0].createTime)
    }

    @Test
    fun `menu parses nested child entries`() {
        val data = json(
            """{"menu":[{"name":"订购产品","url":"cart/goodsList.htm","parent_id":0,
               "child":[{"name":"云服务器","url":"cart/goodsList.htm?fpg_id=1"}]},
               {"name":"资源中心","url":"plugin/21/source.htm","child":[]}]}"""
        )
        val menu = CatalogParser.menu(data)
        assertEquals(2, menu.size)
        assertEquals("订购产品", menu[0].name)
        assertEquals("cart/goodsList.htm", menu[0].url)
        assertEquals(1, menu[0].children.size)
        assertEquals("云服务器", menu[0].children[0].name)
        assertTrue(menu[1].children.isEmpty())
    }

    @Test
    fun `parsers tolerate array payload null and unknown shapes`() {
        val bare = json("""[{"id":"7","name":"裸数组"}]""")
        assertEquals(1, CatalogParser.firstGroups(bare).size)
        assertEquals("7", CatalogParser.firstGroups(bare)[0].id)

        assertTrue(CatalogParser.products(null).isEmpty())
        assertTrue(CatalogParser.news(json("""{"list":[]}""")).isEmpty())
        assertTrue(CatalogParser.menu(json("""{"menu":[]}""")).isEmpty())

        val hosts = CatalogParser.hosts(
            json("""{"list":[{"id":12,"product_name":"VPS-1","status":"正常"}]}""")
        )
        assertEquals(1, hosts.size)
        assertEquals("12", hosts[0].id)
        assertEquals("VPS-1", hosts[0].productName)
        assertEquals("正常", hosts[0].status)
        assertTrue(CatalogParser.hosts(json("{}")).isEmpty())
    }
}
