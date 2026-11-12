package com.vps.qing.core.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.vps.qing.core.SettingsStore

/**
 * 主题色板（v1.4.0）。
 *
 * 关键改动：CartoonColors 的所有属性改为 mutableStateOf 可观察状态，
 * 全 App 组件直接读取这些属性，切主题（深浅 / 主题色）时立即全局重绘——
 * 修复 v1.3.x「切深色只换 MaterialTheme、界面毫无变化」的问题。
 */

/** 核心配色（一套主题在浅色/深色下各一份）。 */
data class CoreColors(
    val background: Color,
    val card: Color,        // 卡片/弹窗底（浅色=纯白，深色=深色卡面）
    val ink: Color,         // 主文字
    val inkSub: Color,      // 次要文字
    val line: Color,        // 描边
    val brand: Color,       // 主题主色
    val brandSoft: Color,   // 主色柔和底
    val glassTint: Color,   // 玻璃底色
    val accentSoftAlpha: Float = 1f  // 深色模式下糖果柔和底用半透明
)

/** 一套高级主题 = id + 名称 + 描述 + 浅色/深色核心配色。 */
data class ThemePaletteSpec(
    val id: String,
    val title: String,
    val desc: String,
    val light: CoreColors,
    val dark: CoreColors
) {
    val preview: Color get() = light.brand
}

private fun nebulaLight() = CoreColors(
    background = Color(0xFFFDFEFF), card = Color(0xFFFFFFFF),
    ink = Color(0xFF2B2E45), inkSub = Color(0xFF8A90A8), line = Color(0xFFE7EAF5),
    brand = Color(0xFF6C6BFF), brandSoft = Color(0xFFECEBFF), glassTint = Color(0xFFFFFFFF)
)
private fun nebulaDark() = CoreColors(
    background = Color(0xFF13141F), card = Color(0xFF1C1E2E),
    ink = Color(0xFFEFF0FA), inkSub = Color(0xFF9BA0BC), line = Color(0x33FFFFFF),
    brand = Color(0xFF8C8BFF), brandSoft = Color(0x336C6BFF), glassTint = Color(0xFF1C1E2E),
    accentSoftAlpha = 0.24f
)

object ThemePalettes {

    val NEBULA = ThemePaletteSpec(
        "nebula", "星云蓝紫", "默认经典 · 蓝紫渐变糖果",
        nebulaLight(), nebulaDark()
    )
    val MINT = ThemePaletteSpec(
        "mint", "薄荷汽水", "清凉薄荷 · 夏日气泡感",
        CoreColors(
            background = Color(0xFFF3FEFA), card = Color(0xFFFFFFFF),
            ink = Color(0xFF22332E), inkSub = Color(0xFF7D9A92), line = Color(0xFFDFF0EB),
            brand = Color(0xFF17B48D), brandSoft = Color(0xFFDAF6EE), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF0F1B18), card = Color(0xFF182522),
            ink = Color(0xFFE8F5F0), inkSub = Color(0xFF8FAEA5), line = Color(0x33FFFFFF),
            brand = Color(0xFF3FD3A8), brandSoft = Color(0x3317B48D), glassTint = Color(0xFF182522),
            accentSoftAlpha = 0.24f
        )
    )
    val SAKURA = ThemePaletteSpec(
        "sakura", "樱花物语", "柔粉樱色 · 春日甜心",
        CoreColors(
            background = Color(0xFFFFF7FA), card = Color(0xFFFFFFFF),
            ink = Color(0xFF3B2A32), inkSub = Color(0xFFA88E99), line = Color(0xFFF7E4EC),
            brand = Color(0xFFE86A92), brandSoft = Color(0xFFFFE4EE), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF1C1216), card = Color(0xFF291C22),
            ink = Color(0xFFF8EAF0), inkSub = Color(0xFFB296A4), line = Color(0x33FFFFFF),
            brand = Color(0xFFFF8FB0), brandSoft = Color(0x33E86A92), glassTint = Color(0xFF291C22),
            accentSoftAlpha = 0.24f
        )
    )
    val SUNSET = ThemePaletteSpec(
        "sunset", "落日橙海", "暖橙落日 · 温暖治愈",
        CoreColors(
            background = Color(0xFFFFF8F2), card = Color(0xFFFFFFFF),
            ink = Color(0xFF3B2F26), inkSub = Color(0xFFA6927F), line = Color(0xFFF7EADD),
            brand = Color(0xFFFF8A3D), brandSoft = Color(0xFFFFE9D9), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF1B140E), card = Color(0xFF2A1F16),
            ink = Color(0xFFF8EFE7), inkSub = Color(0xFFB4A08D), line = Color(0x33FFFFFF),
            brand = Color(0xFFFFA35C), brandSoft = Color(0x33FF8A3D), glassTint = Color(0xFF2A1F16),
            accentSoftAlpha = 0.24f
        )
    )
    val OCEAN = ThemePaletteSpec(
        "ocean", "深海蓝调", "冷静深蓝 · 专业沉稳",
        CoreColors(
            background = Color(0xFFF1F6FF), card = Color(0xFFFFFFFF),
            ink = Color(0xFF22304A), inkSub = Color(0xFF7E92B0), line = Color(0xFFE0EAF7),
            brand = Color(0xFF2E7CF6), brandSoft = Color(0xFFDCEAFF), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF0E1520), card = Color(0xFF16202E),
            ink = Color(0xFFE7EEF9), inkSub = Color(0xFF8DA0BC), line = Color(0x33FFFFFF),
            brand = Color(0xFF5B9AFF), brandSoft = Color(0x332E7CF6), glassTint = Color(0xFF16202E),
            accentSoftAlpha = 0.24f
        )
    )
    val GRAPE = ThemePaletteSpec(
        "grape", "香芋奶紫", "芋泥奶紫 · 绵密温柔",
        CoreColors(
            background = Color(0xFFF9F5FF), card = Color(0xFFFFFFFF),
            ink = Color(0xFF302843), inkSub = Color(0xFF9187A8), line = Color(0xFFEBE4F7),
            brand = Color(0xFF9B6BFF), brandSoft = Color(0xFFF0E8FF), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF151021), card = Color(0xFF1F1932),
            ink = Color(0xFFEFEAFB), inkSub = Color(0xFF9F94BE), line = Color(0x33FFFFFF),
            brand = Color(0xFFB48CFF), brandSoft = Color(0x339B6BFF), glassTint = Color(0xFF1F1932),
            accentSoftAlpha = 0.24f
        )
    )

    val entries: List<ThemePaletteSpec> = listOf(NEBULA, MINT, SAKURA, SUNSET, OCEAN, GRAPE)

    fun byId(id: String): ThemePaletteSpec = entries.firstOrNull { it.id == id } ?: NEBULA
}

/**
 * 全局主题驱动的色板。所有属性都是可观察状态：
 * 组件组合中读取即订阅，切换主题时自动全局重绘。
 */
object CartoonColors {

    /** 卡片底色（旧名 White 兼容保留；深色模式下为深色卡面）。 */
    var White: Color by mutableStateOf(nebulaLight().card); private set
    var Background: Color by mutableStateOf(nebulaLight().background); private set
    var Ink: Color by mutableStateOf(nebulaLight().ink); private set
    var InkSub: Color by mutableStateOf(nebulaLight().inkSub); private set
    var Line: Color by mutableStateOf(nebulaLight().line); private set
    var Brand: Color by mutableStateOf(nebulaLight().brand); private set
    var BrandSoft: Color by mutableStateOf(nebulaLight().brandSoft); private set
    var GlassTint: Color by mutableStateOf(nebulaLight().glassTint); private set

    /** 当前是否深色模式（供个别需要分支的地方）。 */
    var isDark: Boolean = false; private set

    // 糖果点缀色（各主题共用色相，深浅模式只调柔和底）
    var Mint = Color(0xFF37D6B0); private set
    var Sky = Color(0xFF4FB8F5); private set
    var Peach = Color(0xFFFF8FA3); private set
    var Lemon = Color(0xFFFFC24B); private set
    var Lilac = Color(0xFFB08BFF); private set
    var Coral = Color(0xFFFF7043); private set
    var MintSoft: Color by mutableStateOf(Color(0xFFDFF9F1)); private set
    var SkySoft: Color by mutableStateOf(Color(0xFFE1F2FE)); private set
    var PeachSoft: Color by mutableStateOf(Color(0xFFFFE7EC)); private set
    var LemonSoft: Color by mutableStateOf(Color(0xFFFFF3D6)); private set
    var LilacSoft: Color by mutableStateOf(Color(0xFFF0E8FF)); private set

    val GlassStroke = Color(0xFFFFFFFF)
    val GlassShadow = Color(0x336C6BFF)

    /** 分类/菜单按索引循环取的糖果色。 */
    val accents: List<Pair<Color, Color>>
        get() = listOf(
            Mint to MintSoft, Sky to SkySoft, Peach to PeachSoft,
            Lemon to LemonSoft, Lilac to LilacSoft, Brand to BrandSoft
        )
    fun accent(index: Int): Pair<Color, Color> = accents[((index % accents.size) + accents.size) % accents.size]

    init { apply(ThemePalettes.NEBULA, dark = false) }

    /** 应用一套主题（在 QingVpsTheme 组合期调用，写入即触发全局重绘）。 */
    fun apply(spec: ThemePaletteSpec, dark: Boolean) {
        val core = if (dark) spec.dark else spec.light
        Background = core.background
        White = core.card
        Ink = core.ink
        InkSub = core.inkSub
        Line = core.line
        Brand = core.brand
        BrandSoft = core.brandSoft
        GlassTint = core.glassTint
        isDark = dark
        if (dark) {
            val a = core.accentSoftAlpha
            MintSoft = Mint.copy(alpha = a)
            SkySoft = Sky.copy(alpha = a)
            PeachSoft = Peach.copy(alpha = a)
            LemonSoft = Lemon.copy(alpha = a)
            LilacSoft = Lilac.copy(alpha = a)
        } else {
            MintSoft = Color(0xFFDFF9F1)
            SkySoft = Color(0xFFE1F2FE)
            PeachSoft = Color(0xFFFFE7EC)
            LemonSoft = Color(0xFFFFF3D6)
            LilacSoft = Color(0xFFF0E8FF)
        }
    }
}
