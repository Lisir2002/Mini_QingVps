package com.vps.qing.feature.web

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.Strings

/**
 * 长尾功能兜底页。URL 由 AppContainer.webUrl() 生成，已带 ?queryParam=<jwt>，
 * 站点 request.js 会自动把 jwt 写入本地存储，H5 与原生共享同一登录态。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(url: String, onBack: () -> Unit) {
    val decoded = remember(url) {
        if (url.startsWith("http")) url else Uri.decode(url)
    }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val view = webView
        if (view?.canGoBack() == true) view.goBack() else onBack()
    }

    CartoonBackground {
        // Android 15(targetSdk 35) 边到边默认开启：顶栏要避开状态栏，WebView 避开导航条
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            GlassTopBar(
                title = Strings.WEB_ENTRY_HINT,
                subtitle = Strings.WEB_FALLBACK_TIP,
                navIcon = AppIcons.Back,
                onNavClick = onBack
            )
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .navigationBarsPadding(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = WebViewClient()
                        webView = this
                    }
                },
                update = { view ->
                    if (decoded.isNotBlank() && view.tag != decoded) {
                        view.tag = decoded
                        view.loadUrl(decoded)
                    }
                }
            )
        }
    }
}
