package com.vps.qing.core.ui.theme

import com.vps.qing.core.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.16：预设主题扩充到 17 套，新增「晨曦微光 AURORA」作为首次启动默认主题，
 * 每套都具备完整的深浅配色；未知 id 回退到默认主题而非旧版星云。
 */
class ThemePalettesTest {

    @Test
    fun `预设主题扩充到十七套且 id 唯一`() {
        assertEquals(17, ThemePalettes.entries.size)
        assertEquals(17, ThemePalettes.entries.map { it.id }.toSet().size)
        ThemePalettes.entries.forEach { spec ->
            assertTrue(spec.id.isNotBlank())
            assertTrue(spec.title.isNotBlank())
            assertTrue(spec.desc.isNotBlank())
        }
    }

    @Test
    fun `默认主题为晨曦微光且排在首位`() {
        assertEquals(SettingsStore.DEFAULT_ACCENT_ID, ThemePalettes.DEFAULT.id)
        assertEquals(ThemePalettes.AURORA, ThemePalettes.DEFAULT)
        assertEquals(ThemePalettes.AURORA, ThemePalettes.entries.first())
    }

    @Test
    fun `byId 命中已知主题未知回退默认晨曦`() {
        assertEquals(ThemePalettes.MINT, ThemePalettes.byId("mint"))
        assertEquals(ThemePalettes.FOREST, ThemePalettes.byId("forest"))
        assertEquals(ThemePalettes.GLACIER, ThemePalettes.byId("glacier"))
        assertEquals(ThemePalettes.SLATE, ThemePalettes.byId("slate"))
        assertEquals(ThemePalettes.AMBER, ThemePalettes.byId("amber"))
        assertEquals(ThemePalettes.INDIGO, ThemePalettes.byId("indigo"))
        assertEquals(ThemePalettes.CYPRESS, ThemePalettes.byId("cypress"))
        assertEquals(ThemePalettes.LOTUS, ThemePalettes.byId("lotus"))
        assertEquals(ThemePalettes.AURORA, ThemePalettes.byId("not-exist"))
    }

    @Test
    fun `每套主题深浅两套配色均完整`() {
        ThemePalettes.entries.forEach { spec ->
            listOf(spec.light, spec.dark).forEach { c ->
                assertTrue("${spec.id} 主色不应全透明", c.brand.alpha > 0f)
                assertTrue("${spec.id} 背景不应全透明", c.background.alpha > 0f)
                assertTrue("${spec.id} 卡片色不应全透明", c.card.alpha > 0f)
                assertTrue("${spec.id} 主文字不应全透明", c.ink.alpha > 0f)
                assertTrue("${spec.id} 玻璃柔化系数应合法", c.accentSoftAlpha > 0f)
                assertEquals(spec.light.brand, spec.preview)
            }
        }
    }
}
