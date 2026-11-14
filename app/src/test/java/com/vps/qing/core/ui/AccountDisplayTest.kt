package com.vps.qing.core.ui

import com.vps.qing.data.repository.AccountInfo
import com.vps.qing.data.repository.CertInfo
import com.vps.qing.data.repository.CertRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.14：首页 / 个人中心名字显示规则。
 *
 * 核心约定：名字区**只允许显示脱敏邮箱**，绝不显示实名姓名——
 * 邮箱前 2 字符显示、中间星号隐藏、`@` 及域名完整显示；
 * 实名状态仅用徽标表达（绿=已实名 / 橙=未实名），点击引导到安全中心。
 */
class AccountDisplayTest {

    private fun cert(certified: Boolean, name: String?) = CertInfo(
        open = true,
        isCertified = certified,
        person = name?.let {
            CertRecord(
                name = it, cardName = "", cardNumber = "", status = "1",
                createTime = "", failReason = ""
            )
        },
        company = null
    )

    private val account = AccountInfo(
        id = "1", username = "liyou88", email = "180142181@qq.com", phone = "13800138000"
    )

    private val masked = "18******@qq.com"

    // ---- 邮箱脱敏纯函数 ----

    @Test
    fun `邮箱前两位显示中间星号at后完整显示`() {
        assertEquals(masked, maskEmail("180142181@qq.com"))
    }

    @Test
    fun `短 local part 也至少三个星号`() {
        assertEquals("a***@b.com", maskEmail("a@b.com"))
        assertEquals("ab****@example.com", maskEmail("abcdef@example.com"))
    }

    @Test
    fun `脱敏结果只暴露前两位与域名`() {
        val m = maskEmail("180142181@qq.com")
        assertTrue(m.startsWith("18"))
        assertTrue(m.endsWith("@qq.com"))
        val hidden = m.substringAfter("18").substringBefore("@")
        assertTrue(hidden.isNotEmpty() && hidden.all { it == '*' })
        assertFalse(m.contains("0142181"))
    }

    @Test
    fun `星号数量被限制在三到六个`() {
        listOf("a@b.com", "ab@c.com", "abc@d.com", "abcdefghijk@e.com", "180142181@qq.com")
            .forEach { email ->
                val m = maskEmail(email)
                val stars = m.substringBefore("@").count { it == '*' }
                assertTrue("$email 星号数 $stars 越界", stars in 3..6)
            }
    }

    @Test
    fun `空值与无at异常值不泄露完整串`() {
        assertEquals(Strings.UNKNOWN, maskEmail(null))
        assertEquals(Strings.UNKNOWN, maskEmail(""))
        assertEquals(Strings.UNKNOWN, maskEmail("   "))
        assertEquals("no***", maskEmail("noat"))
    }

    // ---- 名字区显示 ----

    @Test
    fun `未实名显示脱敏邮箱并标注未实名`() {
        val d = accountDisplay(account, cert(false, null), loggedIn = true)
        assertEquals(masked, d.name)
        assertEquals(CertBadge.UNCERTIFIED, d.badge)
    }

    @Test
    fun `已实名也绝不显示实名姓名只显示脱敏邮箱`() {
        val d = accountDisplay(account, cert(true, "李由"), loggedIn = true)
        assertEquals(masked, d.name)
        assertEquals(CertBadge.CERTIFIED, d.badge)
    }

    @Test
    fun `已实名但无姓名记录时徽标仍为已实名`() {
        val d = accountDisplay(account, cert(true, null), loggedIn = true)
        assertEquals(masked, d.name)
        assertEquals(CertBadge.CERTIFIED, d.badge)
    }

    @Test
    fun `实名信息未拉取到时不误标未实名`() {
        val d = accountDisplay(account, null, loggedIn = true)
        assertEquals(masked, d.name)
        assertEquals(CertBadge.NONE, d.badge)
    }

    @Test
    fun `未登录显示应用名且无徽标`() {
        val d = accountDisplay(null, null, loggedIn = false)
        assertEquals(Strings.APP_NAME, d.name)
        assertEquals(CertBadge.NONE, d.badge)
    }

    @Test
    fun `用户名为空时仍优先显示脱敏邮箱`() {
        val d = accountDisplay(account.copy(username = null), cert(false, null), loggedIn = true)
        assertEquals(masked, d.name)
    }

    @Test
    fun `无邮箱时回退脱敏手机号`() {
        val d = accountDisplay(
            account.copy(username = null, email = null), cert(false, null), loggedIn = true
        )
        assertEquals("138****8000", d.name)
    }

    @Test
    fun `无邮箱无手机时回退用户名`() {
        val d = accountDisplay(
            account.copy(email = null, phone = ""), cert(false, null), loggedIn = true
        )
        assertEquals("liyou88", d.name)
    }
}
