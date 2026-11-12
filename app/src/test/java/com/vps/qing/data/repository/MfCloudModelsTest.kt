package com.vps.qing.data.repository

import com.google.gson.JsonParser
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.objectAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * mf_cloud（魔方云套餐型）深度适配协议测试。
 *
 * 样本结构全部来自 2026-11 对站点的真实响应：
 *  - GET /product/24/remf_cloud/order_page（美国大陆优化大宽带8核8G）
 *  - GET /product/24/remf_cloud/image
 *  - GET /product/24/remf_cloud/line/746（bw radio 300Mbps）
 *  - POST /product/24/remf_cloud/duration（月付 1432 / 29.50）
 *  - POST /cart 匿名加购实测「添加成功」。
 */
class MfCloudModelsTest {

    private val orderPage = JsonParser.parseString("""
    {
      "data_center": [
        {"id": 226, "iso": "US", "name": "美国",
         "city": [{"name": "美国",
           "area": [{"id": 529, "name": "美国4837",
             "line": [{"id": 746, "name": "宽带", "data_center_id": 529, "bill_type": "bw"}]
           }]}]}
      ],
      "cpu": [{"id": 3677, "value": 8}],
      "memory": [{"id": 3678, "type": "radio", "value": 8}],
      "system_disk": [
        {"id": 3680, "type": "radio", "value": 70},
        {"id": 3681, "type": "radio", "value": 100}
      ],
      "data_disk": [],
      "config": {"support_vpc_network": 1}
    }
    """.trimIndent())

    private val images = JsonParser.parseString("""
    {"list": [
      {"id": 5007, "name": "Ubuntu", "image": [
        {"id": 10243, "name": "Ubuntu-24.04.1-x64", "charge": 0, "price": "0.00"},
        {"id": 10247, "name": "Ubuntu-22.04-x64", "charge": 0, "price": "0.00"}
      ]},
      {"id": 5006, "name": "Debian", "image": [
        {"id": 10244, "name": "Debian-12.0_x64", "charge": 0, "price": "0.00"}
      ]}
    ]}
    """.trimIndent())

    private val line746 = JsonParser.parseString("""
    {"bill_type": "bw", "bw": [{"type": "radio", "value": 300, "min_value": 0, "max_value": 0, "step": 1}]}
    """.trimIndent())

    private fun config(): MfCloudConfig {
        val base = MfParser.parseOrderPage("24", orderPage)
        val merged = MfParser.mergeLineDetail(base.areas, 746, line746)
        return base.copy(imageGroups = MfParser.parseImages(images), areas = merged)
    }

    @Test
    fun `parse order page areas cpu memory disks`() {
        val cfg = config()
        assertEquals(1, cfg.areas.size)
        val area = cfg.areas[0]
        assertEquals(529, area.areaId)
        assertEquals("美国4837", area.areaName)
        assertEquals(746, area.lineId)
        assertEquals("bw", area.billType)
        assertEquals(listOf(8), cfg.cpus)
        assertEquals(listOf(8), cfg.memories)
        assertEquals(listOf(70, 100), cfg.systemDisks)
        assertTrue(cfg.dataDisks.isEmpty())
    }

    @Test
    fun `parse images into two level groups`() {
        val cfg = config()
        assertEquals(listOf("Ubuntu", "Debian"), cfg.imageGroups.map { it.name })
        assertEquals("Ubuntu-24.04.1-x64", cfg.imageName(10243))
        assertNull(cfg.imageName(999))
        assertEquals(3, cfg.images.size)
    }

    @Test
    fun `merge line detail fills bw candidates`() {
        val area = config().areas[0]
        assertEquals(listOf(300), area.bw?.candidates())
        // radio 类型候选恒为单值
        assertEquals(listOf(8), MfRangeOption("radio", 8).candidates())
    }

    @Test
    fun `slider range expands to bounded candidates`() {
        val slider = MfRangeOption("slider", 1, minValue = 1, maxValue = 5, step = 2)
        assertEquals(listOf(1, 3, 5), slider.candidates())
    }

    @Test
    fun `default selection picks first of each dimension`() {
        val sel = MfSelection.default(config())!!
        assertEquals(529, sel.areaId)
        assertEquals(746, sel.lineId)
        assertEquals(8, sel.cpu)
        assertEquals(8, sel.memory)
        assertEquals(70, sel.systemDisk)
        assertEquals(300, sel.bw)
        assertEquals(10243, sel.imageId)
        assertNull(sel.durationId)
    }

    @Test
    fun `config options body matches site protocol`() {
        val body = MfCloudCart.configOptions(MfSelection.default(config())!!)
        assertEquals(529, body.get("data_center_id").asInt)
        assertEquals(8, body.get("cpu").asInt)
        assertEquals(8, body.get("memory").asInt)
        assertEquals(10243, body.get("image_id").asInt)
        assertEquals(746, body.get("line_id").asInt)
        assertEquals(300, body.get("bw").asInt)
        assertEquals(70, body.objectAt("system_disk")?.get("size")?.asInt)
        assertEquals(0, body.arrayAt("data_disk")?.size())
        // bw 计费线路不带 flow 字段
        assertFalse(body.has("flow"))
    }

    @Test
    fun `duration body is flat params plus id`() {
        val body = MfCloudCart.durationBody(MfSelection.default(config())!!, "24")
        assertEquals(24, body.get("id").asInt)
        assertEquals(529, body.get("data_center_id").asInt)
    }

    @Test
    fun `calc body wraps config_options with qty`() {
        val body = MfCloudCart.calcBody(MfSelection.default(config())!!, "24", 2)
        assertEquals(24, body.get("id").asInt)
        assertEquals(2, body.get("qty").asInt)
        assertEquals(300, body.objectAt("config_options")?.get("bw")?.asInt)
    }

    @Test
    fun `cart body carries product id and empty custom fields`() {
        val body = MfCloudCart.cartBody(MfSelection.default(config())!!, "24", 1)
        assertEquals(24, body.get("product_id").asInt)
        assertEquals(1, body.get("qty").asInt)
        assertTrue(body.objectAt("customfield")?.entrySet()?.isEmpty() == true)
        assertTrue(body.objectAt("self_defined_field")?.entrySet()?.isEmpty() == true)
        assertEquals("custom", body.objectAt("config_options")?.get("activeName")?.asString)
    }

    // ---------------- 云端购物车 mf 条目翻译 ----------------

    private val cartLine = """
    {"list": [
      {"product_id": 24, "name": "US | 美国大陆优化大宽带8核8G", "qty": "1",
       "description": "<div class='pd-br'>核心内存：8核8G</div>",
       "config_options": {
         "data_center_id": 529, "cpu": 8, "memory": 8, "image_id": 10243,
         "system_disk": {"size": 70}, "data_disk": [],
         "line_id": 746, "bw": 300, "duration_id": 1432
       }}
    ]}
    """.trimIndent()

    @Test
    fun `mf cart line is translated via mf config`() {
        val cfg = ProductConfig(productId = "24", mf = config()).copy(
            cycles = listOf(
                CycleOption(id = "1432", name = "月付", amount = 29.50, isDefault = true)
            )
        )
        val items = CloudCartParser.parse(JsonParser.parseString(cartLine)) { cfg }
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("24", item.productId)
        assertEquals(0, item.position)
        assertEquals("月付", item.cycle)
        assertEquals(29.50, item.unitPrice, 0.001)
        assertEquals(
            listOf(
                "区域线路：美国 · 美国4837",
                "CPU：8核",
                "内存：8GB",
                "系统盘：70GB",
                "带宽：300Mbps",
                "操作系统：Ubuntu-24.04.1-x64"
            ),
            item.configLabels
        )
        assertEquals("8", item.configOptionRaw["cpu"])
    }

    @Test
    fun `mf cart line without config cache still parses`() {
        val items = CloudCartParser.parse(JsonParser.parseString(cartLine)) { null }
        assertEquals(1, items.size)
        assertEquals("周期 1432", items[0].cycle)
    }

    @Test
    fun `generic configoption lines keep old translation path`() {
        val generic = """
        {"list": [
          {"product_id": 45, "name": "A", "qty": 1,
           "config_options": {"configoption": {"12568": 45697}, "cycle": 3706}}
        ]}
        """.trimIndent()
        val cfg = ConfigParser.parse("45", JsonParser.parseString("""
        {
          "configoptions": [{"id": "12568", "option_name": "流量", "option_type": "select",
            "subs": [{"id": 45697, "option_name": "50G"}]}],
          "custom_cycles": [{"id": 3706, "name": "月付", "cycle_amount": 25}]
        }
        """.trimIndent()))
        val items = CloudCartParser.parse(JsonParser.parseString(generic)) { cfg }
        assertEquals(listOf("流量：50G"), items[0].configLabels)
    }
}
