package com.vps.qing.core.notify

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
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
import com.vps.qing.core.permissions.PermissionStatus
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors
import java.util.Calendar

/**
 * 「通知管理」页（v1.4.17 建立 / v1.4.18 实时化与文案校准）。
 *
 * v1.4.18 修复：
 *  - 顶栏避开状态栏（此前作为整页渲染时标题与状态栏时间堆叠）；
 *  - 系统授权状态来自 [PermissionStatus] 的 StateFlow，MainActivity.onResume 会重读，
 *    在系统设置里开启 / 撤销通知后回到本页**即时同步**，不再是一次性快照；
 *  - 顶部逐项列出「四道闸」的实时状态，并提供一键联动开启，解决
 *    「设置页开了通知、App 里仍显示未开启 / 收不到」的问题；
 *  - 修正「提示音」「我们如何通知你」等与实际实现不一致的说明文案。
 */
@Composable
fun NotificationSettingsPage(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = AppContainer.notificationPrefs
    val permissionPrefs = AppContainer.permissionPrefs
    val permissionMap by permissionPrefs.enabled.collectAsState()
    val permissionOn = permissionMap[PermissionCatalog.NOTIFICATIONS] ?: false
    val systemStatus by PermissionStatus.snapshot.collectAsState()
    val systemOn = systemStatus.notificationsSystemGranted

    val master by prefs.master.collectAsState()
    val channels by prefs.channels.collectAsState()
    val dnd by prefs.dnd.collectAsState()
    val dndStart by prefs.dndStart.collectAsState()
    val dndEnd by prefs.dndEnd.collectAsState()
    val sound by prefs.sound.collectAsState()
    val vibrate by prefs.vibrate.collectAsState()

    var startSlider by remember(dndStart) { mutableFloatStateOf(dndStart.toFloat()) }
    var endSlider by remember(dndEnd) { mutableFloatStateOf(dndEnd.toFloat()) }

    LaunchedEffect(Unit) { PermissionStatus.ensureFresh(context) }

    // Android 13+ 运行时通知权限申请；成功后把「应用内同意 + 总开关」一并补齐，
    // 让用户在系统设置里的操作立刻反映到 App 内。
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        PermissionStatus.refresh(context)
        if (granted && PermissionStatus.snapshot.value.notificationsSystemGranted) {
            permissionPrefs.setEnabled(PermissionCatalog.NOTIFICATIONS, true)
            prefs.setMaster(true)
            Toast.makeText(context, "通知已开启", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "系统通知未授权，可在系统设置中允许通知", Toast.LENGTH_LONG).show()
        }
    }

    fun enableAll() {
        PermissionStatus.refresh(context)
        val sys = PermissionStatus.snapshot.value.notificationsSystemGranted
        when {
            !sys && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            !sys -> openSystemNotificationSettings(context)
            else -> {
                permissionPrefs.setEnabled(PermissionCatalog.NOTIFICATIONS, true)
                prefs.setMaster(true)
                PermissionStatus.refresh(context)
                Toast.makeText(context, "通知已开启", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val nowMinute = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }
    val dndBlocking = dnd && NotificationCatalog.isInDnd(dndStart, dndEnd, nowMinute)
    val gates = listOf(
        "应用内已同意通知权限" to permissionOn,
        "通知总开关已打开" to master,
        "系统已授予通知权限" to systemOn,
        "当前时段允许推送" to !dndBlocking
    )
    val allReady = gates.all { it.second }

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
                // ---- 四道闸实时状态 ----
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(UiT.cardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "通知可用性（实时）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = CartoonColors.Ink
                        )
                        gates.forEach { (label, ok) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (ok) AppIcons.Check else AppIcons.Close,
                                    null,
                                    tint = if (ok) CartoonColors.Mint else CartoonColors.Coral,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (ok) CartoonColors.Ink else CartoonColors.Coral
                                )
                            }
                        }
                        Text(
                            if (allReady) "四道闸全部通过，符合条件时会正常收到通知。" else
                                "上面任一项未通过都不会发送通知；系统设置里的授权变化会即时同步到这里。",
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!allReady) {
                                CartoonButton(
                                    text = "一键开启通知",
                                    onClick = { enableAll() },
                                    icon = AppIcons.Bell,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            CartoonButton(
                                text = "系统通知设置",
                                onClick = { openSystemNotificationSettings(context) },
                                icon = AppIcons.Settings,
                                containerColor = CartoonColors.Lilac,
                                modifier = if (allReady) Modifier.fillMaxWidth() else Modifier.weight(1f)
                            )
                        }
                        if (!permissionOn) {
                            Text(
                                "也可在「我的 → 权限管理」逐项查看并同意权限。",
                                style = MaterialTheme.typography.labelSmall,
                                color = CartoonColors.InkSub,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            CartoonButton(
                                text = "去权限管理查看",
                                onClick = onOpenPermissions,
                                icon = AppIcons.Shield,
                                containerColor = CartoonColors.Sky,
                                modifier = Modifier.fillMaxWidth()
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
                            // v1.4.18：与实现一致——这是「是否静音」的全局开关，不是只作用于重要通知
                            subtitle = "关闭后所有通知静音；开启时高重要性通知（订单/续费/安全）会有提示音",
                            checked = sound,
                            accent = CartoonColors.Mint,
                            onChanged = prefs::setSound
                        )
                        SettingSwitchRow(
                            title = "振动",
                            subtitle = "开启后通知到达时短振两次（系统可能按渠道设置覆盖）",
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
                                    if (ok) "已发送，下拉通知栏查看" else "未发送：请先一键开启通知，并避开免打扰时段",
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
                                "· 触发时机：每次成功刷新首页数据时对比公告、待支付订单与到期时间；" +
                                "工单详情有新回复、密码/邮箱/手机变更成功、支付成功时即时提醒\n" +
                                "· 同一事件只提醒一次，不会重复打扰\n" +
                                "· 点击通知直达对应 App 内原生页，不跳转网页\n" +
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
