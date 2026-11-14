package com.vps.qing.core.ui

import com.vps.qing.core.ui.theme.ThemePalettes
import com.vps.qing.feature.profile.maskIp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.16 新增基础设施的纯函数单测：
 *  - 背景图缩放/定位的位移换算 [bgTranslation]（保证放大后拖动不露边）；
 *  - 机器 IP 打码 [maskIp]（保留分隔符轮廓）；
 *  - 默认主题的日夜配色约束。
 *
 * 注：字体族 AppFonts 依赖 Android framework 的 Typeface，无法在纯 JVM 单测里初始化，
 * 其结构约束改由编译期与 UI 现场保证，此处不测。
 */
class V1416UiInfraTest {

    // ---- 背景图缩放 / 定位 ----

    @Test
    fun `scale 为 1 时任何定位都不产生位移`() {
        val (x, y) = bgTranslation(1f, -1f, 1f, 1080f, 1920f)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    @Test
    fun `放大两倍时边界定位等于半屏可平移量`() {
        // scale=2 → maxX=(2-1)*w/2=540, maxY=(2-1)*h/2=960
        val (x, y) = bgTranslation(1f, 1f, 2f, 1080f, 1920f)
        assertEquals(540f, x, 0.001f)
        assertEquals(960f, y, 0.001f)
    }

    @Test
    fun `定位越界被夹到边界且负向对称`() {
        val (x, y) = bgTranslation(5f, -5f, 3f, 1000f, 2000f)
        // scale=3 → maxX=(3-1)*1000/2=1000, maxY=(3-1)*2000/2=2000
        assertEquals(1000f, x, 0.001f)
        assertEquals(-2000f, y, 0.001f)
    }

    @Test
    fun `居中定位不产生位移`() {
        val (x, y) = bgTranslation(0f, 0f, 4f, 800f, 1600f)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
    }

    // ---- IP 打码 ----

    @Test
    fun `IPv4 打码保留点号其余变圆点`() {
        assertEquals("•••.•••.•.•", maskIp("192.168.1.1"))
    }

    @Test
    fun `IPv6 打码保留连续冒号`() {
        assertEquals("••••::••••", maskIp("fe80::a1b2"))
    }

    @Test
    fun `多个 IP 逗号空格分隔时分隔符保留`() {
        assertEquals("•.•.•.•, •.•.•.•", maskIp("1.2.3.4, 5.6.7.8"))
    }

    @Test
    fun `打码后长度与原串一致且不含明文数字`() {
        val raw = "203.0.113.42"
        val masked = maskIp(raw)
        assertEquals(raw.length, masked.length)
        assertTrue(masked.none { it.isDigit() })
    }

    // ---- 默认主题约束 ----

    @Test
    fun `默认主题排首位且深浅主色不同以适配日夜`() {
        val def = ThemePalettes.DEFAULT
        assertEquals(ThemePalettes.entries.first(), def)
        assertTrue("默认主题深浅主色应不同以适配日夜",
            def.light.brand != def.dark.brand)
        assertTrue("默认主题深浅背景应不同以适配日夜",
            def.light.background != def.dark.background)
    }
}
