package com.vps.qing.core.notify

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vps.qing.core.AppContainer
import com.vps.qing.core.permissions.PermissionCatalog
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors

/**
 * 「通知管理」页（v1.4.17 完整通知体系的配置入口）。
 *
 * 与权限管理联动：未同意通知权限 / 系统未授权时，明确告知并提供一键跳转，
 * 不会出现「开关全开却收不到通知」的困惑。所有通知点击都落在 App 内原生页。
 */
@Composable
fun NotificationSettingsPage(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = AppContainer.notificationPrefs
    val permissionMap by AppContainer.permissionPrefs.enabled.collectAsState()
    val permissionOn = permissionMap[PermissionCatalog.NOTIFICATIONS] ?: false
    val systemOn = QingNotifier.systemAllowed(context)

    val master by prefs.master.collectAsState()
    val channels by prefs.channels.collectAsState()
    val dnd by prefs.dnd.collectAsState()
    val dndStart by prefs.dndStart.collectAsState()
    val dndEnd by prefs.dndEnd.collectAsState()
    val sound by prefs.sound.collectAsState()
    val vibrate by prefs.vibrate.collectAsState()

    var startSlider by remember(dndStart) { mutableFloatStateOf(dndStart.toFloat()) }
    var endSlider by remember(dndEnd) { mutableFloatStateOf(dndEnd.toFloat()) }

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "通知管理",
                subtitle = "按业务分类管理提醒，随时可关",
                navIcon = AppIcons.Back,
                onNavClick = onBack
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiT.pagePad, vertical = 10.dp)
                    .padding(bottom = UiT.bottomBarSpace),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---- 权限状态：知情权显式呈现 ----
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(UiT.cardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "通知权限状态",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = CartoonColors.Ink
                        )
                        Text(
                            when {
                                !permissionOn -> "你尚未在「权限管理」中同意开启通知权限，应用不会发送任何通知。"
                                !systemOn -> "系统通知权限未授予，请在系统设置中允许通知。"
                                else -> "通知权限已开启，下面可按需精简各类提醒。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = CartoonColors.InkSub
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!permissionOn) {
                                CartoonButton(
                                    text = "去权限管理开启",
                                    onClick = onOpenPermissions,
                                    icon = AppIcons.Shield,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            CartoonButton(
                                text = "系统通知设置",
                                onClick = { openSystemNotificationSettings(context) },
                                icon = AppIcons.Settings,
                                containerColor = CartoonColors.Lilac,
                                modifier = if (permissionOn) Modifier.fillMaxWidth() else Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ---- 总开关 ----
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SettingSwitchRow(
                        title = "接收通知",
                        subtitle = "关闭后所有分类都不再提醒，应用内消息不受影响",
                        checked = master,
                        accent = CartoonColors.Brand,
                        onChanged = prefs::setMaster
                    )
                }

                SectionTitle("通知分类", CartoonColors.Sky)
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        NotificationCatalog.ALL.forEach { spec ->
                            SettingSwitchRow(
                                title = spec.title,
                                subtitle = spec.description,
                                checked = channels[spec.id] ?: true,
                                enabled = master,
                                accent = CartoonColors.Sky,
                                onChanged = { prefs.setChannel(spec.id, it) }
                            )
                        }
                    }
                }

                SectionTitle("免打扰", CartoonColors.Lilac)
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(UiT.cardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingSwitchRow(
                            title = "免打扰时段",
                            subtitle = "时段内的提醒会被抑制，不会补发打扰",
                            checked = dnd,
                            accent = CartoonColors.Lilac,
                            onChanged = prefs::setDnd
                        )
                        Text(
                            "开始 ${formatMinute(startSlider.toInt())} · 结束 ${formatMinute(endSlider.toInt())}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CartoonColors.Ink
                        )
                        Text("开始时间", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                        Slider(
                            value = startSlider,
                            onValueChange = { startSlider = it },
                            onValueChangeFinished = {
                                prefs.setDndWindow(startSlider.toInt(), endSlider.toInt())
                            },
                            valueRange = 0f..1439f,
                            enabled = dnd
                        )
                        Text("结束时间", style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                        Slider(
                            value = endSlider,
                            onValueChange = { endSlider = it },
                            onValueChangeFinished = {
                                prefs.setDndWindow(startSlider.toInt(), endSlider.toInt())
                            },
                            valueRange = 0f..1439f,
                            enabled = dnd
                        )
                    }
                }

                SectionTitle("提醒方式", CartoonColors.Mint)
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        SettingSwitchRow(
                            title = "提示音",
                            subtitle = "重要通知（支付/续费/安全）带声音",
                            checked = sound,
                            accent = CartoonColors.Mint,
                            onChanged = prefs::setSound
                        )
                        SettingSwitchRow(
                            title = "振动",
                            subtitle = "通知到达时短振两次",
                            checked = vibrate,
                            accent = CartoonColors.Mint,
                            onChanged = prefs::setVibrate
                        )
                    }
                }

                // ---- 链路自检 ----
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(UiT.cardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "通知链路自检",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = CartoonColors.Ink
                        )
                        Text(
                            "发送一条测试通知，验证权限、渠道、点击落点是否正常。",
                            style = MaterialTheme.typography.bodySmall,
                            color = CartoonColors.InkSub
                        )
                        CartoonButton(
                            text = "发送测试通知",
                            onClick = {
                                val ok = QingNotifier.test(context)
                                Toast.makeText(
                                    context,
                                    if (ok) "已发送，下拉通知栏查看" else "未发送：请先开启通知权限、总开关，并避开免打扰时段",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            icon = AppIcons.Bell,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(UiT.cardPad), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "我们如何通知你",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = CartoonColors.Ink
                        )
                        Text(
                            "· 5 类业务独立渠道与开关：订单支付 / 工单回复 / 公告活动 / 续费到期 / 账号安全\n" +
                                "· 同一事件只提醒一次，不会重复打扰\n" +
                                "· 点击通知直达 App 内对应原生页，不跳转网页\n" +
                                "· 未经你同意，应用不会申请通知权限，也不会发送任何通知",
                            style = MaterialTheme.typography.bodySmall,
                            color = CartoonColors.InkSub
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    accent: Color = CartoonColors.Brand,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiT.cardPad, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) CartoonColors.Ink else CartoonColors.InkSub
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChanged,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = CartoonColors.InkSub.copy(alpha = 0.35f)
            )
        )
    }
}

private fun formatMinute(minute: Int): String {
    val m = minute.coerceIn(0, 1439)
    return "%02d:%02d".format(m / 60, m % 60)
}

private fun openSystemNotificationSettings(context: android.content.Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
