package com.vps.qing.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * v1.4.17：刷新结果提示（成功 / 失败）显示时长 3.5 秒，之后自动隐藏并等待下一次唤醒。
 * 落在用户要求的 3–5 秒区间内，作为可调令牌供测试与后续统一调整。
 */
const val REFRESH_DONE_VISIBLE_MS = 3500L

/**
 * 全局统一的下拉刷新 / 等待反馈组件库（v1.4.16 重写）。
 *
 * v1.4.16 两处关键改动：
 *  1) **位置统一**：指示器不再用 PullToRefreshBox 的浮层 indicator（它在首页落在顶栏
 *     下层、在购物车浮在内容区卡片上，各页不一致）。改为 [GlassRefreshStrip]：一条固定
 *     占据「顶栏正下方一行」的刷新条，所有页面的下拉交互都从同一位置展开/收起。
 *  2) **彻底修复卡住**：此前把 ViewModel 的 isRefreshing 直接交给 PullToRefreshBox，
 *     一旦该状态因缓存/去重/快速返回没有完整经历 true→false，手势 state 就停在阈值，
 *     指示器永远显示「松开立即刷新」。现在由 [GlassRefreshController] 自管刷新生命周期：
 *     onRefresh 触发即本地置 refreshing，并有「等待外部结束 + 最长 4s 兜底」保证一定复位，
 *     手势 state 必然回到 hidden，绝不卡住。
 *
 * 用法（所有页面一致）：
 * ```
 * val refresh = rememberGlassRefresh(state.loading) { vm.load(force = true) }
 * Column {
 *     GlassTopBar(...)                         // 顶栏
 *     GlassPullRefreshBox(refresh, Modifier.weight(1f)) { content }  // 内含 strip = 顶栏下一行
 * }
 * ```
 * 悬浮顶栏页面（首页）用 `showStrip = false`，再把 [GlassRefreshStrip] 放进悬浮顶栏 Column。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class GlassRefreshController internal constructor(
    val pullState: PullToRefreshState
) {
    /** 供 PullToRefreshBox 使用的刷新态：由本控制器保证完整 true→false 周期。 */
    var internalRefreshing by mutableStateOf(false)
        internal set

    var showDone by mutableStateOf(false)
        internal set

    /** v1.4.17：结果反馈文案与成败（成功「已刷新」/ 失败「刷新失败，请稍后重试」）。 */
    var doneText by mutableStateOf(Strings.REFRESH_DONE)
        internal set
    var doneOk by mutableStateOf(true)
        internal set

    /**
     * v1.4.17：每次刷新自增的序号。刷新生命周期的 LaunchedEffect 以它为 key，
     * 在一次刷新过程中恒定 —— 写 internalRefreshing=false 不会重启/取消自身协程。
     * 旧实现以 internalRefreshing 为 key，写 false 时协程被取消，导致
     * `showDone=false` 永远执行不到，「已刷新」一直挂在顶栏下方。
     */
    var refreshSeq by mutableStateOf(0)
        internal set

    /** 组合期由 rememberGlassRefresh 更新为最新的外部刷新回调。 */
    var request: () -> Unit = {}

    private fun begin() {
        showDone = false
        internalRefreshing = true
        refreshSeq++
    }

    /** 松手达到阈值时由 PullToRefreshBox 调用：立刻进入刷新态并触发业务刷新。 */
    fun trigger() {
        if (internalRefreshing) return
        begin()
        request()
    }

    /** 外部（顶栏刷新按钮）自行进入刷新态：纳入同一生命周期，但不重复发请求。 */
    fun beginExternal() {
        if (internalRefreshing) return
        begin()
    }

    /** 是否显示刷新条：下拉中 / 刷新中 / 完成反馈。空闲完全收起，不占空间。 */
    val visible: Boolean
        get() = internalRefreshing || showDone || pullState.distanceFraction > 0.001f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberGlassRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    /** v1.4.17：页面错误态。刷新结束时非空 → 提示「刷新失败」，成功/失败都 3.5s 自动隐藏。 */
    error: String? = null
): GlassRefreshController {
    val pullState = rememberPullToRefreshState()
    val controller = remember { GlassRefreshController(pullState) }
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentRefreshing by rememberUpdatedState(isRefreshing)
    val currentError by rememberUpdatedState(error)
    controller.request = currentOnRefresh

    // 外部状态若自行进入刷新（例如顶栏刷新按钮），也纳入内部生命周期，
    // 但绝不依赖它来结束——结束一律由下面的超时/等待逻辑兜底。
    LaunchedEffect(isRefreshing) {
        if (isRefreshing && !controller.internalRefreshing) controller.beginExternal()
    }

    // v1.4.17：key 用「刷新序号」而不是 internalRefreshing。
    // 一次刷新内序号恒定，写 internalRefreshing=false 不会取消本协程，
    // 于是复位 → 结果反馈 → 自动隐藏这条链路一定能走完。
    // 传给 PullToRefreshBox 的 internalRefreshing 仍必然经历 true→false，
    // 手势 state 一定 animateToHidden，根除「松开立即刷新」卡住。
    LaunchedEffect(controller.refreshSeq) {
        if (controller.refreshSeq == 0) return@LaunchedEffect
        withTimeoutOrNull(4000L) {
            var waited = 0L
            // 给业务至少 320ms 的反应时间，再等它把外部 loading 落回 false
            delay(320L)
            while (currentRefreshing && waited < 3600L) {
                delay(80L)
                waited += 80L
            }
        }
        controller.internalRefreshing = false
        val failed = !currentError.isNullOrBlank()
        controller.doneOk = !failed
        controller.doneText = if (failed) Strings.REFRESH_FAILED else Strings.REFRESH_DONE
        controller.showDone = true
    }

    // v1.4.17：结果提示 3.5 秒后自动隐藏并等待下一次唤醒。
    // 独立的计时器 effect（key=showDone），即便期间发生重组 / 新刷新也不会失效：
    // 新一轮 trigger() 会先把 showDone 置 false 取消旧计时，再重新显示反馈。
    LaunchedEffect(controller.showDone) {
        if (controller.showDone) {
            delay(REFRESH_DONE_VISIBLE_MS)
            controller.showDone = false
        }
    }

    return controller
}

/**
 * 统一位置的下拉刷新容器：内部第一行是 [GlassRefreshStrip]（顶栏下一行），
 * 第二行是真正的下拉手势区。indicator 置空，视觉全部交给 strip。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassPullRefreshBox(
    refresh: GlassRefreshController,
    modifier: Modifier = Modifier,
    showStrip: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Column(modifier = modifier) {
        if (showStrip) {
            GlassRefreshStrip(refresh, Modifier.fillMaxWidth())
        }
        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = refresh.internalRefreshing,
                onRefresh = { refresh.trigger() },
                state = refresh.pullState,
                indicator = {},
                modifier = Modifier.fillMaxSize(),
                content = content
            )
        }
    }
}

/**
 * v1.4.16 兼容入口：保留各页面既有的 `isRefreshing / onRefresh` 调用形态，
 * 内部统一创建 [GlassRefreshController]，让所有页面自动获得「顶栏下一行的刷新条」
 * 与「必然复位、不会卡在松开立即刷新」的刷新生命周期，无需逐页改造。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassPullRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    showStrip: Boolean = true,
    /** v1.4.17：页面错误态，供刷新失败反馈使用；不传则一律按成功提示。 */
    error: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val refresh = rememberGlassRefresh(isRefreshing, onRefresh, error)
    GlassPullRefreshBox(
        refresh = refresh,
        modifier = modifier,
        showStrip = showStrip,
        content = content
    )
}

/**
 * 固定在「顶栏下一行」的磨砂刷新条。空闲高度 0，交互时平滑展开到固定行高，
 * 所有页面起点一致；文案三态 + 刷新中转圈 + 完成打勾。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassRefreshStrip(
    refresh: GlassRefreshController,
    modifier: Modifier = Modifier
) {
    val visible = refresh.visible
    val height by animateDpAsState(
        targetValue = if (visible) UiT.refreshStripHeight else 0.dp,
        animationSpec = tween(180),
        label = "stripHeight"
    )
    Box(
        modifier = modifier
            .height(height)
            .padding(horizontal = UiT.pagePad),
        contentAlignment = Alignment.Center
    ) {
        if (visible) {
            val fraction = refresh.pullState.distanceFraction.coerceIn(0f, 1f)
            val label = when {
                refresh.showDone -> refresh.doneText
                refresh.internalRefreshing -> Strings.REFRESHING
                fraction >= 1f -> Strings.RELEASE_TO_REFRESH
                else -> Strings.PULL_TO_REFRESH
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CartoonKit.PillShape)
                    .glass(shape = CartoonKit.PillShape, alpha = UiT.glassCard, elevation = UiT.shadowSm)
                    .padding(horizontal = UiT.spaceLg, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (refresh.showDone) {
                    Icon(
                        if (refresh.doneOk) AppIcons.Check else AppIcons.Close, null,
                        tint = if (refresh.doneOk) CartoonColors.Mint else CartoonColors.Peach,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (refresh.internalRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = CartoonColors.Brand
                    )
                } else {
                    Icon(
                        AppIcons.Refresh, null,
                        tint = CartoonColors.Brand,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = fraction * 180f }
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink,
                    maxLines = 1
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
 * 内联加载提示条：替换各页零散的 LinearProgressIndicator，
 * 用与全局一致的磨砂玻璃胶囊 + 小转圈 + 文案，明确告诉用户「正在取数」。
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
