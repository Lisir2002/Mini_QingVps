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

    /** v1.4.16 默认主题：晨曦微光 —— 干净的青蓝白底 + 柔和晨光主色，首次启动即用。 */
    val AURORA = ThemePaletteSpec(
        "aurora", "晨曦微光", "默认 · 清透青蓝晨光",
        CoreColors(
            background = Color(0xFFF7FBFF), card = Color(0xFFFFFFFF),
            ink = Color(0xFF223247), inkSub = Color(0xFF8497AC), line = Color(0xFFE1ECF6),
            brand = Color(0xFF2FA8C4), brandSoft = Color(0xFFDDF3F8), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF0B1620), card = Color(0xFF152532),
            ink = Color(0xFFE6F2FA), inkSub = Color(0xFF8FA9BC), line = Color(0x33FFFFFF),
            brand = Color(0xFF57CBE4), brandSoft = Color(0x332FA8C4), glassTint = Color(0xFF152532),
            accentSoftAlpha = 0.24f
        )
    )

    // ---- v1.4.16 新增 4 套，进一步丰富内置主题 ----
    val AMBER = ThemePaletteSpec(
        "amber", "琥珀暖阳", "蜜糖琥珀 · 温暖明亮",
        CoreColors(
            background = Color(0xFFFFFAF1), card = Color(0xFFFFFFFF),
            ink = Color(0xFF3B3022), inkSub = Color(0xFFA7947A), line = Color(0xFFF5E9D5),
            brand = Color(0xFFE0972B), brandSoft = Color(0xFFFBECD0), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF1A1309), card = Color(0xFF281F12),
            ink = Color(0xFFFAF1E2), inkSub = Color(0xFFB7A588), line = Color(0x33FFFFFF),
            brand = Color(0xFFF5B758), brandSoft = Color(0x33E0972B), glassTint = Color(0xFF281F12),
            accentSoftAlpha = 0.24f
        )
    )
    val INDIGO = ThemePaletteSpec(
        "indigo", "靛蓝夜航", "深靛蓝调 · 沉静专注",
        CoreColors(
            background = Color(0xFFF4F6FE), card = Color(0xFFFFFFFF),
            ink = Color(0xFF232A48), inkSub = Color(0xFF838FB4), line = Color(0xFFE2E7F7),
            brand = Color(0xFF4A5BD6), brandSoft = Color(0xFFE3E8FC), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF0D1122), card = Color(0xFF171D36),
            ink = Color(0xFFE9ECFB), inkSub = Color(0xFF909BC2), line = Color(0x33FFFFFF),
            brand = Color(0xFF7E8CF0), brandSoft = Color(0x334A5BD6), glassTint = Color(0xFF171D36),
            accentSoftAlpha = 0.24f
        )
    )
    val CYPRESS = ThemePaletteSpec(
        "cypress", "松柏常青", "苍松黛绿 · 沉稳自然",
        CoreColors(
            background = Color(0xFFF3F9F5), card = Color(0xFFFFFFFF),
            ink = Color(0xFF223329), inkSub = Color(0xFF829B88), line = Color(0xFFDFEEE3),
            brand = Color(0xFF3C8C63), brandSoft = Color(0xFFDFF2E6), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF0C1710), card = Color(0xFF152318),
            ink = Color(0xFFE8F4EC), inkSub = Color(0xFF8FB39C), line = Color(0x33FFFFFF),
            brand = Color(0xFF62C78E), brandSoft = Color(0x333C8C63), glassTint = Color(0xFF152318),
            accentSoftAlpha = 0.24f
        )
    )
    val LOTUS = ThemePaletteSpec(
        "lotus", "藕荷初夏", "淡藕荷粉 · 清雅恬静",
        CoreColors(
            background = Color(0xFFFBF6FB), card = Color(0xFFFFFFFF),
            ink = Color(0xFF3A2C3C), inkSub = Color(0xFFA28EA2), line = Color(0xFFF0E4F0),
            brand = Color(0xFFB565A8), brandSoft = Color(0xFFF5E6F2), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF170F18), card = Color(0xFF251828),
            ink = Color(0xFFF6ECF6), inkSub = Color(0xFFB39EB3), line = Color(0x33FFFFFF),
            brand = Color(0xFFD18CC5), brandSoft = Color(0x33B565A8), glassTint = Color(0xFF251828),
            accentSoftAlpha = 0.24f
        )
    )

    val NEBULA = ThemePaletteSpec(
        "nebula", "星云蓝紫", "经典 · 蓝紫渐变糖果",
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

    // ---- v1.4.14 新增 6 套预设主题（合计 12 套），浅/深两套配色都完整 ----

    val FOREST = ThemePaletteSpec(
        "forest", "森林墨绿", "苔绿松风 · 沉静护眼",
        CoreColors(
            background = Color(0xFFF1FBF3), card = Color(0xFFFFFFFF),
            ink = Color(0xFF223329), inkSub = Color(0xFF7C9A86), line = Color(0xFFDCEEE1),
            brand = Color(0xFF2E9E63), brandSoft = Color(0xFFDDF3E5), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF0D1712), card = Color(0xFF16241C),
            ink = Color(0xFFE7F4EC), inkSub = Color(0xFF8FB19C), line = Color(0x33FFFFFF),
            brand = Color(0xFF58CE8E), brandSoft = Color(0x332E9E63), glassTint = Color(0xFF16241C),
            accentSoftAlpha = 0.24f
        )
    )
    val PEACH = ThemePaletteSpec(
        "peach", "蜜桃奶霜", "奶油蜜桃 · 温柔暖调",
        CoreColors(
            background = Color(0xFFFFF6F1), card = Color(0xFFFFFFFF),
            ink = Color(0xFF3B2C28), inkSub = Color(0xFFAC8C82), line = Color(0xFFF7E5DC),
            brand = Color(0xFFFF7E66), brandSoft = Color(0xFFFFE6DE), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF1B1210), card = Color(0xFF2A1C19),
            ink = Color(0xFFF8ECE7), inkSub = Color(0xFFBBA096), line = Color(0x33FFFFFF),
            brand = Color(0xFFFF9E88), brandSoft = Color(0x33FF7E66), glassTint = Color(0xFF2A1C19),
            accentSoftAlpha = 0.24f
        )
    )
    val LIME = ThemePaletteSpec(
        "lime", "青柠气泡", "鲜柠黄绿 · 清爽元气",
        CoreColors(
            background = Color(0xFFFAFEF0), card = Color(0xFFFFFFFF),
            ink = Color(0xFF31351E), inkSub = Color(0xFF98A06E), line = Color(0xFFECF2D2),
            brand = Color(0xFF8FBF2B), brandSoft = Color(0xFFEEF7D2), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF15180B), card = Color(0xFF202613),
            ink = Color(0xFFF1F6DE), inkSub = Color(0xFFAEB77A), line = Color(0x33FFFFFF),
            brand = Color(0xFFB4DE55), brandSoft = Color(0x338FBF2B), glassTint = Color(0xFF202613),
            accentSoftAlpha = 0.24f
        )
    )
    val ROSE = ThemePaletteSpec(
        "rose", "玫瑰鎏金", "低饱和玫瑰 · 轻奢质感",
        CoreColors(
            background = Color(0xFFFFF7F6), card = Color(0xFFFFFFFF),
            ink = Color(0xFF3A2B30), inkSub = Color(0xFFAE8E95), line = Color(0xFFF4E4E7),
            brand = Color(0xFFC77B86), brandSoft = Color(0xFFF8E6E9), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF1A1114), card = Color(0xFF291B21),
            ink = Color(0xFFF8EAEF), inkSub = Color(0xFFBCA0A8), line = Color(0x33FFFFFF),
            brand = Color(0xFFE6A2AC), brandSoft = Color(0x33C77B86), glassTint = Color(0xFF291B21),
            accentSoftAlpha = 0.24f
        )
    )
    val GLACIER = ThemePaletteSpec(
        "glacier", "冰川青蓝", "冰透青蓝 · 清亮通透",
        CoreColors(
            background = Color(0xFFF0FBFD), card = Color(0xFFFFFFFF),
            ink = Color(0xFF20343A), inkSub = Color(0xFF7A9AA4), line = Color(0xFFDCEFF2),
            brand = Color(0xFF2BA6C4), brandSoft = Color(0xFFD8F1F7), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF0C171B), card = Color(0xFF15262C),
            ink = Color(0xFFE4F5F9), inkSub = Color(0xFF8DB2BC), line = Color(0x33FFFFFF),
            brand = Color(0xFF5CCBE4), brandSoft = Color(0x332BA6C4), glassTint = Color(0xFF15262C),
            accentSoftAlpha = 0.24f
        )
    )
    val SLATE = ThemePaletteSpec(
        "slate", "曜石雾蓝", "灰蓝曜石 · 克制高级",
        CoreColors(
            background = Color(0xFFF5F7FB), card = Color(0xFFFFFFFF),
            ink = Color(0xFF2A3140), inkSub = Color(0xFF8993A6), line = Color(0xFFE3E8F1),
            brand = Color(0xFF5B6B8C), brandSoft = Color(0xFFE5EAF4), glassTint = Color(0xFFFFFFFF)
        ),
        CoreColors(
            background = Color(0xFF10131A), card = Color(0xFF1B202B),
            ink = Color(0xFFEDF0F7), inkSub = Color(0xFF97A1B6), line = Color(0x33FFFFFF),
            brand = Color(0xFF8B9CC4), brandSoft = Color(0x335B6B8C), glassTint = Color(0xFF1B202B),
            accentSoftAlpha = 0.24f
        )
    )

    /** v1.4.16：默认主题排首位，新增 5 套（晨曦/琥珀/靛蓝/松柏/藕荷）合计 17 套。 */
    val entries: List<ThemePaletteSpec> = listOf(
        AURORA, NEBULA, MINT, SAKURA, SUNSET, OCEAN, GRAPE,
        FOREST, PEACH, LIME, ROSE, GLACIER, SLATE,
        AMBER, INDIGO, CYPRESS, LOTUS
    )

    /** 默认主题（首次启动 / id 失效时的兜底）。 */
    val DEFAULT: ThemePaletteSpec get() = AURORA

    fun byId(id: String): ThemePaletteSpec = entries.firstOrNull { it.id == id } ?: DEFAULT
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

    init { apply(ThemePalettes.DEFAULT, dark = false) }

    /**
     * 应用一套主题（在 QingVpsTheme 组合期调用，写入即触发全局重绘）。
     * v1.4.16：[customBrand] 非空时用主题自设主色覆盖色板自带主色（浅/深分别派生柔和底）。
     */
    fun apply(spec: ThemePaletteSpec, dark: Boolean, customBrand: Color? = null) {
        val core = if (dark) spec.dark else spec.light
        Background = core.background
        White = core.card
        Ink = core.ink
        InkSub = core.inkSub
        Line = core.line
        GlassTint = core.glassTint
        if (customBrand != null) {
            Brand = customBrand
            BrandSoft = if (dark) customBrand.copy(alpha = 0.24f)
            else customBrand.copy(alpha = 0.14f)
        } else {
            Brand = core.brand
            BrandSoft = core.brandSoft
        }
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
