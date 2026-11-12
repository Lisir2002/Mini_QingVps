package com.vps.qing.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonIconChip
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.SectionTitle
import com.vps.qing.core.ui.Strings

/** 实名认证：站点未暴露 API，保留网页兜底。 */
private val certificationMenu = ServiceMenu(
    Strings.CERTIFICATION, "站点未开放接口，网页办理", AppIcons.Shield,
    CartoonColors.Peach, CartoonColors.PeachSoft, webPath = "plugin/22/certification.htm"
)

/**
 * 个人中心 v1.3.1：账号与服务每项独立子页面（点击进入整页，返回键/顶栏返回），
 * 应用设置（主题配色）也是独立子页面；弹窗仅保留登出确认与关于。
 */
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
    var showAbout by remember { mutableStateOf(false) }
    /** 当前打开的服务子页面；null = 个人中心主页。 */
    var servicePage by rememberSaveable { mutableStateOf<ServicePanel?>(null) }
    /** 应用设置子页面。 */
    var showSettingsPage by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    // 子页面打开时：进入懒加载 + 系统返回键回主页
    LaunchedEffect(servicePage) { servicePage?.let(vm::ensurePanel) }
    BackHandler(enabled = servicePage != null || showSettingsPage) {
        if (showSettingsPage) showSettingsPage = false else servicePage = null
    }

    servicePage?.let { panel ->
        ServicePageScreen(
            panel = panel,
            data = state.panels[panel],
            onBack = { servicePage = null },
            onRefresh = { vm.refreshPanel(panel) }
        )
        return
    }

    if (showSettingsPage) {
        ThemeSettingsPage(
            currentMode = themeMode,
            onSelectMode = { AppContainer.settings.setThemeMode(it) },
            onBack = { showSettingsPage = false }
        )
        return
    }

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = Strings.PROFILE,
                subtitle = "账号与应用设置",
                actions = {
                    CartoonIconChip(
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
                        InfoRow(AppIcons.Profile, "手机", state.account?.phone?.takeIf { it.isNotBlank() } ?: "未绑定")
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
                        // 账号与服务：每项点击进入独立子页面
                        ServicePanels.entries.forEach { menu ->
                            MenuRow(menu) { menu.panel?.let { servicePage = it } }
                        }
                        // 实名认证：站点无 API，网页兜底
                        MenuRow(certificationMenu) { onOpenWeb(AppContainer.webUrl(certificationMenu.webPath)) }
                    }
                }

                SectionTitle(Strings.APP_GROUP, CartoonColors.Lilac)
                GlassCard(modifier = Modifier.fillMaxWidth(), alpha = 0.82f) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        MenuRow(
                            ServiceMenu(Strings.SETTINGS, Strings.THEME, AppIcons.Settings, CartoonColors.Lilac, CartoonColors.LilacSoft)
                        ) { showSettingsPage = true }
                        MenuRow(
                            ServiceMenu(Strings.ABOUT, "", AppIcons.Info, CartoonColors.Sky, CartoonColors.SkySoft),
                            subtitle = "版本 ${Strings.APP_VERSION}"
                        ) { showAbout = true }
                    }
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

/**
 * 磨砂玻璃弹窗（v1.3.1 修复）：
 *  - 正文与按钮分离：[content] 在 ColumnScope 里纵向排列（修复主题选项被塞进
 *    横向 Row 槽导致 fillMaxWidth 后第一项占满整行、其余选项被挤出可视区的问题）；
 *  - 玻璃底改为近实色（白色 0.97 + 玻璃渐变叠加），背后内容不再透出。
 */
@Composable
private fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    text: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = CartoonKit.SheetShape,
            alpha = 1f,
            elevation = 24.dp
        ) {
            // 玻璃渐变之上再叠近实色白底，保证弹窗完全不透底
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.97f))
                    .padding(22.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = CartoonColors.Ink)
                if (text != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = CartoonColors.InkSub)
                }
                content?.let {
                    Spacer(Modifier.height(12.dp))
                    it()
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

/** 通用菜单行：图标 + 标题 + 副标题 + 右箭头（进入子页面/网页）。 */
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
                subtitle ?: menu.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
        }
        Icon(AppIcons.Forward, null, tint = CartoonColors.InkSub, modifier = Modifier.size(16.dp))
    }
}
