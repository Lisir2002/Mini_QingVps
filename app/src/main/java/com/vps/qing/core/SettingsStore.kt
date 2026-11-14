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
    }

    private val sp = context.applicationContext
        .getSharedPreferences("qingvps_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(sp.getInt("theme_mode", THEME_SYSTEM))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun setThemeMode(mode: Int) {
        sp.edit().putInt("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    /** 高级主题色板 id（v1.4.0），默认星云蓝紫。 */
    private val _accentId = MutableStateFlow(sp.getString("accent_id", null) ?: "nebula")
    val accentId: StateFlow<String> = _accentId.asStateFlow()

    fun setAccentId(id: String) {
        sp.edit().putString("accent_id", id).apply()
        _accentId.value = id
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
}
