package com.vps.qing.core.notify

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知偏好（v1.4.17）。全部默认关闭或保守值：
 *  - 总开关默认 false —— 通知权限既然默认关闭，就绝不主动打扰用户；
 *  - 各分类开关默认 true，但只有总开关打开后才有意义（用户开启通知即获得完整分类，
 *    再逐项精简）；
 *  - 免打扰默认 23:00–07:00 且默认关闭，不偷偷改用户作息。
 */
class NotificationPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("qingvps_notifications", Context.MODE_PRIVATE)

    private fun readChannels(): Map<String, Boolean> =
        NotificationCatalog.ALL.associate { it.id to sp.getBoolean("channel_${it.id}", true) }

    private val _master = MutableStateFlow(sp.getBoolean("master", false))
    val master: StateFlow<Boolean> = _master.asStateFlow()

    private val _channels = MutableStateFlow(readChannels())
    val channels: StateFlow<Map<String, Boolean>> = _channels.asStateFlow()

    private val _dnd = MutableStateFlow(sp.getBoolean("dnd", false))
    val dnd: StateFlow<Boolean> = _dnd.asStateFlow()

    private val _dndStart = MutableStateFlow(sp.getInt("dnd_start", 23 * 60))
    val dndStart: StateFlow<Int> = _dndStart.asStateFlow()

    private val _dndEnd = MutableStateFlow(sp.getInt("dnd_end", 7 * 60))
    val dndEnd: StateFlow<Int> = _dndEnd.asStateFlow()

    private val _sound = MutableStateFlow(sp.getBoolean("sound", true))
    val sound: StateFlow<Boolean> = _sound.asStateFlow()

    private val _vibrate = MutableStateFlow(sp.getBoolean("vibrate", false))
    val vibrate: StateFlow<Boolean> = _vibrate.asStateFlow()

    fun setMaster(v: Boolean) {
        sp.edit().putBoolean("master", v).apply(); _master.value = v
    }

    fun setChannel(id: String, v: Boolean) {
        sp.edit().putBoolean("channel_$id", v).apply()
        _channels.value = _channels.value + (id to v)
    }

    fun setDnd(v: Boolean) {
        sp.edit().putBoolean("dnd", v).apply(); _dnd.value = v
    }

    fun setDndWindow(startMinute: Int, endMinute: Int) {
        val s = startMinute.coerceIn(0, 1439)
        val e = endMinute.coerceIn(0, 1439)
        sp.edit().putInt("dnd_start", s).putInt("dnd_end", e).apply()
        _dndStart.value = s; _dndEnd.value = e
    }

    fun setSound(v: Boolean) {
        sp.edit().putBoolean("sound", v).apply(); _sound.value = v
    }

    fun setVibrate(v: Boolean) {
        sp.edit().putBoolean("vibrate", v).apply(); _vibrate.value = v
    }

    fun isChannelEnabled(id: String): Boolean = _channels.value[id] ?: true
}
