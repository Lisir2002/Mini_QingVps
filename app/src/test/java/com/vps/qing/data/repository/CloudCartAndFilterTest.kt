package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 云端购物车解析 + 分类本地过滤单测。
 *
 * 背景：站点 GET /product 完全忽略 fpg_id/spg_id 参数（恒返回全量 20 条），
 * 每条商品自带 product_group_id_first/second，因此分类过滤在 [CatalogParser.byGroup]
 * 本地完成——这是 v1.2.0 修复「点任意分类都全显示」的核心。
 */
class CloudCartAndFilterTest {

    private fun product(id: String, first: String?, second: String?) = ProductInfo(
        id = id, name = "商品$id",
        groupFirstId = first, groupFirstName = first?.let { "一级$it" },
        groupSecondId = second, groupSecondName = second?.let { "二级$it" }
    )

    private val all = listOf(
        product("1", "1", "7"),
        product("2", "1", "7"),
        product("3", "1", "8"),
        product("4", "9", "10"),
        product("5", null, null)
    )

    @Test
    fun `filter by second group only returns its products`() {
        val result = CatalogParser.byGroup(all, "1", "7")
        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun `filter falls back to first group when second has no match`() {
        // spg=8 在样本里只有商品 3；spg=9 无二级命中时回退一级 9
        val result = CatalogParser.byGroup(all, "9", "9999")
        assertEquals(listOf("4"), result.map { it.id })
    }

    @Test
    fun `filter by first group alone`() {
        val result = CatalogParser.byGroup(all, "1", null)
        assertEquals(listOf("1", "2", "3"), result.map { it.id })
    }

    @Test
    fun `no selection returns everything`() {
        assertEquals(all, CatalogParser.byGroup(all, null, null))
        assertEquals(all, CatalogParser.byGroup(all, "", ""))
    }

    @Test
    fun `ungrouped products stay visible only in the catch-all view`() {
        assertTrue(CatalogParser.byGroup(all, "1", "7").none { it.groupFirstId == null })
    }

    // ---------------- 云端购物车 ----------------

    private val config = ConfigParser.parse("45", JsonParser.parseString("""
    {
      "common_product": {"name": "APAC | 亚太-体验版"},
      "configoptions": [
        {"id": "12568", "option_name": "流量", "option_type": "select",
         "subs": [{"id": 45697, "option_name": "50G"}]}
      ],
      "custom_cycles": [{"id": 3706, "name": "月付", "cycle_amount": 25, "is_default": 1}]
    }
    """.trimIndent()))

    private val cartLine = """
    {
      "list": [
        {
          "product_id": 45,
          "name": "APAC | 亚太-体验版",
          "qty": "2",
          "description": "<div class='pd-br'>月流量：50GiB</div>",
          "config_options": {
            "configoption": {"12568": 45697},
            "cascade_configoption": [],
            "cycle": 3706
          }
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `cloud cart line is translated via product config`() {
        val items = CloudCartParser.parse(JsonParser.parseString(cartLine)) { config }
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("45", item.productId)
        assertEquals("APAC | 亚太-体验版", item.productName)
        assertEquals(2, item.quantity)
        assertTrue(item.synced)
        assertEquals(0, item.position)
        assertEquals("3706", item.cycleId)
        // 周期 id 翻译成名称，价格来自 custom_cycles[].cycle_amount
        assertEquals("月付", item.cycle)
        assertEquals(25.0, item.unitPrice, 0.001)
        // configoption {选项id: subId} 翻译成「选项名：候选名」
        assertEquals(listOf("流量：50G"), item.configLabels)
        assertEquals(mapOf("12568" to "45697"), item.configOptionRaw)
    }

    @Test
    fun `cloud cart degrades gracefully without config lookup`() {
        val items = CloudCartParser.parse(JsonParser.parseString(cartLine)) { null }
        val item = items[0]
        assertEquals("周期 3706", item.cycle)
        assertEquals(0.0, item.unitPrice, 0.001)
        assertEquals(listOf("配置12568：45697"), item.configLabels)
    }

    @Test
    fun `cloud cart with missing or empty list`() {
        assertTrue(CloudCartParser.parse(JsonParser.parseString("""{"list": []}""")).isEmpty())
        assertTrue(CloudCartParser.parse(JsonParser.parseString("""{}""")).isEmpty())
        assertTrue(CloudCartParser.parse(null).isEmpty())
    }

    @Test
    fun `positions follow list index for cart mutation`() {
        val data = JsonParser.parseString("""
        {"list": [
          {"product_id": 45, "name": "A", "qty": 1, "config_options": {}},
          {"product_id": 46, "name": "B", "qty": 3, "config_options": {}}
        ]}
        """.trimIndent())
        val items = CloudCartParser.parse(data)
        assertEquals(0, items[0].position)
        assertEquals(1, items[1].position)
        assertEquals(3, items[1].quantity)
    }
}
