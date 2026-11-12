package com.vps.qing.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vps.qing.core.SettingsStore

/**
 * 纯白卡通风格的固定色板（QingVPS v1.2.0）。
 * 视觉基调：纯白底 + 柔和糖果色光斑 + 大圆角卡片 + 半透明磨砂玻璃层。
 * 不使用系统动态取色（dynamicColor），保证跨设备一致的卡通观感。
 */
object CartoonColors {
    val White = Color(0xFFFFFFFF)
    val Background = Color(0xFFFDFEFF)
    val Ink = Color(0xFF2B2E45)          // 主文字：深墨蓝
    val InkSub = Color(0xFF8A90A8)        // 次要文字
    val Line = Color(0xFFE7EAF5)          // 细描边

    // 品牌与糖果点缀色
    val Brand = Color(0xFF6C6BFF)         // 主色：蓝紫
    val BrandSoft = Color(0xFFECEBFF)
    val Mint = Color(0xFF37D6B0)          // 薄荷
    val MintSoft = Color(0xFFDFF9F1)
    val Sky = Color(0xFF4FB8F5)           // 天蓝
    val SkySoft = Color(0xFFE1F2FE)
    val Peach = Color(0xFFFF8FA3)         // 蜜桃粉
    val PeachSoft = Color(0xFFFFE7EC)
    val Lemon = Color(0xFFFFC24B)         // 奶黄
    val LemonSoft = Color(0xFFFFF3D6)
    val Lilac = Color(0xFFB08BFF)         // 薰衣草
    val LilacSoft = Color(0xFFF0E8FF)
    val Coral = Color(0xFFFF7043)         // 珊瑚（价格/强调）

    // 磨砂玻璃
    val GlassTint = Color(0xFFFFFFFF)
    val GlassStroke = Color(0xFFFFFFFF)
    val GlassShadow = Color(0x336C6BFF)

    /** 分类/菜单按索引循环取的糖果色，卡通风格的多彩点缀。 */
    val accents = listOf(
        Mint to MintSoft, Sky to SkySoft, Peach to PeachSoft,
        Lemon to LemonSoft, Lilac to LilacSoft, Brand to BrandSoft
    )
    fun accent(index: Int): Pair<Color, Color> = accents[((index % accents.size) + accents.size) % accents.size]
}

private val LightCartoon = lightColorScheme(
    primary = CartoonColors.Brand,
    onPrimary = Color.White,
    primaryContainer = CartoonColors.BrandSoft,
    onPrimaryContainer = CartoonColors.Brand,
    secondary = CartoonColors.Mint,
    onSecondary = Color.White,
    secondaryContainer = CartoonColors.MintSoft,
    tertiary = CartoonColors.Peach,
    background = CartoonColors.Background,
    onBackground = CartoonColors.Ink,
    surface = CartoonColors.White,
    onSurface = CartoonColors.Ink,
    surfaceVariant = Color(0xFFF4F6FC),
    onSurfaceVariant = CartoonColors.InkSub,
    outline = CartoonColors.Line,
    error = Color(0xFFE5484D),
    onError = Color.White
)

/** 夜间仍保持柔和浅紫灰，不出现纯黑，避免破坏卡通观感。 */
private val SoftNight = lightColorScheme(
    primary = CartoonColors.Lilac,
    onPrimary = Color.White,
    primaryContainer = CartoonColors.LilacSoft,
    onPrimaryContainer = CartoonColors.Lilac,
    secondary = CartoonColors.Mint,
    background = Color(0xFFF4F2FB),
    onBackground = CartoonColors.Ink,
    surface = Color(0xFFFBF9FF),
    onSurface = CartoonColors.Ink,
    surfaceVariant = Color(0xFFECE7F8),
    onSurfaceVariant = CartoonColors.InkSub,
    outline = CartoonColors.Line,
    error = Color(0xFFE5484D),
    onError = Color.White
)

@Composable
fun QingVpsTheme(
    themeMode: Int = SettingsStore.THEME_SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val useSoftNight = when (themeMode) {
        SettingsStore.THEME_LIGHT -> false
        SettingsStore.THEME_DARK -> true
        else -> darkTheme
    }
    MaterialTheme(
        colorScheme = if (useSoftNight) SoftNight else LightCartoon,
        content = content
    )
}
