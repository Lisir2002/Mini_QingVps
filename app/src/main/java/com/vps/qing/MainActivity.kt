package com.vps.qing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.vps.qing.core.ui.theme.QingVpsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(applicationContext)
        setContent {
            // 个人中心的「应用设置」写入 SettingsStore，这里收集后让主题（深浅+色板）立即生效
            val themeMode by AppContainer.settings.themeMode.collectAsState()
            val accentId by AppContainer.settings.accentId.collectAsState()
            QingVpsTheme(themeMode = themeMode, accentId = accentId) {
                // 业务 401 后整个导航图重建到登录页，避免返回键回到已失效的 Tab 容器
                var sessionEpoch by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    UnauthorizedBus.events.collect { sessionEpoch++ }
                }

                key(sessionEpoch) {
                    val navController = rememberNavController()
                    AppNavHost(
                        navController = navController,
                        startDestination = if (AppContainer.isLoggedIn()) Routes.MAIN else Routes.LOGIN
                    )
                }
            }
        }
    }
}
