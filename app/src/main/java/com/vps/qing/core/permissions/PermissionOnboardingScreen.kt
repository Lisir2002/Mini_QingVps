package com.vps.qing.core.permissions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vps.qing.core.AppContainer
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors

/**
 * 独立「权限管理」页（v1.4.17）。
 *
 * 两处复用同一份 UI：
 *  - 首次启动：[isOnboarding] = true，作为顶层导航页，完成后写入 onboardingDone 进入应用；
 *  - 应用设置 → 权限管理：[isOnboarding] = false，作为个人中心子页，随时复管。
 *
 * 所有权限默认关闭；必要权限只做说明与一次性申请，可选权限由用户逐项勾选。
 * 系统运行时权限的申请结果会回填到开关，被拒绝时自动回滚，绝不出现「开关开着但没权限」。
 */
@Composable
fun PermissionOnboardingScreen(
    onDone: () -> Unit,
    isOnboarding: Boolean = true,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = AppContainer.permissionPrefs
    val enabled by prefs.enabled.collectAsState()
    var pendingId by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val id = pendingId ?: return@rememberLauncherForActivityResult
        pendingId = null
        val spec = PermissionCatalog.byId(id)
        val granted = spec?.let { systemGranted(context, it) } ?: result.values.all { it }
        if (granted) {
            prefs.setEnabled(id, true)
            toast(context, "已开启「${spec?.label ?: id}」")
        } else {
            prefs.setEnabled(id, false)
            toast(context, "未获得系统授权，该项保持关闭")
        }
    }

    val toggle: (PermissionSpec, Boolean) -> Unit = { spec, value ->
        if (!value) {
            prefs.setEnabled(spec.id, false)
            toast(context, "已关闭「${spec.label}」")
        } else {
            val runtime = PermissionCatalog.runtimePermissions(spec)
            if (runtime.isNotEmpty() && !systemGranted(context, spec)) {
                pendingId = spec.id
                runCatching { launcher.launch(runtime.toTypedArray()) }
                    .onFailure { pendingId = null; toast(context, "无法发起权限申请") }
            } else {
                prefs.setEnabled(spec.id, true)
                toast(context, "已开启「${spec.label}」")
            }
        }
    }

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "权限管理",
                subtitle = if (isOnboarding) "首次使用 · 由你决定是否开启" else "随时查看与调整权限",
                navIcon = if (isOnboarding) null else AppIcons.Back,
                onNavClick = onBack
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiT.pagePad, vertical = 10.dp)
                    .padding(bottom = if (isOnboarding) 24.dp else UiT.bottomBarSpace),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(UiT.cardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "尊重你的知情权",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = CartoonColors.Ink
                        )
                        Text(
                            "QingVPS 首次启动时所有权限都是关闭的。下面逐项说明用途与不开通的影响：" +
                                "必要权限用于连接站点与读取你主动选择的图片；可选权限完全由你决定，" +
                                "不开启不影响购买、支付与管理。我们不会在你同意之前申请任何权限，" +
                                "也不会把定位、相册内容上传服务器。",
                            style = MaterialTheme.typography.bodySmall,
                            color = CartoonColors.InkSub
                        )
                        Text(
                            "已开启 ${enabled.values.count { it }} / ${PermissionCatalog.ALL.size} 项",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CartoonColors.Brand
                        )
                    }
                }

                SectionTitle("必要权限 · 建议开通", CartoonColors.Brand)
                PermissionCatalog.REQUIRED.forEach { spec ->
                    PermissionCard(
                        spec = spec,
                        enabled = enabled[spec.id] ?: false,
                        granted = systemGranted(context, spec),
                        onToggle = { v -> toggle(spec, v) },
                        onOpenSystemSettings = { openAppSettings(context) }
                    )
                }

                SectionTitle("可选权限 · 由你选择", CartoonColors.Lilac)
                PermissionCatalog.OPTIONAL.forEach { spec ->
                    PermissionCard(
                        spec = spec,
                        enabled = enabled[spec.id] ?: false,
                        granted = systemGranted(context, spec),
                        onToggle = { v -> toggle(spec, v) },
                        onOpenSystemSettings = { openAppSettings(context) }
                    )
                }

                if (isOnboarding) {
                    CartoonButton(
                        text = "完成，进入应用",
                        onClick = {
                            prefs.completeOnboarding()
                            onDone()
                        },
                        icon = AppIcons.Check,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "也可以保持全部关闭直接使用，之后在「我的 → 权限管理」随时调整。",
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    CartoonButton(
                        text = "保存并返回",
                        onClick = onDone,
                        icon = AppIcons.Check,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    spec: PermissionSpec,
    enabled: Boolean,
    granted: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    val required = spec.group == PermissionGroup.REQUIRED
    val accent = if (required) CartoonColors.Brand else CartoonColors.Lilac
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(UiT.cardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CartoonKit.ItemShape).background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconFor(spec.id), null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        spec.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = CartoonColors.Ink
                    )
                    Text(
                        if (required) "必要权限" else "可选权限",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = CartoonColors.InkSub.copy(alpha = 0.35f)
                    )
                )
            }
            PermissionInfoLine("用途", spec.purpose)
            PermissionInfoLine("不开启", spec.deniedImpact)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val stateText = when {
                    enabled && granted -> "已开启 · 系统已授权"
                    enabled && !granted -> "已开启 · 系统未授权（点此去系统设置）"
                    !enabled && granted -> "应用内已关闭 · 系统权限仍为授权"
                    else -> "未开启"
                }
                Text(
                    stateText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled && granted) CartoonColors.Mint else CartoonColors.InkSub,
                    modifier = Modifier.weight(1f)
                )
                if (!enabled && granted && PermissionCatalog.runtimePermissions(spec).isNotEmpty()) {
                    Text(
                        "系统设置 ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.Brand,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOpenSystemSettings() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionInfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CartoonColors.InkSub,
            modifier = Modifier.width(46.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = CartoonColors.InkSub,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun iconFor(id: String): ImageVector = when (id) {
    PermissionCatalog.NETWORK -> AppIcons.Globe
    PermissionCatalog.STORAGE -> AppIcons.Image
    PermissionCatalog.NOTIFICATIONS -> AppIcons.Bell
    PermissionCatalog.LOCATION -> AppIcons.Globe
    PermissionCatalog.EXTERNAL_APPS -> AppIcons.ExternalLink
    PermissionCatalog.CLIPBOARD -> AppIcons.Sliders
    PermissionCatalog.CAMERA -> AppIcons.Image
    else -> AppIcons.Shield
}

/** 安装期权限（INTERNET 等）永远视为已授予；运行时权限逐项检查。 */
private fun systemGranted(context: Context, spec: PermissionSpec): Boolean {
    val runtime = PermissionCatalog.runtimePermissions(spec)
    if (runtime.isEmpty()) return true
    return runtime.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
