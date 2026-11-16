package com.vps.qing.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 已购机器操作单测（v1.4.22）：请求体构造、连接信息格式化、日志解析、镜像解析。
 */
class HostOpsTest {

    @Test
    fun `重装请求体包含镜像密码端口且端口兜底22`() {
        val body = HostOpsBodies.reinstall(imageId = 42, password = "P@ss123", port = "")
        assertEquals(42, body.get("image_id").asInt)
        assertEquals("P@ss123", body.get("password").asString)
        assertEquals("22", body.get("port").asString)
    }

    @Test
    fun `重装请求体保留自定义端口`() {
        val body = HostOpsBodies.reinstall(imageId = 7, password = "abc", port = "2222")
        assertEquals("2222", body.get("port").asString)
    }

    @Test
    fun `重置密码请求体包含密码与端口`() {
        val body = HostOpsBodies.resetPassword("newpw", "33")
        assertEquals("newpw", body.get("password").asString)
        assertEquals("33", body.get("port").asString)
    }

    @Test
    fun `连接文本包含要求的五项且缺失字段兜底`() {
        val text = HostOpsBodies.connectionText(
            ip = "1.2.3.4", name = "my-vps", port = "", username = "", password = "pw"
        )
        assertTrue(text.contains("IP：1.2.3.4"))
        assertTrue(text.contains("机器名：my-vps"))
        assertTrue(text.contains("端口：22"))
        assertTrue(text.contains("账户：root"))
        assertTrue(text.contains("密码：pw"))
    }

    @Test
    fun `SSH命令按端口是否为22生成`() {
        assertEquals("ssh root@1.2.3.4", HostOpsBodies.sshCommand("1.2.3.4", "22", ""))
        assertEquals("ssh admin@1.2.3.4 -p 2222", HostOpsBodies.sshCommand("1.2.3.4", "2222", "admin"))
        assertEquals("", HostOpsBodies.sshCommand("", "22", "root"))
    }

    @Test
    fun `日志解析兼容多候选字段并过滤空描述`() {
        val data = JsonParser.parseString(
            """{"list":[
                {"description":"开始重装","create_time":"1700000000","ip":"1.2.3.4"},
                {"name":"开机","time":"1700000100"},
                {"content":""},
                {"remark":"重启","add_time":"1700000200"}
            ]}"""
        )
        val logs = HostOpsBodies.parseLog(data)
        assertEquals(3, logs.size)
        assertEquals("开始重装", logs[0].text)
        assertEquals("1700000000", logs[0].time)
        assertEquals("1.2.3.4", logs[0].ip)
        assertEquals("开机", logs[1].text)
        assertEquals("重启", logs[2].text)
    }

    @Test
    fun `镜像解析按分组返回且过滤空分组`() {
        val data = JsonParser.parseString(
            """{"list":[
                {"id":1,"name":"Linux","icon":"linux","image":[
                    {"id":10,"name":"CentOS 7","charge":0,"price":0},
                    {"id":11,"name":"Ubuntu 22","charge":1,"price":10.5}
                ]},
                {"id":2,"name":"Windows","image":[]}
            ]}"""
        )
        val groups = MfParser.parseImages(data)
        assertEquals(1, groups.size)
        assertEquals("Linux", groups[0].name)
        assertEquals(2, groups[0].images.size)
        assertEquals(11, groups[0].images[1].id)
        assertEquals(10.5, groups[0].images[1].price, 0.0)
    }
}
