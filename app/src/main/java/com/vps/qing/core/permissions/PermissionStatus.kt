package com.vps.qing.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vps.qing.core.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 系统权限授权的**实时快照**（v1.4.18）。
 *
 * 背景：用户在「系统设置 / 应用设置」里开启或撤销通知权限后返回 App，
 * 旧版页面里 `systemGranted()` / `systemAllowed()` 只是组合期的一次性普通函数调用，
 * 不是 State，Compose 不会重组，于是「设置里开了、App 里还显示关着」——
 * 权限状态既不及时也不准确。
 *
 * 现在由 [refresh] 统一读取系统真实授权，写入 StateFlow；MainActivity.onResume
 * 与权限申请结果回调都会触发刷新，所有页面通过 collectAsState 即时同步。
 * 同时把「系统已撤销」回滚到 App 内的同意标记，杜绝「开关开着却收不到通知」。
 */
object PermissionStatus {

    data class Snapshot(
        /** 权限 id → 系统层面是否已授权（安装期权限恒为 true）。 */
        val granted: Map<String, Boolean> = emptyMap(),
        /** 通知：系统总开关 + Android 13+ POST_NOTIFICATIONS 同时满足。 */
        val notificationsSystemGranted: Boolean = false,
        val refreshedAtMs: Long = 0L
    ) {
        fun isGranted(id: String): Boolean = granted[id] ?: false

        /** 无运行时权限的项（INTERNET 等安装期权限）恒视为已授权。 */
        fun isGranted(spec: PermissionSpec): Boolean =
            PermissionCatalog.runtimePermissions(spec).isEmpty() || isGranted(spec.id)
    }

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    /** 读取系统真实授权并广播；同时把被系统撤销的「应用内同意」标记回滚。 */
    fun refresh(context: Context) {
        val ctx = context.applicationContext
        val granted = PermissionCatalog.ALL.associate { spec ->
            val runtime = PermissionCatalog.runtimePermissions(spec)
            val ok = if (runtime.isEmpty()) true else runtime.all {
                ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
            }
            spec.id to ok
        }
        val notifyOk = NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED)

        // 系统撤销授权 → App 内「已同意通知」标记同步回滚，避免状态不对称。
        val prefs = AppContainer.permissionPrefs
        if (!notifyOk && prefs.isEnabled(PermissionCatalog.NOTIFICATIONS)) {
            prefs.setEnabled(PermissionCatalog.NOTIFICATIONS, false)
        }

        _snapshot.value = Snapshot(
            granted = granted,
            notificationsSystemGranted = notifyOk,
            refreshedAtMs = System.currentTimeMillis()
        )
    }

    /** 页面首次进入时若快照尚未初始化（refreshedAtMs=0），立即补一次同步读取。 */
    fun ensureFresh(context: Context) {
        if (_snapshot.value.refreshedAtMs == 0L) refresh(context)
    }
}
