package com.vps.qing.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.vps.qing.core.SettingsStore

/**
 * QingVPS 主题（v1.4.0 重构）：
 *  - CartoonColors 为可观察全局色板，主题切换立即全局重绘（修复 v1.3.x 不生效问题）；
 *  - 支持浅色/深色 + 六套高级主题色板（星云蓝紫/薄荷汽水/樱花物语/落日橙海/深海蓝调/香芋奶紫）。
 */

/** 由当前色板派生的 Material 配色（组合期构建，跟随主题变化）。 */
private fun materialScheme(dark: Boolean) = if (dark) darkColorScheme(
    primary = CartoonColors.Brand,
    onPrimary = Color.White,
    primaryContainer = CartoonColors.BrandSoft,
    onPrimaryContainer = CartoonColors.Brand,
    secondary = CartoonColors.Mint,
    onSecondary = Color.White,
    background = CartoonColors.Background,
    onBackground = CartoonColors.Ink,
    surface = CartoonColors.White,
    onSurface = CartoonColors.Ink,
    surfaceVariant = CartoonColors.BrandSoft,
    onSurfaceVariant = CartoonColors.InkSub,
    outline = CartoonColors.Line,
    error = Color(0xFFE5484D),
    onError = Color.White
) else lightColorScheme(
    primary = CartoonColors.Brand,
    onPrimary = Color.White,
    primaryContainer = CartoonColors.BrandSoft,
    onPrimaryContainer = CartoonColors.Brand,
    secondary = CartoonColors.Mint,
    onSecondary = Color.White,
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

@Composable
fun QingVpsTheme(
    themeMode: Int = SettingsStore.THEME_SYSTEM,
    accentId: String = ThemePalettes.NEBULA.id,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        SettingsStore.THEME_LIGHT -> false
        SettingsStore.THEME_DARK -> true
        else -> darkTheme
    }
    val spec = ThemePalettes.byId(accentId)
    // 写入可观察色板：所有读取 CartoonColors.* 的组件立即全局重绘
    SideEffect { CartoonColors.apply(spec, dark) }
    MaterialTheme(
        colorScheme = materialScheme(dark),
        content = content
    )
}
