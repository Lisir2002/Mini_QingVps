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

/**
 * 子账户权限树的勾选状态机（纯 JVM，便于单测）。
 *
 * 协议依据站点 addChild.js：
 *  - 权限树 [AuthNode] 可多层嵌套（child 递归），叶子才是真正的一条具体权限；
 *  - 提交 auth = 树控件 `getCheckedKeys() + getHalfCheckedKeys()`：
 *    即**所有「部分或全部后代被选中」的节点 id**（选中叶子 + 半选/全选父链），
 *    站点用 deepFun 从选中叶子逐级上溯父 id 拼出来；
 *  - `disabledFn` 强制 title=="概要" 的节点 checked=true 且不可取消，
 *    `funActive` 把「概要」及其祖先链无条件加入提交列表。
 *
 * 因此这里统一以「选中叶子集合」为事实来源：
 *  [stateOf] 由叶子选中比例派生三态；[normalize] 由叶子集合生成完整 auth；
 *  [toggle] 切换任意节点（组则整组、叶子则单项）后重新 normalize。
 *  UI 只需持有 form.auth（完整列表），无需另存一份状态，编辑回填也天然兼容。
 */
object AuthTreeSelection {

    enum class State { OFF, ON, INDETERMINATE }

    /** 站点强制必选的分组名（disabledFn）。 */
    private const val MANDATORY_TITLE = "概要"

    /** 节点下所有叶子 id（节点自身是叶子时返回自身）。 */
    fun leavesOf(node: AuthNode): List<String> =
        if (node.children.isEmpty()) listOf(node.id)
        else node.children.flatMap { leavesOf(it) }

    fun allLeafIds(nodes: List<AuthNode>): Set<String> =
        nodes.flatMap { leavesOf(it) }.toSet()

    fun allNodeIds(nodes: List<AuthNode>): Set<String> {
        val out = LinkedHashSet<String>()
        fun visit(n: AuthNode) { out.add(n.id); n.children.forEach { visit(it) } }
        nodes.forEach { visit(it) }
        return out
    }

    fun find(nodes: List<AuthNode>, id: String): AuthNode? {
        for (n in nodes) {
            if (n.id == id) return n
            find(n.children, id)?.let { return it }
        }
        return null
    }

    /** 「概要」节点所在路径（自身 + 所有祖先）id，站点无条件提交。 */
    fun mandatoryPathIds(nodes: List<AuthNode>): Set<String> {
        val out = LinkedHashSet<String>()
        fun visit(n: AuthNode, ancestors: List<String>): Boolean {
            val path = ancestors + n.id
            var hit = n.title == MANDATORY_TITLE
            for (c in n.children) if (visit(c, path)) hit = true
            if (hit) out.addAll(path)
            return hit
        }
        nodes.forEach { visit(it, emptyList()) }
        return out
    }

    /** 「概要」节点（含其子树）里的叶子，勾选时保底加入，取消时也不允许移除。 */
    private fun mandatoryLeafIds(nodes: List<AuthNode>): Set<String> {
        val out = LinkedHashSet<String>()
        fun visit(n: AuthNode) {
            if (n.title == MANDATORY_TITLE) out.addAll(leavesOf(n))
            n.children.forEach { visit(it) }
        }
        nodes.forEach { visit(it) }
        return out
    }

    /** 节点自身是否是必选项（UI 禁用其勾选交互）。 */
    fun isMandatoryNode(node: AuthNode): Boolean = node.title == MANDATORY_TITLE

    /** 由「完整 auth 列表」派生节点三态（只看叶子命中比例，父 id 是否在内不影响）。 */
    fun stateOf(node: AuthNode, auth: Set<String>): State {
        val leaves = leavesOf(node)
        val hit = leaves.count { it in auth }
        return when {
            hit == 0 -> State.OFF
            hit == leaves.size -> State.ON
            else -> State.INDETERMINATE
        }
    }

    /**
     * 由「选中叶子集合」生成完整提交 auth：所有 state!=OFF 的节点 id
     * + 概要必选路径 + 树外 id（站点隐含项原样回传，保证保真）。
     */
    fun normalize(nodes: List<AuthNode>, auth: Set<String>): List<String> {
        val leafIds = allLeafIds(nodes)
        val kept = (auth.filter { it in leafIds } + mandatoryLeafIds(nodes)).toMutableSet()
        val out = LinkedHashSet<String>()
        fun visit(n: AuthNode) {
            if (stateOf(n, kept) != State.OFF) out.add(n.id)
            n.children.forEach { visit(it) }
        }
        nodes.forEach { visit(it) }
        out.addAll(mandatoryPathIds(nodes))
        out.addAll(auth - allNodeIds(nodes))
        return out.toList()
    }

    /** 切换任意节点：全选态→取消其下所有叶子，否则→选中其下所有叶子；随后 normalize。 */
    fun toggle(nodes: List<AuthNode>, auth: List<String>, nodeId: String): List<String> {
        val set = auth.toMutableSet()
        val node = find(nodes, nodeId) ?: return normalize(nodes, set)
        val leafIds = allLeafIds(nodes)
        val leaves = leavesOf(node)
        val kept = set.filter { it in leafIds }.toMutableSet()
        if (stateOf(node, kept) == State.ON) kept.removeAll(leaves) else kept.addAll(leaves)
        val extras = set.filter { it !in allNodeIds(nodes) } // 树外 id 原样保留
        return normalize(nodes, (kept + mandatoryLeafIds(nodes) + extras).toSet())
    }
}
