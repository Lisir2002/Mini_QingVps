package com.vps.qing.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.4.14：GitHub Releases 软件更新的语义版本比较与 JSON 解析（纯函数）。 */
class UpdateCheckerTest {

    @Test
    fun `语义版本比较识别新版本`() {
        assertTrue(UpdateChecker.isNewerVersion("v1.4.15", "1.4.14"))
        assertTrue(UpdateChecker.isNewerVersion("1.5.0", "1.4.99"))
        assertTrue(UpdateChecker.isNewerVersion("V2.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewerVersion("v1.4.14-beta", "1.4.13"))
        assertTrue(UpdateChecker.isNewerVersion("1.4.14.1", "1.4.14"))
    }

    @Test
    fun `相同版本或更旧版本不提示更新`() {
        assertFalse(UpdateChecker.isNewerVersion("v1.4.14", "1.4.14"))
        assertFalse(UpdateChecker.isNewerVersion("v1.4.13", "1.4.14"))
        assertFalse(UpdateChecker.isNewerVersion("1.4.14", "1.4.14.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.3.99", "1.4.0"))
    }

    @Test
    fun `解析 release JSON 并挑出 apk 下载地址`() {
        val json = """
        {
          "tag_name": "v1.4.15",
          "name": "QingVPS v1.4.15",
          "body": "新增自定义背景图",
          "html_url": "https://github.com/Lisir2002/Mini_QingVps/releases/tag/v1.4.15",
          "published_at": "2026-11-14T00:00:00Z",
          "assets": [
            {"name": "SHA256SUMS.txt", "browser_download_url": "https://example/sums"},
            {"name": "QingVPS-v1.4.15-debug.apk", "browser_download_url": "https://example/app.apk"}
          ]
        }
        """.trimIndent()
        val r = UpdateChecker.parse(json)!!
        assertEquals("v1.4.15", r.tag)
        assertEquals("QingVPS v1.4.15", r.name)
        assertEquals("新增自定义背景图", r.body)
        assertEquals("https://example/app.apk", r.apkUrl)
        assertEquals("2026-11-14T00:00:00Z", r.publishedAt)
    }

    @Test
    fun `无 apk 资源或字段缺失时降级到 Releases 页面`() {
        val r = UpdateChecker.parse("""{"tag_name":"v1.4.14","html_url":""}""")!!
        assertEquals("v1.4.14", r.tag)
        assertEquals("v1.4.14", r.name) // name 缺失回退 tag
        assertNull(r.apkUrl)
        assertEquals(UpdateChecker.RELEASES_URL, r.htmlUrl)
        assertEquals("", r.body)
    }

    @Test
    fun `非法 JSON 或缺 tag_name 返回 null`() {
        assertNull(UpdateChecker.parse("not json"))
        assertNull(UpdateChecker.parse("{}"))
        assertNull(UpdateChecker.parse("""{"name":"x"}"""))
    }
}
