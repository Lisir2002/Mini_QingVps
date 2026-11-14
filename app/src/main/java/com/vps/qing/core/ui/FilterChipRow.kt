package com.vps.qing.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vps.qing.core.ui.theme.CartoonColors

/**
 * 统一分类筛选胶囊（v1.4.12）：订单记录 / 交易记录 / 工单中心共用。
 * 玻璃材质、横向滚动、尾部留白（滚到最右不贴边裁切）、选中态高亮。
 * id 为 null 表示「全部」。
 */
@Composable
fun FilterChipRow(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (id, label) ->
            val active = selected == id
            Box(
                Modifier
                    .clip(CartoonKit.PillShape)
                    .background(if (active) CartoonColors.Brand else CartoonColors.White.copy(alpha = 0.55f))
                    .then(
                        if (!active) Modifier.border(
                            UiT.strokeWidthHair, UiT.borderColor, CartoonKit.PillShape
                        ) else Modifier
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color.White else CartoonColors.Ink
                )
            }
        }
        Spacer(Modifier.width(6.dp))
    }
}
