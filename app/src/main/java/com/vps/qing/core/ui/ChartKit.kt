package com.vps.qing.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vps.qing.core.ui.theme.CartoonColors

/**
 * 轻量自绘统计图表（v1.4.23）：不引第三方图表库，用 Canvas 画折线 + 面积渐变。
 * 数据来自魔方 chart 接口，见 docs/mofang-v10-host-ops-research.md。
 */

/** 一条曲线：名称、数值、颜色。 */
data class ChartSeries(val name: String, val values: List<Float>, val color: Color)

/**
 * 多序列折线图。所有序列等长（点数一致），缺失点由调用方补 0。
 */
@Composable
fun LineChartCard(
    title: String,
    series: List<ChartSeries>,
    unit: String = "",
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CartoonColors.Ink)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(s.color))
                        Spacer(Modifier.width(5.dp))
                        val last = s.values.lastOrNull() ?: 0f
                        val legend = s.name + " " + formatNum(last) + unit
                        Text(legend, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (series.isEmpty() || series.all { it.values.isEmpty() }) {
                Text(
                    Strings.HOST_EMPTY_DATA,
                    style = MaterialTheme.typography.labelSmall,
                    color = CartoonColors.InkSub,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                val maxV = run {
                    val peak = series.flatMap { it.values }.maxOrNull() ?: 0f
                    if (peak <= 0f) 1f else peak * 1.15f
                }
                val pointCount = series.maxOf { it.values.size }
                Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    val w = size.width
                    val h = size.height
                    val gridColor = CartoonColors.InkSub.copy(alpha = 0.18f)
                    for (i in 0..4) {
                        val y = h * i / 4f
                        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                    }
                    if (pointCount > 1) {
                        val stepX = w / (pointCount - 1).toFloat()
                        series.forEach { s ->
                            if (s.values.size < 2) return@forEach
                            val pts = s.values.mapIndexed { idx, v ->
                                Offset(idx * stepX, h - (v.coerceAtMost(maxV) / maxV) * h)
                            }
                            val area = Path().apply {
                                moveTo(pts.first().x, h)
                                pts.forEach { lineTo(it.x, it.y) }
                                lineTo(pts.last().x, h)
                                close()
                            }
                            drawPath(
                                area,
                                brush = Brush.verticalGradient(
                                    colors = listOf(s.color.copy(alpha = 0.35f), s.color.copy(alpha = 0.02f)),
                                    startY = 0f,
                                    endY = h
                                )
                            )
                            val line = Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                pts.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(line, color = s.color, style = Stroke(width = 2.5f))
                        }
                    }
                }
            }
        }
    }
}

private fun formatNum(v: Float): String = when {
    v >= 100f -> v.toInt().toString()
    v >= 10f -> String.format("%.1f", v)
    else -> String.format("%.2f", v)
}
