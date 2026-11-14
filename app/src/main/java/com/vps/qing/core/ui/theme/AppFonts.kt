package com.vps.qing.core.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

/**
 * 内置字体（v1.4.16）：全部基于 Android 系统内置字体族 / 系统字形变体，零体积、
 * 无需下载、中文自动回退到设备 CJK 字形，切换即时全局生效。
 * 通过 [typography] 把字体族统一注入整套 Material Typography，是全 App 字体的唯一调度入口。
 */
data class AppFontSpec(
    val id: String,
    val title: String,
    val desc: String,
    val family: FontFamily
)

object AppFonts {
    const val DEFAULT_ID = "system"

    /** 系统字形变体：部分 ROM 无该 family 时 Typeface.create 会回退到默认，不会崩。 */
    private fun native(family: String): FontFamily =
        FontFamily(Typeface.create(family, Typeface.NORMAL))

    val SYSTEM = AppFontSpec("system", "系统默认", "设备原生无衬线，均衡清晰", FontFamily.Default)
    val LIGHT = AppFontSpec("light", "纤细轻盈", "细体无衬线，通透秀气", native("sans-serif-light"))
    val ROUND = AppFontSpec("round", "圆润柔和", "圆润字形，亲和温暖", native("sans-serif-rounded"))
    val SERIF = AppFontSpec("serif", "优雅衬线", "衬线/宋体感，端正从容", FontFamily.Serif)
    val MONO = AppFontSpec("mono", "等宽数码", "等宽字距，编号与参数更整齐", FontFamily.Monospace)
    val CONDENSED = AppFontSpec("condensed", "凝练窄体", "紧凑字宽，信息密度更高", native("sans-serif-condensed"))

    val entries: List<AppFontSpec> = listOf(SYSTEM, LIGHT, ROUND, SERIF, MONO, CONDENSED)
    fun byId(id: String): AppFontSpec = entries.firstOrNull { it.id == id } ?: SYSTEM

    /** 统一调度：把选中字体族应用到整套 Material 排版令牌。 */
    fun typography(id: String): Typography {
        val f = byId(id).family
        val b = Typography()
        return b.copy(
            displayLarge = b.displayLarge.copy(fontFamily = f),
            displayMedium = b.displayMedium.copy(fontFamily = f),
            displaySmall = b.displaySmall.copy(fontFamily = f),
            headlineLarge = b.headlineLarge.copy(fontFamily = f),
            headlineMedium = b.headlineMedium.copy(fontFamily = f),
            headlineSmall = b.headlineSmall.copy(fontFamily = f),
            titleLarge = b.titleLarge.copy(fontFamily = f),
            titleMedium = b.titleMedium.copy(fontFamily = f),
            titleSmall = b.titleSmall.copy(fontFamily = f),
            bodyLarge = b.bodyLarge.copy(fontFamily = f),
            bodyMedium = b.bodyMedium.copy(fontFamily = f),
            bodySmall = b.bodySmall.copy(fontFamily = f),
            labelLarge = b.labelLarge.copy(fontFamily = f),
            labelMedium = b.labelMedium.copy(fontFamily = f),
            labelSmall = b.labelSmall.copy(fontFamily = f)
        )
    }
}
