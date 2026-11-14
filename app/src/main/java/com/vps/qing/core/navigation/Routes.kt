package com.vps.qing.core.navigation

import android.net.Uri

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"
    /** 底栏 Tab 主容器（仪表盘 / 选购 / 消息 / 个人中心）。 */
    const val MAIN = "main"
    const val WEB = "web"
    /** v1.4.17：首次启动的独立「权限管理」页（所有权限默认关闭，由用户逐项决定）。 */
    const val PERMISSIONS = "permissions"

    /** WebView 兜底页：url 需要编码后放进路径，避免 ? & 被当作导航参数分隔。 */
    const val WEB_PATTERN = "$WEB/{url}"
    fun web(url: String) = "$WEB/${Uri.encode(url)}"
}
