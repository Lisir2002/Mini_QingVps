package com.vps.qing.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vps.qing.core.ui.theme.CartoonColors

/**
 * 纯白卡通 + 磨砂玻璃 的公共组件库（QingVPS v1.2.1）。
 *
 * 磨砂玻璃用「半透明白渐变 + 描边 + 柔和彩色阴影」模拟：不依赖 Android 12+
 * 的 RenderEffect.blur，minSdk 26 起所有设备观感一致。底层 [CartoonBackground]
 * 铺几团柔和糖果色光斑，玻璃层透出彩色即形成磨砂质感。
 *
 * v1.2.1：所有圆角 / 阴影 / 透明度 / 边框 / 尺寸统一引用 [UiT] 令牌；
 * 卡片边界改用 [UiT.borderColor] 描边，轮廓更清晰。
 */
object CartoonKit {
    val CardShape = RoundedCornerShape(UiT.radiusXl)
    val PillShape = RoundedCornerShape(50)
    val GlassShape = RoundedCornerShape(UiT.radiusXl)
    val SheetShape = RoundedCornerShape(UiT.radiusSheet)
    val ItemShape = RoundedCornerShape(UiT.radiusMd)
}

/** 全局卡通背景：纯白底 + 几团半透明糖果色光斑。所有页面都叠在它上面。 */
@Composable
fun CartoonBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxSize()
            .background(CartoonColors.Background)
    ) {
        // 光斑用大尺寸半透明圆 + 径向渐变，画布不拦截点击
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CartoonColors.Mint.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(0.92f, 0.06f),
                        radius = 900f
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CartoonColors.Sky.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(0.05f, 0.42f),
                        radius = 800f
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CartoonColors.Peach.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(0.88f, 0.86f),
                        radius = 820f
                    )
                )
        )
        content()
    }
}

/**
 * 磨砂玻璃 Modifier：半透明白渐变 + 描边 + 柔和阴影。
 * 边界用 [UiT.borderColor]（灰蓝细线）保证卡片轮廓清晰。
 */
fun Modifier.glass(
    shape: Shape = CartoonKit.GlassShape,
    alpha: Float = UiT.glass,
    elevation: Dp = UiT.shadowMd,
    border: Boolean = true
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = CartoonColors.GlassShadow,
        spotColor = CartoonColors.GlassShadow
    )
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                CartoonColors.GlassTint.copy(alpha = alpha),
                CartoonColors.GlassTint.copy(alpha = (alpha - 0.18f).coerceAtLeast(0.18f))
            )
        )
    )
    .then(
        if (border) Modifier.border(UiT.strokeWidth, UiT.borderColor, shape)
        else Modifier
    )

/** 磨砂玻璃容器。 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CartoonKit.GlassShape,
    alpha: Float = UiT.glass,
    elevation: Dp = UiT.shadowMd,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.glass(shape = shape, alpha = alpha, elevation = elevation),
        content = content
    )
}

/**
 * 磨砂玻璃顶栏。自带标题 + 可选左侧图标 + 右侧动作槽。
 * 用自定义布局而非 material3 TopAppBar，避免实验性 API 且完全掌控玻璃外观。
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navIcon: ImageVector? = null,
    onNavClick: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = UiT.pagePad, vertical = 10.dp),
        shape = CartoonKit.GlassShape,
        alpha = UiT.glassBar,
        elevation = UiT.shadowMd
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiT.spaceMd, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navIcon != null && onNavClick != null) {
                IconButton(onClick = onNavClick, modifier = Modifier.size(40.dp)) {
                    Icon(navIcon, contentDescription = null, tint = CartoonColors.Brand)
                }
                Spacer(Modifier.width(UiT.spaceXs))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = CartoonColors.Ink
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.InkSub
                    )
                }
            }
            actions()
        }
    }
}

/** 磨砂玻璃卡片（内容区白得比玻璃容器更实一点，便于阅读）。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    alpha: Float = UiT.glassStrong,
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = CartoonKit.CardShape,
        alpha = alpha,
        elevation = UiT.shadowMd,
        content = content
    )
}

/** 卡通主按钮（胶囊、大圆角、柔和阴影）。文字单行不换行，空间不足时缩尾。 */
@Composable
fun CartoonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color = CartoonColors.Brand,
    /** 文本右侧的自定义内容（如加载指示器）；传入时与文本之间自动留间距。 */
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(UiT.button),
        shape = CartoonKit.PillShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = UiT.spaceLg, vertical = UiT.spaceXs
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = containerColor.copy(alpha = 0.4f),
            disabledContentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = UiT.shadowSm)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(UiT.buttonIcon))
            Spacer(Modifier.width(UiT.spaceSm))
        }
        Text(
            text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
        if (trailingContent != null) {
            Spacer(Modifier.width(UiT.spaceSm))
            trailingContent()
        }
    }
}

/** 图标圆按钮：加购等动作在紧凑布局下只留图标也绝不空白。 */
@Composable
fun CartoonRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = CartoonColors.Brand,
    iconTint: Color = Color.White,
    size: Dp = UiT.iconOnlyButton
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CartoonKit.PillShape)
            .background(containerColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(UiT.buttonIcon))
    }
}

/** 卡通图标按钮（带彩色圆底）。 */
@Composable
fun CartoonIconChip(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CartoonColors.Brand,
    bg: Color = CartoonColors.BrandSoft
) {
    Box(
        modifier = modifier
            .size(UiT.chip)
            .clip(CartoonKit.PillShape)
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(UiT.chipIcon))
    }
}

/** 章节标题：带一个彩色小圆点。 */
@Composable
fun SectionTitle(text: String, accent: Color = CartoonColors.Brand, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = UiT.spaceXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CartoonKit.PillShape)
                .background(accent)
        )
        Spacer(Modifier.width(UiT.spaceSm))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = CartoonColors.Ink
        )
    }
}

/** 空状态占位：大图标 + 文案。 */
@Composable
fun EmptyHint(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CartoonKit.CardShape)
                .background(CartoonColors.BrandSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = CartoonColors.Brand, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = CartoonColors.InkSub)
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

/** 数量步进器（加减按钮 + 数字），购物车与配置弹窗复用。 */
@Composable
fun QuantityStepper(
    value: Int,
    onDelta: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 99
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CartoonIconChip(
            icon = AppIcons.Minus,
            onClick = { if (value > min) onDelta(-1) },
            tint = CartoonColors.Brand,
            bg = CartoonColors.BrandSoft
        )
        // v1.4.7：数字固定宽度并水平居中，个位/两位数都不偏移
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(min = 44.dp),
            color = CartoonColors.Ink
        )
        CartoonIconChip(
            icon = AppIcons.Plus,
            onClick = { if (value < max) onDelta(1) },
            tint = CartoonColors.Brand,
            bg = CartoonColors.BrandSoft
        )
    }
}
