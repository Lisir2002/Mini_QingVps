package com.vps.qing.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * v1.4.19：下载完成后由用户确认「是否直接安装」，通过 FileProvider 拉起系统安装器。
 *
 * Android 8+ 需要「安装未知应用」授权（REQUEST_INSTALL_PACKAGES）；
 * 未授权时 [needsInstallPermission] 返回 true，引导用户到系统设置页开启后再安装。
 * 绝不以 root / 静默安装等越权方式处理。
 */
object ApkInstaller {

    /** 下载目录：cacheDir/updates，与 res/xml/file_paths.xml 的 cache-path 对应。 */
    fun updateDir(context: Context): File = File(context.cacheDir, "updates")

    /** 目标 APK 文件：按 tag 命名，避免与旧包混淆。 */
    fun apkFile(context: Context, tag: String): File =
        File(updateDir(context), "QingVPS-${tag.trim().removePrefix("v")}-update.apk")

    /** Android 8+ 且未授予「安装未知应用」时为 true。 */
    fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()

    /** 打开本应用的「安装未知应用」授权页。 */
    fun openInstallPermissionSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /** 拉起系统安装界面；返回是否成功发起。 */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
