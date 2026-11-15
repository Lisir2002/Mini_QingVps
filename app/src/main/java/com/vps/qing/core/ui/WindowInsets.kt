package com.vps.qing.core.ui

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * v1.4.19：标记「外层容器是否已经替内容让位过状态栏」。
 *
 * 顶栏上部空白过大的根因是**双重让位**：[com.vps.qing.feature.main.MainScreen]
 * 的 Scaffold 已通过 `padding.calculateTopPadding()` 让位状态栏，而个人中心子页
 * （权限管理 / 通知管理 / 安全中心编辑页）在 v1.4.18 又各自补了一次
 * `statusBarsPadding()`。Compose 的 `WindowInsets.statusBars` 不会因父级 padding
 * 自动减少，于是两处叠加 ≈ 55dp 空白，玻璃顶栏被推到状态栏下方一大截。
 *
 * 但顶层导航页（首次启动权限页、登录/注册/找回密码、WebView）不在 Scaffold 内，
 * 必须由页面自己避让状态栏，否则标题又会与状态栏时间/图标堆叠。
 *
 * 因此用一个 CompositionLocal 精确表达：MainScreen 的 Scaffold 内容 provide `true`，
 * 页面统一调用 [topInsetPadding] —— 外层已让位就跳过，否则才补 `statusBarsPadding()`。
 * 同一个页面（如权限管理页）无论作为顶层导航页还是个人中心子页，都能自动正确显示。
 */
val LocalTopInsetApplied = staticCompositionLocalOf { false }

/** 仅当外层尚未让位状态栏时才补 `statusBarsPadding()`，杜绝双重让位。 */
@Composable
fun Modifier.topInsetPadding(): Modifier =
    if (LocalTopInsetApplied.current) this else this.statusBarsPadding()
