package com.vps.qing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.vps.qing.core.AppContainer
import com.vps.qing.core.navigation.Routes
import com.vps.qing.core.network.UnauthorizedBus
import com.vps.qing.core.notify.DeepLinkBus
import com.vps.qing.core.notify.QingNotifier
import com.vps.qing.core.permissions.PermissionStatus
import com.vps.qing.core.ui.theme.QingVpsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // v1.4.16：edge-to-edge + 透明系统栏。根级 CartoonBackground 铺满整窗，
        // 自定义背景图一直延伸到状态栏后面，消除「状态栏一条不透明色带」的切割。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppContainer.init(applicationContext)

        // v1.4.17：通知点击带来的应用内落点，先暂存；StateFlow 保证 MainScreen
        // 稍后订阅时仍能收到（冷启动也不会丢）。
        val initialRoute = intent?.getStringExtra(QingNotifier.EXTRA_ROUTE)

        setContent {
            // 应用设置里的主题 / 字体 / 主题自设主色写入 SettingsStore，这里收集后即时全局生效
            val themeMode by AppContainer.settings.themeMode.collectAsState()
            val accentId by AppContainer.settings.accentId.collectAsState()
            val fontId by AppContainer.settings.fontId.collectAsState()
            val customAccent by AppContainer.settings.customAccent.collectAsState()
            QingVpsTheme(
                themeMode = themeMode,
                accentId = accentId,
                fontId = fontId,
                customAccent = customAccent
            ) {
                // 业务 401 后整个导航图重建到登录页，避免返回键回到已失效的 Tab 容器
                var sessionEpoch by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    UnauthorizedBus.events.collect { sessionEpoch++ }
                }
                LaunchedEffect(Unit) {
                    initialRoute?.let { DeepLinkBus.emit(it) }
                }

                key(sessionEpoch) {
                    val navController = rememberNavController()
                    AppNavHost(
                        navController = navController,
                        // v1.4.17：首次启动必须先经过独立权限管理页（所有权限默认关闭）
                        startDestination = when {
                            !AppContainer.permissionPrefs.onboardingDone.value -> Routes.PERMISSIONS
                            AppContainer.isLoggedIn() -> Routes.MAIN
                            else -> Routes.LOGIN
                        }
                    )
                }
            }
        }
    }

    /**
     * v1.4.18：从系统设置 / 应用设置返回，或前后台切换时，立刻重读系统真实授权。
     * 这是「在设置里开启通知，App 内状态实时同步」的关键：StateFlow 一更新，
     * 权限管理页与通知管理页的开关、状态文案即刻重组，无需重启应用。
     */
    override fun onResume() {
        super.onResume()
        PermissionStatus.refresh(applicationContext)
    }

    /** 通知点击在应用已前台时走 onNewIntent（launchMode=singleTop）。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(QingNotifier.EXTRA_ROUTE)?.let { DeepLinkBus.emit(it) }
    }
}
