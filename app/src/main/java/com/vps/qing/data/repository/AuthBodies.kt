package com.vps.qing.data.repository

import com.google.gson.JsonObject
import com.vps.qing.core.network.PasswordCipher

/**
 * 认证接口请求体构造（纯 JVM，无 Android 依赖，便于本地单元测试）。
 *
 * 协议来源：站点前端 login.js / regist.js / forget.js / api/common.js，
 * 并对 `POST /console/v1/login` 做过匿名实测（返回「邮箱未注册」，证明字段与密文格式被接受）。
 *
 * 关键约定：
 * - 登录用 `type=password|code` + `account`，密码必须经 [PasswordCipher] 加密；
 * - 注册 / 找回密码用 `type=email|phone` + `account`，密码是**明文**，且要 `re_password`；
 * - `phone_code` 是国家区号（"86"），不是短信验证码；
 * - 发验证码的 `action` 只有三种：login / register / password_reset；
 * - 图形验证码凭证 `captcha` + `token` 挂在「发送验证码」请求上，登录本身不需要（本站 captcha_client_login=0）。
 */
object AuthBodies {

    const val ACTION_LOGIN = "login"
    const val ACTION_REGISTER = "register"
    const val ACTION_PASSWORD_RESET = "password_reset"

    private const val DEFAULT_COUNTRY_CODE = "86"

    /** POST /login。密码登录时 password 为 AES 密文；验证码登录时 password 为空串。 */
    fun login(
        account: String,
        password: String? = null,
        code: String? = null,
        countryCode: String = DEFAULT_COUNTRY_CODE,
        remember: Boolean = false,
        captcha: String = "",
        captchaToken: String = ""
    ): JsonObject {
        val byPassword = !password.isNullOrEmpty()
        return JsonObject().apply {
            addProperty("type", if (byPassword) "password" else "code")
            addProperty("account", account)
            addProperty("phone_code", countryCode)
            addProperty("code", code.orEmpty())
            addProperty("password", if (byPassword) PasswordCipher.encrypt(password!!) else "")
            addProperty("remember_password", if (remember) "1" else "0")
            addProperty("captcha", captcha)
            addProperty("token", captchaToken)
        }
    }

    /** POST /register。本站只开放邮箱注册（register_phone=0），密码明文。 */
    fun register(
        email: String,
        password: String,
        confirmPassword: String,
        emailCode: String,
        countryCode: String = DEFAULT_COUNTRY_CODE
    ): JsonObject = JsonObject().apply {
        addProperty("type", "email")
        addProperty("account", email)
        addProperty("phone_code", countryCode)
        addProperty("code", emailCode)
        addProperty("username", "")
        addProperty("password", password)
        addProperty("re_password", confirmPassword)
        add("customfield", JsonObject())
    }

    /** POST /email/code。`action` 决定场景，图形验证码凭证必带。 */
    fun emailCode(
        email: String,
        action: String = ACTION_REGISTER,
        captcha: String = "",
        captchaToken: String = ""
    ): JsonObject = JsonObject().apply {
        addProperty("action", action)
        addProperty("email", email)
        addProperty("token", captchaToken)
        addProperty("captcha", captcha)
    }

    /** POST /phone/code。 */
    fun phoneCode(
        phone: String,
        action: String = ACTION_REGISTER,
        countryCode: String = DEFAULT_COUNTRY_CODE,
        captcha: String = "",
        captchaToken: String = ""
    ): JsonObject = JsonObject().apply {
        addProperty("action", action)
        addProperty("phone_code", countryCode)
        addProperty("phone", phone)
        addProperty("token", captchaToken)
        addProperty("captcha", captcha)
    }

    /** POST /account/password_reset。密码明文，成功后站点前端会自动调用登录。 */
    fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
        confirmPassword: String = newPassword,
        countryCode: String = DEFAULT_COUNTRY_CODE
    ): JsonObject = JsonObject().apply {
        addProperty("type", "email")
        addProperty("account", email)
        addProperty("phone_code", countryCode)
        addProperty("code", code)
        addProperty("password", newPassword)
        addProperty("re_password", confirmPassword)
    }
}
