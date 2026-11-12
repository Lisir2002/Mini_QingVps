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
import com.vps.qing.feature.profile.ProfileScreen
import com.vps.qing.feature.web.WebViewScreen

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
                onSuccess = { autoLogin ->
                    if (autoLogin) {
                        navController.navigate(Routes.PROFILE) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                },
                onRegister = { navController.navigate(Routes.REGISTER) },
                onForgot = { navController.navigate(Routes.FORGOT) },
                onWebLogin = { navController.navigate(Routes.web(AppContainer.webUrl("login.htm"))) }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onSuccess = { autoLogin ->
                    if (autoLogin) {
                        navController.navigate(Routes.PROFILE) {
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

        composable(Routes.PROFILE) {
            ProfileScreen(
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.PROFILE) { inclusive = true }
                    }
                },
                onOpenWeb = { url -> navController.navigate(Routes.web(url)) }
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
