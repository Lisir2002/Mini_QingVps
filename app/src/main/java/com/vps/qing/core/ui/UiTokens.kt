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
    val shadowButton = 3.dp  // 按钮统一柔和阴影（v1.4.12，消除生硬割裂投影）
    val shadowMd = 8.dp      // 卡片 / 顶栏 / 底栏
    val shadowLg = 12.dp     // 悬浮容器
    val shadowXl = 18.dp     // 弹窗

    // ---------- 玻璃透明度（v1.4.12 统一高磨砂玻璃）----------
    /**
     * 设计基准：所有卡片一律采用「磨砂高度模糊的透明玻璃」——静置时玻璃足够实，
     * 下层内容被雾化成柔和的彩色影子（而不是清晰文字），文字保持高可读；
     * 滚动时磨砂渐隐、只留透明度，让内容清晰穿过（见 [glassBarScrolling]）。
     * 玻璃层始终是半透明，不存在任何不透明画布。
     */
    const val glassCard = 0.86f      // 所有内容卡片统一材质
    const val glassFaint = 0.74f     // 侧边栏等大面积玻璃
    const val glass = 0.82f          // 常规玻璃容器
    const val glassStrong = glassCard // 卡片（与 glassCard 统一，不再分档）
    const val glassBar = 0.88f       // 顶栏/底栏静置：高磨砂，只透出下层模糊影子
    const val glassBarScrolling = 0.20f // 顶栏/底栏滚动中：磨砂渐隐，只留透明度
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
