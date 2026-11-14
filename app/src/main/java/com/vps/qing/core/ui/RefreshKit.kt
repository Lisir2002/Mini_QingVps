package com.vps.qing.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vps.qing.core.ui.theme.CartoonColors
import kotlinx.coroutines.delay

/**
 * 全局统一的下拉刷新 / 等待反馈组件库（v1.4.15）。
 *
 * - [GlassPullRefreshBox]：磨砂玻璃下拉指示器，三态文案（下拉刷新 / 松开立即刷新 /
 *   正在获取最新数据…）+ 下拉进度淡入放大 + 刷新完成后「已更新」轻反馈，替代各页裸
 *   [PullToRefreshBox] 与零散刷新按钮，交互反馈全局一致；
 * - [LoadingSkeleton]：刷新 / 首屏取数时的骨架等待样式（磨砂玻璃卡 + shimmer 呼吸），
 *   替代生硬的 LinearProgressIndicator / 干转圈；
 * - [GlassRefreshChip]：顶栏刷新按钮的旋转反馈，刷新中不可重复点击并持续旋转。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassPullRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state: PullToRefreshState = rememberPullToRefreshState()

    // 刷新完成（true → false）后弹出 1.1s「已更新」轻反馈，给用户明确的动作结果
    var wasRefreshing by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) {
            showDone = true
            delay(1100)
            showDone = false
        }
        wasRefreshing = isRefreshing
    }

    Box(modifier = modifier) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = state,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                GlassRefreshIndicator(
                    isRefreshing = isRefreshing,
                    fraction = state.distanceFraction,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            content = content
        )

        // 「已更新」轻反馈：浮在内容上方，不打断列表
        AnimatedVisibility(
            visible = showDone,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            GlassSurface(
                shape = CartoonKit.PillShape,
                alpha = UiT.glassOverlay,
                elevation = UiT.shadowMd
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = UiT.spaceLg, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        AppIcons.Check, null,
                        tint = CartoonColors.Mint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Strings.REFRESH_DONE,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = CartoonColors.Ink
                    )
                }
            }
        }
    }
}

/** 磨砂玻璃下拉指示器：随下拉进度淡入放大、文案切三态、刷新中持续旋转。 */
@Composable
private fun GlassRefreshIndicator(
    isRefreshing: Boolean,
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val progress = fraction.coerceIn(0f, 1f)
    val label = when {
        isRefreshing -> Strings.REFRESHING
        progress >= 1f -> Strings.RELEASE_TO_REFRESH
        else -> Strings.PULL_TO_REFRESH
    }
    val spin by rememberInfiniteTransition(label = "refreshSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(950, easing = LinearEasing)),
        label = "spin"
    )
    Box(
        modifier = modifier
            .padding(top = 8.dp)
            .graphicsLayer {
                val reveal = if (isRefreshing) 1f else progress
                alpha = reveal
                val s = 0.82f + 0.18f * reveal
                scaleX = s
                scaleY = s
            }
    ) {
        GlassSurface(
            shape = CartoonKit.PillShape,
            alpha = UiT.glassOverlay,
            elevation = UiT.shadowMd
        ) {
            Row(
                modifier = Modifier.padding(horizontal = UiT.spaceLg, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = CartoonColors.Brand
                    )
                } else {
                    Icon(
                        AppIcons.Refresh, null,
                        tint = CartoonColors.Brand,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = progress * 180f }
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink
                )
            }
        }
    }
}

/**
 * 磨砂玻璃骨架等待态：刷新 / 首屏取数时占位，带 shimmer 呼吸动画，
 * 替代生硬的横向进度条 / 干转圈，让「等待」也有与全局一致的玻璃质感。
 */
@Composable
fun LoadingSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 3,
    message: String = Strings.LOADING_DATA
) {
    val shimmer by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            tween(1050, easing = LinearEasing), RepeatMode.Reverse
        ),
        label = "shimmer"
    )
    Column(
        modifier = modifier.padding(horizontal = UiT.pagePad, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = CartoonColors.Brand
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
        }
        repeat(rows) { idx ->
            GlassSurface(
                shape = CartoonKit.CardShape,
                alpha = UiT.glassCard,
                elevation = UiT.shadowSm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiT.cardPad),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SkeletonBar(0.55f + idx * 0.05f, 14.dp, shimmer)
                    SkeletonBar(0.86f, 10.dp, shimmer * 0.8f)
                    SkeletonBar(0.42f, 10.dp, shimmer * 0.8f)
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(fraction: Float, height: androidx.compose.ui.unit.Dp, alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(CartoonKit.PillShape)
            .background(CartoonColors.InkSub.copy(alpha = alpha))
    )
}

/**
 * 内联加载提示条（v1.4.15）：替换各页零散的 LinearProgressIndicator，
 * 用与全局一致的磨砂玻璃胶囊 + 小转圈 + 文案，明确告诉用户「正在取数」而非干等。
 * 不强制 fillMaxWidth，调用方可用 modifier 控制占位。
 */
@Composable
fun GlassLoadingBar(
    modifier: Modifier = Modifier,
    message: String = Strings.LOADING_DATA
) {
    GlassSurface(
        shape = CartoonKit.PillShape,
        alpha = UiT.glassCard,
        elevation = UiT.shadowSm,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = UiT.spaceLg, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = CartoonColors.Brand
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub,
                maxLines = 1
            )
        }
    }
}

/**
 * 顶栏刷新按钮：刷新中持续旋转并禁用重复点击，给「点击刷新」明确的回应式反馈。
 * 静置时与全局 [CartoonIconChip] 观感一致（彩色圆底 + 图标）。
 */
@Composable
fun GlassRefreshChip(
    refreshing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CartoonColors.Mint,
    bg: Color = CartoonColors.MintSoft
) {
    val spin by rememberInfiniteTransition(label = "refreshChip").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(950, easing = LinearEasing)),
        label = "chipSpin"
    )
    Box(
        modifier = modifier
            .size(UiT.chip)
            .clip(CartoonKit.PillShape)
            .background(if (refreshing) bg.copy(alpha = 0.7f) else bg)
            .clickable(enabled = !refreshing) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            AppIcons.Refresh, null,
            tint = tint,
            modifier = Modifier
                .size(UiT.chipIcon)
                .graphicsLayer { if (refreshing) rotationZ = spin }
        )
    }
}
