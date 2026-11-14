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
 * 账号在首页 / 个人中心「该怎么显示名字」（v1.4.14）。
 *
 * 隐私规则（v1.4.14 新）：名字区**只允许显示脱敏邮箱**，绝不显示实名姓名——
 *  - 邮箱前 2 个字符显示，中间用星号隐藏，`@` 及之后（域名）完整显示；
 *    例如 `180142181@qq.com` → `18******@qq.com`；
 *  - 实名状态仍用徽标表达（绿=已实名 / 橙=未实名），点击引导到「安全中心」；
 *  - 未登录：显示应用名，不带徽标；
 *  - 实名信息尚未拉取到：只显示脱敏邮箱，不显示徽标（避免误判闪烁）。
 *  - 账号无邮箱（异常态）时回退：手机号脱敏 → 用户名 → 占位。
 */
enum class CertBadge { NONE, CERTIFIED, UNCERTIFIED }

data class AccountDisplay(
    val name: String,
    val badge: CertBadge
)

/** 邮箱脱敏：前 2 字符 + 星号 + `@域名`（v1.4.14）。 */
fun maskEmail(email: String?): String {
    if (email.isNullOrBlank()) return Strings.UNKNOWN
    val at = email.indexOf('@')
    if (at < 0) {
        // 无 @ 的异常值：只保留前 2 位，其余星号（不泄露完整串）
        val head = email.take(2)
        return head + "*".repeat((email.length - head.length).coerceAtLeast(3).coerceAtMost(6))
    }
    val local = email.substring(0, at)
    val domain = email.substring(at) // 含 "@" 与域名，@后完整显示
    val head = local.take(2)
    // 中间长度按被隐藏的字符数，至少 3 个星号、最多 6 个，既不泄露长度也够清晰
    val stars = "*".repeat((local.length - head.length).coerceAtLeast(3).coerceAtMost(6))
    return head + stars + domain
}

/** 账号标识（名字区）一律优先用脱敏邮箱，绝不暴露实名姓名。 */
fun accountDisplay(
    account: AccountInfo?,
    cert: CertInfo?,
    loggedIn: Boolean
): AccountDisplay {
    if (!loggedIn) return AccountDisplay(Strings.APP_NAME, CertBadge.NONE)

    val emailLabel = account?.email?.takeIf { it.isNotBlank() }?.let { maskEmail(it) }
    val phoneLabel = account?.phone?.takeIf { it.length >= 7 }?.let { "${it.take(3)}****${it.takeLast(4)}" }
    val name = emailLabel
        ?: phoneLabel
        ?: account?.username?.takeIf { it.isNotBlank() }
        ?: Strings.UNKNOWN

    val badge = when {
        cert == null -> CertBadge.NONE
        cert.isCertified -> CertBadge.CERTIFIED
        else -> CertBadge.UNCERTIFIED
    }
    return AccountDisplay(name, badge)
}

/** 实名状态徽标：已实名绿色 / 未实名橙色可点击直达认证（安全中心）。 */
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
