package com.vps.qing.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.number
import com.vps.qing.core.network.objectAt
import com.vps.qing.core.network.string
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 子账户管理模型与解析器（v1.4.3）。
 *
 * 协议来自站点插件 idcsmart_sub_account 的前端 JS 与 2026-11 实测：
 * 插件页面 plugin/28/childAccount.htm、addChildAccount.htm；
 * 接口文件 plugins/addon/idcsmart_sub_account/template/clientarea/pc/default/api/childAccount.js。
 *
 *  - GET    /sub_account          → data.list[{id,username,last_action_time,status}]
 *  - GET    /sub_account/{id}    → data.account 含基本信息与 auth 权限 id 列表
 *  - PUT    /sub_account/{id}/status → 启用/停用
 *  - DELETE /sub_account/{id}    → 删除
 *  - POST   /sub_account         → 创建
 *  - PUT    /sub_account/{id}    → 编辑
 *  - GET    /auth                → 权限树 data.list[{id,title,parent_id,rules,child}]
 *  - GET    /module              → 可选模块 data.list[{name,display_name}]
 *  - GET    /host/all            → 关联产品 data.list[{id,product_name,name}]
 *  - GET    /country             → 国家区号（phone_code）
 *
 * 全部解析走纯 JVM JsonElement，宽松取值。
 */

/** 子账户详情 / 编辑表单。 */
data class SubAccountDetail(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val phoneCode: String = "",
    val phone: String = "",
    val status: String = "",
    val lastActionTime: String = "",
    val createTime: String = "",
    /** 可见产品类型：module（按模块）或 host（按具体产品）。 */
    val visibleProduct: String = "module",
    val projectIds: List<String> = emptyList(),
    val hostIds: List<String> = emptyList(),
    val modules: List<String> = emptyList(),
    /** 已勾选的权限节点 id（含叶子与半选父节点，提交时原样回传）。 */
    val auth: List<String> = emptyList()
) {
    val enabled: Boolean get() = status == "1" || status.equals("true", true)
}

/** 权限树节点（/auth）。 */
data class AuthNode(
    val id: String,
    val title: String,
    val url: String = "",
    val parentId: String = "",
    val rules: List<String> = emptyList(),
    val children: List<AuthNode> = emptyList()
) {
    val isLeaf: Boolean get() = children.isEmpty()
    /** 展平成可勾选列表（含自身与所有后代）。 */
    fun flatten(): List<AuthNode> = listOf(this) + children.flatMap { it.flatten() }
}

/** 可关联的产品（/host/all）。 */
data class HostOption(
    val id: String,
    val productId: String = "",
    val productName: String = "",
    val name: String = "",
    val status: String = ""
) {
    val label: String get() = listOf(name, productName).filter { it.isNotBlank() }.joinToString(" · ")
        .ifBlank { "#$id" }
}

/** 可选模块（/module）。 */
data class ModuleOption(
    val name: String,
    val displayName: String = "",
    val version: String = ""
)

/** 国家区号（/country）。 */
data class CountryOption(
    val id: String = "",
    val name: String = "",
    val phoneCode: String = ""
)

/** 创建/编辑子账户的表单输入（ViewModel 持有，提交时交给 [SubAccountBodies.build]）。 */
data class SubAccountForm(
    val username: String = "",
    val email: String = "",
    val phoneCode: String = "86",
    val phone: String = "",
    val password: String = "",
    val visibleProduct: String = "module",
    val projectIds: List<String> = emptyList(),
    val hostIds: List<String> = emptyList(),
    val modules: List<String> = emptyList(),
    val auth: List<String> = emptyList()
) {
    /** 基础校验：用户名必填，新建时邮箱/手机至少一项 + 密码。 */
    fun validate(isEdit: Boolean): String? = when {
        username.isBlank() -> "请填写子账户用户名"
        email.isBlank() && phone.isBlank() -> "请填写邮箱或手机号"
        !isEdit && password.isBlank() -> "请设置登录密码"
        email.isNotBlank() && !email.contains("@") -> "邮箱格式不正确"
        else -> null
    }
}

object SubAccountParser {

    private fun dateTime(v: String?): String {
        val ts = v?.trim()?.toLongOrNull() ?: return v?.takeIf { it.isNotBlank() } ?: "--"
        if (ts <= 0L) return "--"
        val millis = if (ts > 1_000_000_000_000L) ts else ts * 1000L
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
    }

    private fun stringList(el: JsonElement?, vararg keys: String): List<String> {
        val arr = el?.arrayAt(*keys) ?: return emptyList()
        return arr.mapNotNull { a -> runCatching { a.asString }.getOrNull() }
    }

    /** /sub_account/{id} → data.account。 */
    fun detail(data: JsonElement?): SubAccountDetail? {
        val a = data.objectAt("account") ?: data?.takeIf { it.isJsonObject } ?: return null
        val id = a.string("id") ?: return null
        return SubAccountDetail(
            id = id,
            username = a.string("username", "name").orEmpty(),
            email = a.string("email").orEmpty(),
            phoneCode = a.string("phone_code").orEmpty(),
            phone = a.string("phone").orEmpty(),
            status = a.string("status").orEmpty(),
            lastActionTime = dateTime(a.string("last_action_time")),
            createTime = dateTime(a.string("create_time")),
            visibleProduct = a.string("visible_product").orEmpty().ifBlank { "module" },
            projectIds = stringList(a, "project_id"),
            hostIds = stringList(a, "host_id"),
            modules = stringList(a, "module"),
            auth = stringList(a, "auth")
        )
    }

    /** /auth → 权限树（保留 child 层级）。 */
    fun auth(data: JsonElement?): List<AuthNode> =
        (data.arrayAt("list") ?: data.arrayAt() ?: JsonArray()).mapNotNull { el -> parseAuthNode(el) }

    private fun parseAuthNode(el: JsonElement): AuthNode? {
        val id = el.string("id") ?: return null
        val rules = (el.arrayAt("rules") ?: JsonArray())
            .mapNotNull { r -> runCatching { r.asString }.getOrNull() }
        val children = (el.arrayAt("child") ?: JsonArray()).mapNotNull { parseAuthNode(it) }
        return AuthNode(
            id = id,
            title = el.string("title", "name").orEmpty(),
            url = el.string("url").orEmpty(),
            parentId = el.string("parent_id").orEmpty(),
            rules = rules,
            children = children
        )
    }

    /** /host/all → 关联产品精简列表。 */
    fun hosts(data: JsonElement?): List<HostOption> =
        (data.arrayAt("list") ?: data.arrayAt() ?: JsonArray()).mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            HostOption(
                id = id,
                productId = el.string("product_id").orEmpty(),
                productName = el.string("product_name").orEmpty(),
                name = el.string("name").orEmpty(),
                status = el.string("status").orEmpty()
            )
        }

    /** /module → 可选模块。 */
    fun modules(data: JsonElement?): List<ModuleOption> =
        (data.arrayAt("list") ?: data.arrayAt() ?: JsonArray()).mapNotNull { el ->
            val name = el.string("name") ?: return@mapNotNull null
            ModuleOption(
                name = name,
                displayName = el.string("display_name", "title").orEmpty(),
                version = el.string("version").orEmpty()
            )
        }

    /** /country → 国家区号（站点结构多样，宽松解析）。 */
    fun countries(data: JsonElement?): List<CountryOption> {
        val root = data?.arrayAt("list")
            ?: data?.arrayAt("country")
            ?: data?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return emptyList()
        return root.mapNotNull { el ->
            val code = el.string("phone_code", "phonecode", "area_code", "code")
                ?: return@mapNotNull null
            CountryOption(
                id = el.string("id").orEmpty(),
                name = el.string("name", "name_zh", "country_name").orEmpty(),
                phoneCode = code
            )
        }
    }
}

/** 子账户写操作请求体（纯 JVM）。 */
object SubAccountBodies {

    /** 创建/编辑：字段与站点 addChildAccount.js 的 addAccountForm 对齐。 */
    fun build(form: SubAccountForm, id: String? = null): JsonObject = JsonObject().apply {
        addProperty("username", form.username)
        addProperty("email", form.email)
        form.phoneCode.toLongOrNull()?.let { addProperty("phone_code", it) }
            ?: addProperty("phone_code", form.phoneCode)
        addProperty("phone", form.phone)
        if (form.password.isNotBlank()) addProperty("password", form.password)
        addProperty("visible_product", form.visibleProduct)
        add("project_id", JsonArray().apply { form.projectIds.forEach { add(it) } })
        add("host_id", JsonArray().apply { form.hostIds.forEach { add(it) } })
        add("module", JsonArray().apply { form.modules.forEach { add(it) } })
        add("auth", JsonArray().apply { form.auth.forEach { add(it) } })
        id?.let { id.toLongOrNull()?.let { addProperty("id", it) } ?: addProperty("id", id) }
    }

    /** 启用/停用：status 1 启用 0 停用。 */
    fun status(enabled: Boolean): JsonObject = JsonObject().apply {
        addProperty("status", if (enabled) 1 else 0)
    }
}
