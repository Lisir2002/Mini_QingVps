package com.vps.qing.feature.web

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.vps.qing.core.AppContainer
import com.vps.qing.core.network.CachePolicy
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.Strings
import okhttp3.Request
import java.io.ByteArrayInputStream

/**
 * 长尾功能兜底页。URL 由 AppContainer.webUrl() 生成，已带 ?queryParam=<jwt>，
 * 站点 request.js 会自动把 jwt 写入本地存储，H5 与原生共享同一登录态。
 *
 * v1.4.4 加载提速：
 *  - 站点 H5 有大量 Vue / Element UI 的 JS/CSS，站点对这些资源同样不返回缓存头；
 *    这里通过 shouldInterceptRequest 让静态资源走与 API 共用的 OkHttp 连接池 + 磁盘缓存，
 *    重复打开时直接从磁盘命中，不再重复下载。
 *  - 页面 HTML 仍走 LOAD_DEFAULT（带 token 且内容动态，不能缓存）。
 *  - 加载期间显示进度指示，消除白屏等待感。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(url: String, onBack: () -> Unit) {
    val decoded = remember(url) {
        if (url.startsWith("http")) url else Uri.decode(url)
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }

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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            // 页面 HTML 动态，用默认策略；静态资源由下面的拦截器单独缓存
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.mediaPlaybackRequiresUserGesture = true
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportZoom(false)
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): WebResourceResponse? = interceptStatic(request)
                            }
                            webView = this
                        }
                    },
                    update = { view ->
                        if (decoded.isNotBlank() && view.tag != decoded) {
                            view.tag = decoded
                            progress = 0
                            view.loadUrl(decoded)
                        }
                    }
                )
                if (progress in 1..99) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

/** 只接管同源静态资源；任何异常都返回 null 交回 WebView 自己加载，保证稳定性。 */
private fun interceptStatic(request: WebResourceRequest): WebResourceResponse? {
    // 主框架是 HTML 页面（动态且带 token），必须交回 WebView；只接管子资源
    if (!request.method.equals("GET", true) || request.isForMainFrame) return null
    val uri = request.url ?: return null
    if (uri.scheme != "https" && uri.scheme != "http") return null
    val siteHost = Uri.parse(AppContainer.SITE_ORIGIN).host
    if (uri.host != siteHost) return null
    val path = uri.encodedPath ?: return null
    if (!CachePolicy.isStaticAsset(path) || path.contains("/console/v1/")) return null

    return try {
        val call = AppContainer.webHttpClient.newCall(Request.Builder().url(uri.toString()).build())
        call.execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val mime = body.contentType()?.toString()?.substringBefore(";")?.trim()
                ?: guessMime(path)
            val bytes = body.bytes()
            // OkHttp 已透明解压 gzip，这里不要再传 Content-Encoding
            WebResourceResponse(mime, null, ByteArrayInputStream(bytes))
        }
    } catch (e: Exception) {
        null
    }
}

private fun guessMime(path: String): String = when {
    path.endsWith(".js", true) || path.endsWith(".map", true) -> "application/javascript"
    path.endsWith(".css", true) -> "text/css"
    path.endsWith(".png", true) -> "image/png"
    path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
    path.endsWith(".gif", true) -> "image/gif"
    path.endsWith(".webp", true) -> "image/webp"
    path.endsWith(".svg", true) -> "image/svg+xml"
    path.endsWith(".woff2", true) -> "font/woff2"
    path.endsWith(".woff", true) -> "font/woff"
    path.endsWith(".ico", true) -> "image/x-icon"
    else -> "application/octet-stream"
}
