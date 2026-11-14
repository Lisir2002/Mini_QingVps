package com.vps.qing.data.repository

import com.google.gson.JsonObject

/**
 * 安全中心（绑定手机 / 绑定邮箱）发送验证码的请求体。纯 JVM，便于单元测试。
 *
 * 协议来源：站点 `clientarea/template/pc/default/js/account.js` 的
 * `sendPhoneCode()` / `sendEmailCode()`，并用匿名 POST 实测过：
 * - 不带 `captcha`/`token` → 400「请输入图形验证码」；
 * - 带错误凭证 → 400「行为验证码错误,请查看配置是否正确」。
 *
 * 注意 `phone_code` 是国家区号（站点用 Number），不是短信验证码；
 * `action=verify` 验证原绑定、`action=update` 验证新绑定、`action=password_reset` 找回密码。
 */
object SecurityBodies {
    const val ACTION_VERIFY = "verify"
    const val ACTION_UPDATE = "update"

    fun code(
        target: String,
        action: String,
        value: String,
        phoneCode: String = "86",
        captcha: String = "",
        captchaToken: String = ""
    ): JsonObject = JsonObject().apply {
        addProperty("action", action)
        if (target == "phone") {
            addProperty("phone_code", phoneCode.toIntOrNull() ?: 86)
            addProperty("phone", value)
        } else {
            addProperty("email", value)
        }
        // 站点开启 captcha_client_verify / captcha_client_update 时必带，缺一个就 400
        addProperty("token", captchaToken)
        addProperty("captcha", captcha)
    }
}
