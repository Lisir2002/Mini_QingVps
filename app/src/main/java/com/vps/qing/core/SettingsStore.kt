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
}
