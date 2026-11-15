package com.vps.qing.feature.promo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vps.qing.core.AppContainer
import com.vps.qing.core.simpleViewModelFactory
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonBackground
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.EmptyHint
import com.vps.qing.core.ui.GlassCard
import com.vps.qing.core.ui.GlassPullRefreshBox
import com.vps.qing.core.ui.GlassTopBar
import com.vps.qing.core.ui.LoadingSkeleton
import com.vps.qing.core.ui.Strings
import com.vps.qing.core.ui.topInsetPadding
import com.vps.qing.core.ui.UiT
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.data.repository.PromoApplyResult
import com.vps.qing.data.repository.PromoDiscovered

/**
 * 独立优惠码中心（v1.4.21）。
 * 发现并展示各专区公告里的优惠码，支持官方 promo_code/apply 试算校验，一键直达专区选购。
 */
@Composable
fun PromoCenterScreen(
    onBack: () -> Unit,
    /** 去某专区选购（可携带选中的码）。 */
    onGoZone: (fpgId: String, spgId: String, code: String) -> Unit = { _, _, _ -> }
) {
    val vm: PromoCenterViewModel = viewModel(
        factory = simpleViewModelFactory { PromoCenterViewModel(AppContainer.catalogRepository) }
    )
    val state by vm.ui.collectAsState()
    val clipboard = LocalClipboardManager.current

    CartoonBackground {
        Column(modifier = Modifier.fillMaxSize().topInsetPadding()) {
            GlassTopBar(
                title = Strings.PROMO_CENTER,
                subtitle = Strings.PROMO_SUBTITLE,
                navIcon = AppIcons.Back,
                onNavClick = onBack,
                actions = {
                    Text(
                        if (state.count > 0) Strings.PROMO_FOUND.format(state.count) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = CartoonColors.Brand,
                        fontWeight = FontWeight.Bold
                    )
                }
            )

            GlassPullRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.load(refresh = true) },
                modifier = Modifier.fillMaxSize(),
                showStrip = false,
                error = state.error
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = UiT.pagePad, vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { TipCard() }
                    item { ManualVerifyCard(state, vm) }
                    if (state.loading) {
                        item { LoadingSkeleton(modifier = Modifier.fillMaxWidth(), rows = 3) }
                    } else if (state.codes.isEmpty() && state.error == null) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                EmptyHint(icon = AppIcons.Gift, text = Strings.PROMO_EMPTY)
                            }
                        }
                    } else {
                        items(state.codes, key = { it.item.code }) { discovered ->
                            PromoCodeCard(discovered, vm, clipboard, onGoZone)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TipCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Info, null, tint = CartoonColors.Sky, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    Strings.PROMO_TIP_TITLE,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = CartoonColors.Ink
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                Strings.PROMO_TIP,
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.InkSub
            )
        }
    }
}

@Composable
private fun ManualVerifyCard(state: PromoCenterState, vm: PromoCenterViewModel) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            OutlinedTextField(
                value = state.input,
                onValueChange = { vm.onInput(it.uppercase()) },
                label = { Text(Strings.PROMO_INPUT_HINT) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(10.dp))
            CartoonButton(
                text = if (state.manualApplying) Strings.PROMO_VERIFYING else Strings.PROMO_VERIFY,
                onClick = { vm.verifyManual() },
                enabled = state.input.isNotBlank() && !state.manualApplying,
                icon = AppIcons.Search
            )
            state.manualResult?.let { ResultLine(it) }
        }
    }
}

@Composable
private fun PromoCodeCard(
    discovered: PromoDiscovered,
    vm: PromoCenterViewModel,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    onGoZone: (String, String, String) -> Unit
) {
    val item = discovered.item
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 折扣徽标 / 码
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CartoonColors.PeachSoft)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        item.discountText.ifBlank { "优惠码" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = CartoonColors.Coral
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CartoonColors.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (item.zoneName.isNotBlank()) {
                        Text(
                            "${Strings.PROMO_ZONE}：${item.zoneName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CartoonColors.InkSub,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    AppIcons.Tag,
                    contentDescription = Strings.PROMO_COPY,
                    tint = CartoonColors.Brand,
                    modifier = Modifier.size(22.dp).clickable {
                        clipboard.setText(AnnotatedString(item.code))
                    }
                )
            }
            if (item.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(item.note, style = MaterialTheme.typography.labelSmall, color = CartoonColors.InkSub)
            }
            discovered.applyResult?.let { ResultLine(it) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CartoonButton(
                    text = if (discovered.applying) Strings.PROMO_VERIFYING else Strings.PROMO_TRY,
                    onClick = { vm.verify(item) },
                    enabled = !discovered.applying,
                    containerColor = CartoonColors.Lilac,
                    modifier = Modifier.weight(1f)
                )
                CartoonButton(
                    text = Strings.PROMO_GO_SHOP,
                    onClick = { onGoZone(item.fpgId, item.spgId, item.code) },
                    icon = AppIcons.Shop,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ResultLine(result: PromoApplyResult) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (result.usable) {
            Icon(AppIcons.Check, null, tint = CartoonColors.Mint, modifier = Modifier.size(16.dp))
            Text(
                Strings.PROMO_DISCOUNT_OK.format(result.discount),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Mint
            )
        } else {
            Icon(AppIcons.Info, null, tint = CartoonColors.Coral, modifier = Modifier.size(16.dp))
            Text(
                "${Strings.PROMO_UNAVAILABLE}${if (result.msg.isNotBlank()) " · ${result.msg}" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = CartoonColors.Coral,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
