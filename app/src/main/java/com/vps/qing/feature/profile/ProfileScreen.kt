package com.vps.qing.feature.profile

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.SettingsStore
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings

data class ServiceMenu(val title: String, val path: String, val icon: ImageVector, val accent: Color, val soft: Color)

/**
 * H5 兜底入口。IDCSmart 插件页格式为 /plugin/{id}/{page}.htm。
 * 实名认证按需放在第一项，其余为账号相关服务。
 */
object ProfileLinks {
    val entries: List<ServiceMenu> = listOf(
        ServiceMenu(Strings.CERTIFICATION, "plugin/22/certification.htm", AppIcons.Shield, CartoonColors.Peach, CartoonColors.PeachSoft),
        ServiceMenu(Strings.MY_ORDERS, "clientarea/order.htm", AppIcons.Order, CartoonColors.Brand, CartoonColors.BrandSoft),
        ServiceMenu(Strings.MY_HOSTS, "clientarea/host.htm", AppIcons.Server, CartoonColors.Mint, CartoonColors.MintSoft),
        ServiceMenu(Strings.FINANCE, "clientarea/finance.htm", AppIcons.Wallet, CartoonColors.Lemon, CartoonColors.LemonSoft),
        ServiceMenu(Strings.TICKETS, "plugin/27/ticket.htm", AppIcons.Ticket, CartoonColors.Sky, CartoonColors.SkySoft),
        ServiceMenu(Strings.MESSAGES, "plugin/30/announcement.htm", AppIcons.Message, CartoonColors.Lilac, CartoonColors.LilacSoft),
        ServiceMenu(Strings.SECURITY, "clientarea/security.htm", AppIcons.Lock, CartoonColors.Brand, CartoonColors.BrandSoft)
    )
}

/** 个人中心 Tab（纯白卡通重构）：个人信息 + 服务菜单 + 应用设置/关于 + 退出登录。 */
@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    onOpenWeb: (String) -> Unit
) {
    val vm: ProfileViewModel = viewModel(
        factory = simpleViewModelFactory { ProfileViewModel(AppContainer.profileRepository) }
    )
    val state by vm.ui.collectAsState()
    val themeMode by AppContainer.settings.themeMode.collectAsState()

    var showLogout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.PROFILE,
                subtitle = "账号与应用设置",
                actions = {
                    com.vps.qing.core.ui.CartoonIconChip(
                        icon = AppIcons.Refresh,
                        onClick = { vm.load() },
                        tint = CartoonColors.Mint,
                        bg = CartoonColors.MintSoft
                    )
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 账号卡
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.85f) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(50.dp)
                                    .clip(CartoonKit.CardShape)
                                    .background(CartoonColors.BrandSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(AppIcons.Profile, null, tint = CartoonColors.Brand, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Strings.ACCOUNT, style = MaterialTheme.typography.labelMedium, color = CartoonColors.InkSub)
                                Text(
                                    state.account?.username ?: Strings.UNKNOWN,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CartoonColors.Ink
                                )
                            }
                        }
                        InfoRow(AppIcons.Mail, "邮箱", state.account?.email ?: Strings.UNKNOWN)
                        InfoRow(AppIcons.Profile, "手机", state.account?.phone ?: Strings.UNKNOWN)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            InfoInline(AppIcons.Wallet, Strings.BALANCE, state.account?.balance ?: Strings.UNKNOWN, Modifier.weight(1f))
                            InfoInline(AppIcons.Tag, Strings.CREDIT, state.account?.creditLimit ?: Strings.UNKNOWN, Modifier.weight(1f))
                        }
                    }
                }

                if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CartoonColors.Brand)
                state.error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    CartoonButton(text = Strings.RETRY, onClick = { vm.load() })
                }

                SectionTitle(Strings.ACCOUNT_SERVICES, CartoonColors.Brand)
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.82f) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        ProfileLinks.entries.forEach { menu ->
                            MenuRow(menu) { onOpenWeb(AppContainer.webUrl(menu.path)) }
                        }
                    }
                }

                SectionTitle(Strings.APP_GROUP, CartoonColors.Lilac)
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.82f) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        MenuRow(
                            ServiceMenu(Strings.SETTINGS, "", AppIcons.Settings, CartoonColors.Lilac, CartoonColors.LilacSoft),
                            subtitle = Strings.THEME
                        ) { showSettings = true }
                        MenuRow(
                            ServiceMenu(Strings.ABOUT, "", AppIcons.Info, CartoonColors.Sky, CartoonColors.SkySoft),
                            subtitle = "版本 ${Strings.APP_VERSION}"
                        ) { showAbout = true }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Info, null, tint = CartoonColors.InkSub, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Strings.WEB_FALLBACK_TIP, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                }

                CartoonButton(
                    text = Strings.LOGOUT,
                    onClick = { showLogout = true },
                    icon = AppIcons.Logout,
                    containerColor = CartoonColors.Peach,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showLogout) {
        GlassDialog(title = Strings.LOGOUT, text = Strings.LOGOUT_CONFIRM, onDismiss = { showLogout = false }) {
            TextButton(onClick = { showLogout = false }) { Text(Strings.CANCEL, color = CartoonColors.InkSub) }
            TextButton(onClick = { showLogout = false; vm.logout() }) {
                Text(Strings.CONFIRM, fontWeight = FontWeight.Bold, color = CartoonColors.Brand)
            }
        }
    }

    if (showSettings) {
        GlassDialog(title = Strings.THEME, onDismiss = { showSettings = false }) {
            val options = listOf(
                Strings.THEME_SYSTEM to SettingsStore.THEME_SYSTEM,
                Strings.THEME_LIGHT to SettingsStore.THEME_LIGHT,
                Strings.THEME_DARK to SettingsStore.THEME_DARK
            )
            options.forEach { (label, mode) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            AppContainer.settings.setThemeMode(mode)
                            showSettings = false
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = CartoonColors.Brand)
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = CartoonColors.Ink)
                }
            }
        }
    }

    if (showAbout) {
        GlassDialog(
            title = Strings.ABOUT,
            text = Strings.ABOUT_TEXT.replace("%s", Strings.APP_VERSION),
            onDismiss = { showAbout = false }
        ) {
            TextButton(onClick = { showAbout = false }) {
                Text(Strings.CONFIRM, fontWeight = FontWeight.Bold, color = CartoonColors.Brand)
            }
        }
    }
}

/** 磨砂玻璃弹窗。 */
@Composable
private fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    text: String? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = CartoonKit.SheetShape,
            alpha = 0.94f,
            elevation = 24.dp
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = CartoonColors.Ink)
                if (text != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = CartoonColors.InkSub)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) { actions() }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label：$value", style = MaterialTheme.typography.bodyMedium, color = CartoonColors.Ink)
    }
}

@Composable
private fun InfoInline(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = CartoonColors.Mint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("$label $value", style = MaterialTheme.typography.labelMedium, color = CartoonColors.InkSub, maxLines = 1)
    }
}

@Composable
private fun MenuRow(menu: ServiceMenu, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(menu.soft),
            contentAlignment = Alignment.Center
        ) {
            Icon(menu.icon, null, tint = menu.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(menu.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
            Text(
                subtitle ?: Strings.WEB_ENTRY_HINT,
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
        }
        Icon(AppIcons.Forward, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
    }
}
