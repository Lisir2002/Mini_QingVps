package com.vps.qing.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 滚动感知玻璃（v1.4.11）：主页面列表滚动时通知玻璃顶栏/底栏把磨砂感收掉，
 * 只保留透明度让内容清晰穿过；停止滚动后磨砂感平滑恢复。
 *
 * 单例总线：任意列表（首页 / 选购 / 购物车 / 我的）任一在滚即置位，
 * 全部静止后复位；顶栏与底栏各自用动画值过渡，不同步闪烁。
 */
object GlassScrollBus {
    private val _scrolling = MutableStateFlow(false)
    val scrolling: StateFlow<Boolean> = _scrolling.asStateFlow()

    fun set(value: Boolean) {
        _scrolling.value = value
    }
}

/**
 * 把一个滚动容器的「滚动中」状态上报给 [GlassScrollBus]。
 * 支持 [LazyListState] 与 [ScrollState]；离开组合时复位，避免残留滚动感。
 */
@Composable
fun ScrollGlassEffect(state: LazyListState) {
    reportScroll(state) { it.isScrollInProgress }
}

@Composable
fun ScrollGlassEffect(state: ScrollState) {
    reportScroll(state) { it.isScrollInProgress }
}

@Composable
private fun <T> reportScroll(state: T, scrollingOf: (T) -> Boolean) {
    LaunchedEffect(state) {
        snapshotFlow { scrollingOf(state) }.collect { GlassScrollBus.set(it) }
    }
    DisposableEffect(state) {
        onDispose { GlassScrollBus.set(false) }
    }
}

/**
 * 玻璃栏当前透明度：静置 = [UiT.glassBar]（磨砂），滚动中 = [UiT.glassBarScrolling]（只留透明度）。
 * 顶栏 / 底栏共用；用 animateFloatAsState 平滑过渡，避免突变闪烁。
 */
@Composable
fun barGlassAlpha(): Float {
    val scrolling by GlassScrollBus.scrolling.collectAsState()
    val target = if (scrolling) UiT.glassBarScrolling else UiT.glassBar
    return animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 220),
        label = "barGlassAlpha"
    ).value
}
