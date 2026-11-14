package com.vps.qing.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.4.14：预设主题扩充到 12 套，且每套都具备完整的深浅配色。 */
class ThemePalettesTest {

    @Test
    fun `预设主题扩充到十二套且 id 唯一`() {
        assertEquals(12, ThemePalettes.entries.size)
        assertEquals(12, ThemePalettes.entries.map { it.id }.toSet().size)
        ThemePalettes.entries.forEach { spec ->
            assertTrue(spec.id.isNotBlank())
            assertTrue(spec.title.isNotBlank())
            assertTrue(spec.desc.isNotBlank())
        }
    }

    @Test
    fun `byId 命中已知主题未知回退星云`() {
        assertEquals(ThemePalettes.MINT, ThemePalettes.byId("mint"))
        assertEquals(ThemePalettes.FOREST, ThemePalettes.byId("forest"))
        assertEquals(ThemePalettes.GLACIER, ThemePalettes.byId("glacier"))
        assertEquals(ThemePalettes.SLATE, ThemePalettes.byId("slate"))
        assertEquals(ThemePalettes.NEBULA, ThemePalettes.byId("not-exist"))
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
