package com.vps.qing.core.ui

import com.vps.qing.data.repository.AccountInfo
import com.vps.qing.data.repository.CertInfo
import com.vps.qing.data.repository.CertRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.4.12：首页 / 个人中心名字显示规则。
 * 核心约定：未实名绝不冒充实名姓名，只显示账号标识并标注「未实名」。
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

    @Test
    fun `未实名显示账号用户名并标注未实名`() {
        val d = accountDisplay(account, cert(false, null), loggedIn = true)
        assertEquals("liyou88", d.name)
        assertEquals(CertBadge.UNCERTIFIED, d.badge)
    }

    @Test
    fun `已实名显示站点实名姓名`() {
        val d = accountDisplay(account, cert(true, "李由"), loggedIn = true)
        assertEquals("李由", d.name)
        assertEquals(CertBadge.CERTIFIED, d.badge)
    }

    @Test
    fun `isCertified 为真但无实名姓名时不冒充实名`() {
        val d = accountDisplay(account, cert(true, null), loggedIn = true)
        assertEquals("liyou88", d.name)
        assertEquals(CertBadge.UNCERTIFIED, d.badge)
    }

    @Test
    fun `实名信息未拉取到时不误标未实名`() {
        val d = accountDisplay(account, null, loggedIn = true)
        assertEquals("liyou88", d.name)
        assertEquals(CertBadge.NONE, d.badge)
    }

    @Test
    fun `未登录显示应用名且无徽标`() {
        val d = accountDisplay(null, null, loggedIn = false)
        assertEquals(Strings.APP_NAME, d.name)
        assertEquals(CertBadge.NONE, d.badge)
    }

    @Test
    fun `用户名为空时回退邮箱前缀`() {
        val d = accountDisplay(account.copy(username = null), cert(false, null), loggedIn = true)
        assertEquals("180142181", d.name)
    }

    @Test
    fun `仅有手机号时脱敏显示`() {
        val d = accountDisplay(
            account.copy(username = null, email = null), cert(false, null), loggedIn = true
        )
        assertEquals("138****8000", d.name)
    }
}
