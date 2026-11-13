package com.vps.qing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vps.qing.core.AppContainer
import com.vps.qing.core.navigation.Routes
import com.vps.qing.feature.auth.ForgotPasswordScreen
import com.vps.qing.feature.auth.LoginScreen
import com.vps.qing.feature.auth.RegisterScreen
import com.vps.qing.feature.main.MainScreen
import com.vps.qing.feature.web.WebViewScreen

/**
 * 顶层导航：登录 / 注册 / 找回密码 是独立页；登录成功后进入 MAIN（底栏 Tab 容器）。
 * 站点插件页等长尾功能仍走 WebView 兜底，登录态由 queryParam 自动同步。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onSuccess = { _ ->
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onRegister = { navController.navigate(Routes.REGISTER) },
                onForgot = { navController.navigate(Routes.FORGOT) }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onSuccess = { autoLogin ->
                    if (autoLogin) {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onWebRegister = {
                    navController.navigate(Routes.web(AppContainer.webUrl("regist.htm")))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.FORGOT) {
            ForgotPasswordScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MAIN) {
            val toLogin: () -> Unit = {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.MAIN) { inclusive = true }
                }
            }
            MainScreen(
                onOpenWeb = { url -> navController.navigate(Routes.web(url)) },
                onLoggedOut = toLogin,
                // 首页「去登录」引导（v1.4.6）
                onNeedLogin = toLogin
            )
        }

        composable(
            route = Routes.WEB_PATTERN,
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { entry ->
            WebViewScreen(
                url = entry.arguments?.getString("url").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
