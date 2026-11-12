package com.vps.qing.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.vps.qing.core.SettingsStore

private val Brand = Color(0xFF4F46E5)
private val BrandDark = Color(0xFF818CF8)

private val LightColors = lightColorScheme(
    primary = Brand,
    secondary = Color(0xFF0891B2),
    tertiary = Color(0xFFEA580C)
)

private val DarkColors = darkColorScheme(
    primary = BrandDark,
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFFFB923C)
)

@Composable
fun QingVpsTheme(
    themeMode: Int = SettingsStore.THEME_SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDark = when (themeMode) {
        SettingsStore.THEME_LIGHT -> false
        SettingsStore.THEME_DARK -> true
        else -> darkTheme
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        useDark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
