package com.vps.qing.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.data.repository.AccountInfo
import com.vps.qing.data.repository.CertInfo

/**
 * 账号在首页 / 个人中心「该怎么显示名字」（v1.4.12）。
 *
 * 规则（回答「未实名应该显示什么」）：
 *  - 未登录：显示应用名，不带实名徽标；
 *  - 已实名：显示站点返回的实名姓名，带绿色「已实名」徽标；
 *  - 未实名：绝不显示 / 编造实名姓名，改用账号用户名（无则邮箱、手机号脱敏），
 *    并带橙色「未实名」徽标，点击可直达实名认证；
 *  - 实名信息尚未拉取到：暂用账号用户名，不显示徽标（避免误判闪烁）。
 */
enum class CertBadge { NONE, CERTIFIED, UNCERTIFIED }

data class AccountDisplay(
    val name: String,
    val badge: CertBadge
)

fun accountDisplay(
    account: AccountInfo?,
    cert: CertInfo?,
    loggedIn: Boolean
): AccountDisplay {
    if (!loggedIn) return AccountDisplay(Strings.APP_NAME, CertBadge.NONE)

    val realName = cert?.person?.name?.takeIf { it.isNotBlank() }
    val certified = cert?.isCertified == true && realName != null
    // 账号自身标识：用户名 → 邮箱前缀 → 手机号脱敏 → 占位
    val accountLabel = account?.username?.takeIf { it.isNotBlank() }
        ?: account?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        ?: account?.phone?.takeIf { it.length >= 7 }?.let { "${it.take(3)}****${it.takeLast(4)}" }
        ?: Strings.UNKNOWN

    return when {
        certified -> AccountDisplay(realName!!, CertBadge.CERTIFIED)
        cert == null -> AccountDisplay(accountLabel, CertBadge.NONE)
        else -> AccountDisplay(accountLabel, CertBadge.UNCERTIFIED)
    }
}

/** 实名状态徽标：已实名绿色 / 未实名橙色可点击直达认证。 */
@Composable
fun CertBadgeChip(
    badge: CertBadge,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    if (badge == CertBadge.NONE) return
    val certified = badge == CertBadge.CERTIFIED
    val bg = if (certified) CartoonColors.MintSoft else CartoonColors.PeachSoft
    val fg = if (certified) CartoonColors.Mint else CartoonColors.Coral
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (certified) AppIcons.Check else AppIcons.Shield,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            if (certified) "已实名" else "未实名 · 去认证",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}
