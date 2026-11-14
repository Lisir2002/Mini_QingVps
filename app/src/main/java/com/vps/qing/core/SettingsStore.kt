package com.vps.qing.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 应用设置。用 SharedPreferences 持久化，StateFlow 让主题切换立刻生效。 */
class SettingsStore(context: Context) {

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        /** v1.4.16：首次启动默认主题（高级主题里的「晨曦」）。 */
        const val DEFAULT_ACCENT_ID = "aurora"
        const val DEFAULT_FONT_ID = "system"
    }

    private val sp = context.applicationContext
        .getSharedPreferences("qingvps_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(sp.getInt("theme_mode", THEME_SYSTEM))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun setThemeMode(mode: Int) {
        sp.edit().putInt("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    /** 高级主题色板 id（v1.4.16 起默认「晨曦」主题）。 */
    private val _accentId = MutableStateFlow(sp.getString("accent_id", null) ?: DEFAULT_ACCENT_ID)
    val accentId: StateFlow<String> = _accentId.asStateFlow()

    fun setAccentId(id: String) {
        sp.edit().putString("accent_id", id).apply()
        _accentId.value = id
    }

    // ---- v1.4.16：主题自设（自定义主色，覆盖所选色板的 Brand） ----
    /** 自定义主色 ARGB；0L = 未启用（用色板自带主色）。 */
    private val _customAccent = MutableStateFlow(sp.getLong("custom_accent", 0L))
    val customAccent: StateFlow<Long> = _customAccent.asStateFlow()

    fun setCustomAccent(argb: Long) {
        sp.edit().putLong("custom_accent", argb).apply()
        _customAccent.value = argb
    }

    // ---- v1.4.16：内置字体 ----
    private val _fontId = MutableStateFlow(sp.getString("font_id", null) ?: DEFAULT_FONT_ID)
    val fontId: StateFlow<String> = _fontId.asStateFlow()

    fun setFontId(id: String) {
        sp.edit().putString("font_id", id).apply()
        _fontId.value = id
    }

    // ---- v1.4.14 自定义背景图 ----
    // 之前所有玻璃卡片都做成半透明磨砂，正是为了在根级画布铺一张用户自己的图片，
    // 让玻璃层透出背景。这里持久化 SAF 授权的 content:// URI（可跨重启读取）。

    /** 自定义背景图的 content:// URI；null = 使用默认糖果渐变背景。 */
    private val _bgImageUri = MutableStateFlow(sp.getString("bg_image_uri", null))
    val bgImageUri: StateFlow<String?> = _bgImageUri.asStateFlow()

    fun setBgImageUri(uri: String?) {
        sp.edit().putString("bg_image_uri", uri).apply()
        _bgImageUri.value = uri
    }

    /** 背景图之上叠加的柔化遮罩强度 0f~0.7f：越大越朦胧、玻璃文字越清晰。默认 0.28f。 */
    private val _bgDim = MutableStateFlow(sp.getFloat("bg_dim", 0.28f).coerceIn(0f, 0.7f))
    val bgDim: StateFlow<Float> = _bgDim.asStateFlow()

    fun setBgDim(value: Float) {
        val v = value.coerceIn(0f, 0.7f)
        sp.edit().putFloat("bg_dim", v).apply()
        _bgDim.value = v
    }

    // ---- v1.4.16：背景图缩放 / 定位 ----
    /** 背景图缩放 1f~4f，1f = 铺满（Crop）。 */
    private val _bgScale = MutableStateFlow(sp.getFloat("bg_scale", 1f).coerceIn(1f, 4f))
    val bgScale: StateFlow<Float> = _bgScale.asStateFlow()

    fun setBgScale(value: Float) {
        val v = value.coerceIn(1f, 4f)
        sp.edit().putFloat("bg_scale", v).apply()
        _bgScale.value = v
    }

    /** 背景图水平定位 -1f~1f（相对可平移范围，0 = 居中）。 */
    private val _bgOffsetX = MutableStateFlow(sp.getFloat("bg_offset_x", 0f).coerceIn(-1f, 1f))
    val bgOffsetX: StateFlow<Float> = _bgOffsetX.asStateFlow()

    /** 背景图垂直定位 -1f~1f（相对可平移范围，0 = 居中）。 */
    private val _bgOffsetY = MutableStateFlow(sp.getFloat("bg_offset_y", 0f).coerceIn(-1f, 1f))
    val bgOffsetY: StateFlow<Float> = _bgOffsetY.asStateFlow()

    fun setBgOffset(x: Float, y: Float) {
        val nx = x.coerceIn(-1f, 1f)
        val ny = y.coerceIn(-1f, 1f)
        sp.edit().putFloat("bg_offset_x", nx).putFloat("bg_offset_y", ny).apply()
        _bgOffsetX.value = nx
        _bgOffsetY.value = ny
    }

    fun resetBgTransform() {
        sp.edit().putFloat("bg_scale", 1f).putFloat("bg_offset_x", 0f).putFloat("bg_offset_y", 0f).apply()
        _bgScale.value = 1f
        _bgOffsetX.value = 0f
        _bgOffsetY.value = 0f
    }
}
