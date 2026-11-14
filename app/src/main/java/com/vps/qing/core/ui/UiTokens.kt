package com.vps.qing.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * QingVPS UI 令牌（v1.2.1 起）。
 *
 * 全应用的圆角 / 阴影 / 透明度 / 边框 / 间距 / 控件尺寸一律引用这里的令牌，
 * 禁止在页面里散落写魔法数字——这是「统一化管理 UI 各项参数」的唯一事实来源。
 * 颜色令牌在 [com.vps.qing.core.ui.theme.CartoonColors]，这里只管形状与量值。
 */
object UiT {

    // ---------- 圆角 ----------
    val radiusXs = 8.dp      // 小徽章、圆点
    val radiusSm = 12.dp     // 列表内嵌块、stepper
    val radiusMd = 16.dp     // 侧边栏条目、内嵌卡片
    val radiusLg = 20.dp     // 商品卡
    val radiusXl = 24.dp     // 大卡片 / 玻璃容器
    val radiusSheet = 30.dp  // 弹窗

    // ---------- 阴影（elevation） ----------
    val shadowNone = 0.dp
    val shadowSm = 4.dp      // 按钮、小控件
    val shadowMd = 8.dp      // 卡片
    val shadowLg = 12.dp     // 顶栏 / 悬浮容器
    val shadowXl = 18.dp     // 弹窗

    // ---------- 玻璃透明度（v1.4.9 深度透明化：组件半透明以透出糖果背景）----------
    const val glassFaint = 0.42f     // 侧边栏等大面积玻璃
    const val glass = 0.54f          // 常规玻璃容器
    const val glassStrong = 0.64f    // 卡片（内容区更实，便于阅读）
    const val glassBar = 0.46f       // 顶栏/底栏：透明画布，透出背景糖果光斑
    const val glassBarScrolling = 0.14f // 顶栏/底栏滚动中：磨砂感消失只留透明度（v1.4.11）
    const val glassOverlay = 0.92f   // toast / 遮罩层

    // ---------- 边框 ----------
    val strokeWidthHair = 1.dp
    val strokeWidth = 1.4.dp
    /** 清晰的卡片边界：比纯白描边更易分辨卡片轮廓。 */
    val borderColor = Color(0xFFD8DEF0)

    // ---------- 间距 ----------
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val spaceLg = 16.dp
    val spaceXl = 20.dp
    /** 页面左右统一留白。 */
    val pagePad = 14.dp

    // ---------- 控件尺寸 ----------
    val chip = 36.dp           // 圆形图标按钮
    val chipIcon = 18.dp
    /** 按钮/操作位图标统一尺寸：全应用所有按钮图标（含购物车）必须引用同一令牌，避免一大一小。 */
    val buttonIcon = 18.dp
    val control = 44.dp        // 行内按钮 / 步进器行高
    val button = 46.dp         // 主按钮高度（紧凑版，原 52）
    val iconOnlyButton = 44.dp // 图标按钮直径
    val sidebarExpandedWidth = 176.dp
    val sidebarCollapsedWidth = 64.dp
    val cardPad = spaceLg      // 卡片内边距

    /** Dp 快捷组合：横向页面留白。 */
    val pageHorizontal: Dp get() = pagePad
}
