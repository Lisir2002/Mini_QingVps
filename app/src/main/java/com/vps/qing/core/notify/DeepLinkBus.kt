package com.vps.qing.core.notify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知点击 → 应用内原生页的路由总线（v1.4.17）。
 *
 * MainActivity 收到通知的 contentIntent 后 emit token，MainScreen 收集后转换成
 * 首页快捷入口 / 公告直达 / 个人中心子页，保证「点通知永远落在 App 内对应页面」。
 * 用 StateFlow（replay=1）而非 SharedFlow：冷启动时 Activity 先收到 intent、
 * MainScreen 稍后才订阅，也不会丢掉这次点击。
 */
object DeepLinkBus {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun emit(token: String) {
        if (token.isNotBlank()) _pending.value = token
    }

    /** 取走并清空，避免配置变更 / 重组时重复跳转。 */
    fun consume(): String? {
        val value = _pending.value
        _pending.value = null
        return value
    }
}
