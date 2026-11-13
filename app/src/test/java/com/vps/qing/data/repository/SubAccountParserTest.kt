package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 子账户管理解析测试（v1.4.3）。
 * 样例来自 2026-11 对站点插件 idcsmart_sub_account 的登录态实测：
 * GET /sub_account/{id}、/auth、/module、/host/all、/country。
 */
class SubAccountParserTest {

    private fun el(s: String): JsonElement = JsonParser.parseString(s)

    @Test
    fun `详情解析基本信息与权限产品 id 列表`() {
        val d = SubAccountParser.detail(
            el(
                """{"account":{
                    "id":7,"username":"sub01","email":"sub@example.com",
                    "phone_code":"86","phone":"13800000000","status":"1",
                    "last_action_time":1794399603,"create_time":1794399603,
                    "visible_product":"host",
                    "project_id":["1","2"],"host_id":[4,5],
                    "module":["mf_cloud","mf_dcim"],"auth":["1","2","3"]
                }}""".trimIndent()
            )
        )
        assertTrue(d != null)
        d!!
        assertEquals("7", d.id)
        assertEquals("sub01", d.username)
        assertEquals("sub@example.com", d.email)
        assertEquals("86", d.phoneCode)
        assertEquals("13800000000", d.phone)
        assertTrue(d.enabled)
        assertEquals("host", d.visibleProduct)
        assertEquals(listOf("1", "2"), d.projectIds)
        assertEquals(listOf("4", "5"), d.hostIds)
        assertEquals(listOf("mf_cloud", "mf_dcim"), d.modules)
        assertEquals(listOf("1", "2", "3"), d.auth)
        assertTrue(d.createTime.contains("-"))
        assertTrue(d.lastActionTime.contains("-"))
        assertNull(SubAccountParser.detail(el("""{"account":{}}""")))
        assertFalse(SubAccountDetail(status = "0").enabled)
        assertTrue(SubAccountDetail(status = "true").enabled)
    }

    @Test
    fun `权限树保留层级并可展平`() {
        val nodes = SubAccountParser.auth(
            el(
                """{"list":[{
                    "id":1,"title":"产品管理","parent_id":0,"url":"/host",
                    "rules":["view"],
                    "child":[
                      {"id":2,"title":"已购产品","parent_id":1,"rules":["view","edit"],"child":[]},
                      {"id":3,"title":"开机","parent_id":1,"rules":["power"],"child":[]}
                    ]
                }]}""".trimIndent()
            )
        )
        assertEquals(1, nodes.size)
        assertEquals("产品管理", nodes[0].title)
        assertFalse(nodes[0].isLeaf)
        assertEquals(listOf("view"), nodes[0].rules)
        assertEquals(2, nodes[0].children.size)
        assertTrue(nodes[0].children[0].isLeaf)
        assertEquals(listOf("view", "edit"), nodes[0].children[0].rules)
        assertEquals(3, nodes[0].flatten().size)
        assertEquals("0", nodes[0].parentId)
    }

    @Test
    fun `关联产品模块与区号列表`() {
        val hosts = SubAccountParser.hosts(
            el("""{"list":[{"id":4,"product_id":24,"product_name":"美国大宽带","name":"ZY74044","status":"Active"}]}""")
        )
        assertEquals(1, hosts.size)
        assertEquals("4", hosts[0].id)
        assertEquals("ZY74044 · 美国大宽带", hosts[0].label)
        assertEquals("#9", HostOption(id = "9").label)

        val modules = SubAccountParser.modules(
            el("""{"list":[{"name":"mf_cloud","display_name":"云服务器","version":"1.0"}]}""")
        )
        assertEquals("mf_cloud", modules[0].name)
        assertEquals("云服务器", modules[0].displayName)

        val countries = SubAccountParser.countries(
            el("""{"list":[{"id":1,"name":"中国","phone_code":"86"},{"id":2,"name":"美国","phone_code":"1"}]}""")
        )
        assertEquals(2, countries.size)
        assertEquals("86", countries[0].phoneCode)
        assertTrue(SubAccountParser.countries(null).isEmpty())
    }

    @Test
    fun `表单校验规则`() {
        assertNull(SubAccountForm(username = "u", email = "a@b.com", password = "p").validate(false))
        assertEquals(
            "请填写子账户用户名",
            SubAccountForm(username = " ").validate(false)
        )
        assertEquals(
            "请填写邮箱或手机号",
            SubAccountForm(username = "u", password = "p").validate(false)
        )
        assertEquals(
            "请设置登录密码",
            SubAccountForm(username = "u", email = "a@b.com").validate(false)
        )
        assertNull(
            SubAccountForm(username = "u", email = "a@b.com").validate(true)
        )
        assertEquals(
            "邮箱格式不正确",
            SubAccountForm(username = "u", email = "abc", password = "p").validate(false)
        )
        assertNull(
            SubAccountForm(username = "u", phone = "13800000000", password = "p").validate(false)
        )
    }

    @Test
    fun `创建编辑请求体与停用状态体`() {
        val form = SubAccountForm(
            username = "sub02", email = "s@b.com", phoneCode = "86", phone = "13900000000",
            password = "secret", visibleProduct = "module",
            projectIds = listOf("1"), hostIds = listOf("4"),
            modules = listOf("mf_cloud"), auth = listOf("1", "2")
        )
        val body = SubAccountBodies.build(form, "7")
        assertEquals("sub02", body.get("username").asString)
        assertEquals(86L, body.get("phone_code").asLong)
        assertEquals("secret", body.get("password").asString)
        assertEquals(7L, body.get("id").asLong)
        assertEquals("module", body.get("visible_product").asString)
        assertEquals(1, body.getAsJsonArray("host_id").size())
        assertEquals(2, body.getAsJsonArray("auth").size())

        // 编辑时留空密码不应下发 password（避免覆盖原密码）
        val edit = SubAccountBodies.build(form.copy(password = ""), "7")
        assertFalse(edit.has("password"))

        assertEquals(1, SubAccountBodies.status(true).get("status").asInt)
        assertEquals(0, SubAccountBodies.status(false).get("status").asInt)
    }
}
