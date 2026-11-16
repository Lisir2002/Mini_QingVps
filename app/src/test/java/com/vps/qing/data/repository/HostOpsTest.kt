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

    // ---- v1.4.23：管理页解析 ----

    @Test
    fun `CPU图表解析value与time`() {
        val data = JsonParser.parseString("""{"list":[
            {"time":1700000000,"value":12.5},
            {"time":1700000060,"value":8.0}
        ]}""")
        val pts = HostOpsBodies.parseChart(data)
        assertEquals(2, pts.size)
        assertEquals(1700000000L, pts[0].time)
        assertEquals(12.5, pts[0].value!!, 0.0)
    }

    @Test
    fun `磁盘IO图表解析四字段`() {
        val data = JsonParser.parseString("""{"list":[
            {"time":1700000000,"read_bytes":1048576,"write_bytes":2097152,"read_iops":100,"write_iops":200}
        ]}""")
        val pts = HostOpsBodies.parseChart(data)
        assertEquals(1, pts.size)
        assertEquals(1048576.0, pts[0].readBytes!!, 0.0)
        assertEquals(2097152.0, pts[0].writeBytes!!, 0.0)
        assertEquals(100.0, pts[0].readIops!!, 0.0)
        assertEquals(200.0, pts[0].writeIops!!, 0.0)
    }

    @Test
    fun `内存图表解析total与used`() {
        val data = JsonParser.parseString("""{"list":[
            {"time":1700000000,"total":2147483648,"used":1073741824}
        ]}""")
        val pts = HostOpsBodies.parseChart(data)
        assertEquals(2147483648.0, pts[0].total!!, 0.0)
        assertEquals(1073741824.0, pts[0].used!!, 0.0)
    }

    @Test
    fun `带宽图表解析进出带宽`() {
        val data = JsonParser.parseString("""{"list":[
            {"time":1700000000,"in_bw":1000.5,"out_bw":2000.25}
        ]}""")
        val pts = HostOpsBodies.parseChart(data)
        assertEquals(1000.5, pts[0].inBw!!, 0.0)
        assertEquals(2000.25, pts[0].outBw!!, 0.0)
    }

    @Test
    fun `图表解析缺少time时跳过`() {
        val data = JsonParser.parseString("""{"list":[
            {"value":12.5},
            {"time":1700000000,"value":8.0}
        ]}""")
        val pts = HostOpsBodies.parseChart(data)
        assertEquals(1, pts.size)
        assertEquals(8.0, pts[0].value!!, 0.0)
    }

    @Test
    fun `流量包解析`() {
        val data = JsonParser.parseString("""{"total":"100GB","used":"30GB","leave":"70GB","reset_flow_date":"2026-12-01"}""")
        val flow = HostOpsBodies.parseFlow(data)
        assertEquals("100GB", flow.total)
        assertEquals("30GB", flow.used)
        assertEquals("70GB", flow.leave)
        assertEquals("2026-12-01", flow.resetDate)
    }

    @Test
    fun `磁盘列表解析并识别系统盘`() {
        val data = JsonParser.parseString("""{"list":[
            {"id":1,"name":"kvm-system","size":40,"type":"ssd","type2":"system","status":1},
            {"id":2,"name":"data","size":100,"type":"hdd","type2":"data","status":0}
        ]}""")
        val disks = HostOpsBodies.parseDisks(data)
        assertEquals(2, disks.size)
        assertEquals("kvm-system", disks[0].name)
        assertEquals("system", disks[0].type2)
        assertEquals(1, disks[0].status)
        assertEquals(100L, disks[1].sizeGb)
    }

    @Test
    fun `快照备份解析`() {
        val data = JsonParser.parseString("""{"list":[
            {"id":5,"name":"snap1","create_time":1700000000,"notes":"before update"},
            {"id":6,"name":"","create_time":1}
        ]}""")
        val items = HostOpsBodies.parseBackups(data)
        assertEquals(1, items.size)
        assertEquals("snap1", items[0].name)
        assertEquals("before update", items[0].notes)
        assertEquals(1700000000L, items[0].createTime)
    }

    @Test
    fun `IP列表解析多字段`() {
        val data = JsonParser.parseString("""{"list":[
            {"ip":"1.2.3.4","subnet_mask":"255.255.255.0","gateway":"1.2.3.1"},
            {"ip":"","gateway":"1.2.3.1"}
        ]}""")
        val ips = HostOpsBodies.parseIps(data)
        assertEquals(1, ips.size)
        assertEquals("1.2.3.4", ips[0].ip)
        assertEquals("255.255.255.0", ips[0].subnetMask)
        assertEquals("1.2.3.1", ips[0].gateway)
    }
}
