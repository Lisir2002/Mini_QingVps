package com.vps.qing.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.vps.qing.core.SettingsStore

/**
 * QingVPS 主题（v1.4.16 重构）：
 *  - CartoonColors 为可观察全局色板，主题切换立即全局重绘；
 *  - [fontId] 经 [AppFonts] 统一注入整套 Material Typography，字体切换全局生效；
 *  - [customAccent] 主题自设主色，覆盖所选色板自带主色；
 *  - 状态栏图标明暗随当前深浅主题切换，配合 edge-to-edge 消除自定义背景与状态栏的切割。
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
    outlineVariant = CartoonColors.Line,
    error = Color(0xFFFF8A80),
    onError = Color(0xFF1A1111)
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
    surfaceVariant = CartoonColors.BrandSoft,
    onSurfaceVariant = CartoonColors.InkSub,
    outline = CartoonColors.Line,
    outlineVariant = CartoonColors.Line,
    error = Color(0xFFE5484D),
    onError = Color.White
)

@Composable
fun QingVpsTheme(
    themeMode: Int = SettingsStore.THEME_SYSTEM,
    accentId: String = ThemePalettes.DEFAULT.id,
    fontId: String = SettingsStore.DEFAULT_FONT_ID,
    customAccent: Long = 0L,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        SettingsStore.THEME_LIGHT -> false
        SettingsStore.THEME_DARK -> true
        else -> darkTheme
    }
    val spec = ThemePalettes.byId(accentId)
    // customAccent 存的是 0xAARRGGBB 形式的 Long；用 0..255 的 RGB 构造解析，
    // 避免依赖 Color(Long) 在不同 Compose 版本下的打包语义（v1.4.16）。
    val customBrand = if (customAccent != 0L) {
        Color(
            red = ((customAccent shr 16) and 0xFF).toInt(),
            green = ((customAccent shr 8) and 0xFF).toInt(),
            blue = (customAccent and 0xFF).toInt()
        )
    } else null

    // 写入可观察色板：所有读取 CartoonColors.* 的组件立即全局重绘
    SideEffect { CartoonColors.apply(spec, dark, customBrand) }

    // v1.4.16：edge-to-edge 下状态栏透明，图标明暗跟随当前主题，
    // 让自定义背景图一直铺到状态栏后面而不留一道不透明切割带。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view)?.apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = materialScheme(dark),
        typography = appTypography(fontId),
        content = content
    )
}

/** 字体统一调度：任何页面都不再各自指定字体，全部走这里注入的 Typography。 */
private fun appTypography(fontId: String): Typography = AppFonts.typography(fontId)
