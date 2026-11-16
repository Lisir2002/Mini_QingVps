package com.vps.qing.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vps.qing.core.ui.theme.CartoonColors

/**
 * 通用选择组件（v1.4.23 提取自 ConfigSheet，供选购页与已购机器重装页共用，保证样式统一）。
 */

/** 胶囊流式选择行。value=选项 id，label=显示文案。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlowRow(
    options: List<Pair<String, String>>,
    selectedId: String?,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val selected = value == selectedId
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            selected -> CartoonColors.Brand
                            !enabled -> CartoonColors.White.copy(alpha = 0.45f)
                            else -> CartoonColors.White.copy(alpha = 0.7f)
                        }
                    )
                    .clickable(enabled = enabled) { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else CartoonColors.Ink,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 玻璃下拉选择：点击展开动画菜单，选中项高亮打勾。
 * items = [id, label]，selectedId 为当前 id。
 * v1.4.23：改用 AnimatedVisibility 展开/收起，视觉反馈更顺滑。
 */
@Composable
fun GlassDropdown(
    items: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = items.firstOrNull { it.first == selectedId }?.second
        ?: items.firstOrNull()?.second.orEmpty()

    Column(modifier) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(16.dp),
            alpha = UiT.glassCard,
            elevation = 2.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    AppIcons.ChevronDown, null,
                    tint = CartoonColors.Brand,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(4.dp))
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    alpha = 0.94f,
                    elevation = 8.dp
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp)
                    ) {
                        items.forEach { (id, label) ->
                            val selected = id == selectedId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) CartoonColors.BrandSoft else Color.Transparent)
                                    .clickable {
                                        onSelect(id)
                                        expanded = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) CartoonColors.Brand else CartoonColors.Ink,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(AppIcons.Check, null, tint = CartoonColors.Brand, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
