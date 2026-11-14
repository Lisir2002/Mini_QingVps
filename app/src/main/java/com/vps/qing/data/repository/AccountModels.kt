package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vps.qing.core.network.string
import com.vps.qing.core.network.objectAt
import com.vps.qing.core.network.number

/**
 * 个人中心「账号与服务」原生数据模型（v1.3.0，不再跳网页）。
 *
 * 字段全部来自 2026-11 用登录态对站点 /console/v1 的实测：
 *  - GET /host         → data.list[{id, product_id, product_name, name, active_time, due_time,
 *                          status, renew_amount, billing_cycle, dedicate_ip, base_info, image_name...}]
 *  - GET /order        → data.list[{id, type, create_time, amount, status, gateway,
 *                          host_name, description, product_names...}]
 *  - GET /transaction  → data.list[{id, amount, gateway, transaction_number, order_id, create_time...}]
 *  - GET /ticket       → data.list[{...}]
 *  - GET /sub_account  → data.list[{...}]
 *  - GET /account      → data.account{id, username, email, phone, credit, status, register_time...}
 *
 * 分页注意：与 /product 相同，站点列表接口默认 limit=20，必须显式传大 limit。
 */
object ServiceParser {

    /** 通用取列表：data.list 数组 + data.count 总数。 */
    fun rawList(data: JsonElement?): Pair<List<JsonElement>, Int> {
        val arr = data?.takeIf { it.isJsonObject }?.asJsonObject?.get("list")
            ?.takeIf { it.isJsonArray }?.asJsonArray
        val list = arr?.toList() ?: emptyList()
        val count = data?.takeIf { it.isJsonObject }?.asJsonObject?.get("count")
            ?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() ?: list.size
        return list to count
    }

    /** 时间戳 → 本地时间字符串；失败原样返回。 */
    private fun time(v: String?): String {
        val ts = v?.toLongOrNull() ?: return v.orEmpty()
        if (ts <= 0) return "--"
        return java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.CHINA)
            .format(java.util.Date(ts * 1000))
    }

    fun hosts(data: JsonElement?): List<HostLine> =
        rawList(data).first.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            HostLine(
                id = id,
                productId = el.string("product_id").orEmpty(),
                name = el.string("name").orEmpty(),
                productName = el.string("product_name").orEmpty(),
                status = el.string("status").orEmpty(),
                dueTime = time(el.string("due_time")),
                activeTime = time(el.string("active_time")),
                billingCycle = el.string("billing_cycle").orEmpty(),
                renewAmount = el.string("renew_amount").orEmpty(),
                ip = el.string("dedicate_ip").orEmpty().ifBlank { el.string("assign_ip").orEmpty() },
                baseInfo = el.string("base_info").orEmpty(),
                imageName = el.string("image_name").orEmpty(),
                renewForbidden = el.string("renew_forbidden") == "1"
            )
        }

    fun orders(data: JsonElement?): List<OrderLine> =
        rawList(data).first.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            val names = el.takeIf { it.isJsonObject }?.asJsonObject?.get("product_names")
                ?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { runCatching { it.asString }.getOrNull() }
                .orEmpty()
            OrderLine(
                id = id,
                amount = el.string("amount").orEmpty(),
                status = el.string("status").orEmpty(),
                gateway = el.string("gateway").orEmpty(),
                createTime = time(el.string("create_time")),
                payTime = time(el.string("pay_time")),
                hostName = el.string("host_name").orEmpty(),
                productNames = names,
                description = el.string("description").orEmpty().trim(),
                type = el.string("type").orEmpty()
            )
        }

    fun transactions(data: JsonElement?): List<TxnLine> =
        rawList(data).first.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            TxnLine(
                id = id,
                amount = el.string("amount").orEmpty(),
                gateway = el.string("gateway").orEmpty(),
                number = el.string("transaction_number").orEmpty(),
                orderId = el.string("order_id").orEmpty(),
                createTime = time(el.string("create_time")),
                type = el.string("type").orEmpty()
            )
        }

    /**
     * 工单列表（plugin/27/ticket.htm 表格字段）：
     * id / ticket_num / title / name(类型名) / status(状态名) / status_id /
     * color(状态色) / post_time / last_reply_time / create_time / ticket_type_id。
     * v1.4.3 补全字段，供统计条与状态徽章使用。
     */
    fun tickets(data: JsonElement?): List<TicketLine> =
        rawList(data).first.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            TicketLine(
                id = id,
                num = el.string("ticket_num").orEmpty(),
                title = el.string("title").orEmpty(),
                typeName = el.string("name", "ticket_type_name").orEmpty(),
                typeId = el.string("ticket_type_id").orEmpty(),
                status = el.string("status").orEmpty(),
                statusId = el.string("status_id").orEmpty(),
                color = el.string("color").orEmpty(),
                createTime = time(el.string("create_time", "post_time")),
                lastReply = time(el.string("last_reply_time", "update_time", "post_time"))
            )
        }

    /**
     * 子账户列表（plugin/28/childAccount.htm 表格字段）：
     * id / username / last_action_time / status(0停用 1启用)，详情另有 email / phone。
     */
    fun subAccounts(data: JsonElement?): List<SubAccountLine> =
        rawList(data).first.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            val rawStatus = el.string("status").orEmpty()
            SubAccountLine(
                id = id,
                username = el.string("username", "name").orEmpty(),
                email = el.string("email").orEmpty(),
                phone = el.string("phone").orEmpty(),
                status = rawStatus,
                enabled = rawStatus == "1" || rawStatus.equals("true", true) ||
                    rawStatus.equals("Active", true),
                lastActionTime = time(el.string("last_action_time", "update_time")),
                createTime = time(el.string("create_time"))
            )
        }

    /** /account → 安全中心概要（邮箱/手机绑定状态）。 */
    fun security(account: AccountInfo): List<Pair<String, String>> = listOf(
        "邮箱绑定" to (account.email?.takeIf { it.isNotBlank() } ?: "未绑定"),
        "手机绑定" to (account.phone?.takeIf { it.isNotBlank() } ?: "未绑定"),
        "余额" to (account.balance?.takeIf { it.isNotBlank() } ?: "--"),
        "信用额度" to (account.creditLimit?.takeIf { it.isNotBlank() } ?: "--")
    )

    // ---- v1.4.0：订单详情 + 实名认证 ----

    /**
     * GET /order/{id} → data{order{...items[]}, self_defined_field, customfields}。
     * 实测（订单 #25）：items 内含每项配置明细（description 逐行 CPU/内存/带宽/系统/价格）、
     * 关联主机 host_name/host_status、购买周期 billing_cycle。
     */
    fun orderDetail(data: JsonElement?): OrderDetail? {
        val root = data?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val order = root.get("order")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val items = order.get("items")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { el ->
            val itemId = el.string("id") ?: return@mapNotNull null
            OrderItemLine(
                id = itemId,
                description = el.string("description").orEmpty().trim(),
                amount = el.string("amount").orEmpty(),
                hostId = el.string("host_id").orEmpty(),
                productName = el.string("product_name").orEmpty(),
                hostName = el.string("host_name").orEmpty(),
                billingCycle = el.string("billing_cycle").orEmpty(),
                hostStatus = el.string("host_status").orEmpty()
            )
        }.orEmpty()
        return OrderDetail(
            id = order.string("id").orEmpty(),
            type = order.string("type").orEmpty(),
            amount = order.string("amount").orEmpty(),
            amountUnpaid = order.string("amount_unpaid").orEmpty(),
            refundAmount = order.string("refund_amount").orEmpty(),
            credit = order.string("credit").orEmpty(),
            status = order.string("status").orEmpty(),
            gateway = order.string("gateway").orEmpty(),
            notes = order.string("notes").orEmpty(),
            createTime = time(order.string("create_time")),
            payTime = time(order.string("pay_time")),
            items = items
        )
    }

    /**
     * GET /certification/info → 实名认证开关与资料（2026-11 实测）：
     * data{certification_open, certification_company_open, certification_upload,
     * certification_uncertified_cannot_buy_product, is_certification,
     * person{username, card_name, card_number, status, create_time, auth_fail...},
     * company{...}}。卡号/姓名已由站点脱敏；company 未开启时为空对象，certFrom 会过滤。
     */
    fun certInfo(data: JsonElement?): CertInfo? {
        val root = data?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val person = root.get("person")?.takeIf { it.isJsonObject }?.asJsonObject
        val company = root.get("company")?.takeIf { it.isJsonObject }?.asJsonObject
        fun certFrom(o: com.google.gson.JsonObject?) = o?.takeIf {
            it.string("card_name").orEmpty().isNotBlank() || it.string("card_number").orEmpty().isNotBlank()
        }?.let {
            CertRecord(
                name = it.string("username", "company_name").orEmpty(),
                cardName = it.string("card_name").orEmpty(),
                cardNumber = it.string("card_number").orEmpty(),
                status = it.string("status").orEmpty(),
                createTime = time(it.string("create_time")),
                failReason = it.string("auth_fail").orEmpty()
            )
        }
        // 开关缺失视为开启（与旧行为一致）；upload/buyBlocked 为显式 opt-in：
        // 缺省按未开启处理，避免站点不发字段时误拦表单或误提示购买受限
        fun switch(key: String) = root.get(key)?.takeIf { it.isJsonPrimitive }?.asString != "0"
        fun flag(key: String) = root.get(key)?.takeIf { it.isJsonPrimitive }?.asString == "1"
        val certifiedFlag = root.get("is_certification")
            ?.takeIf { it.isJsonPrimitive }?.asString?.lowercase()
        return CertInfo(
            open = switch("certification_open"),
            isCertified = certifiedFlag == "true" || certifiedFlag == "1",
            companyOpen = switch("certification_company_open"),
            uploadRequired = flag("certification_upload"),
            buyBlocked = flag("certification_uncertified_cannot_buy_product"),
            person = certFrom(person),
            company = certFrom(company)
        )
    }

    /** GET /certification/plugin → 认证方式列表（实测：人脸实名认证，仅个人）。 */
    fun certPlugins(data: JsonElement?): List<CertPlugin> =
        rawList(data).first.mapNotNull { el ->
            val id = el.string("id") ?: return@mapNotNull null
            val types = el.takeIf { it.isJsonObject }?.asJsonObject?.get("certification_type")
                ?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { runCatching { it.asString }.getOrNull() }
                .orEmpty()
            CertPlugin(
                id = id,
                title = el.string("title", "name").orEmpty(),
                types = types
            )
        }

    // ---- v1.4.5：实名认证提交与进度 ----

    /** GET /certification/plugin/config → 收费配置；异常数据回退为免费。 */
    fun certPluginConfig(data: JsonElement?): CertPluginConfig {
        val o = data?.takeIf { it.isJsonObject }?.asJsonObject
        if (o == null) return CertPluginConfig(0, "0", false)
        return CertPluginConfig(
            free = o.string("free")?.toIntOrNull() ?: 0,
            amount = o.string("amount", "price").orEmpty().ifBlank { "0" },
            pay = o.string("pay") == "1"
        )
    }

    /** GET /certification/auth → 200 时取 data.html；400 时取 data.code（10000/10001）。 */
    fun certAuthPage(status: Int, data: JsonElement?): CertAuthPage {
        val o = data?.takeIf { it.isJsonObject }?.asJsonObject
        return if (status == 200) {
            CertAuthPage(code = 0, html = o?.string("html").orEmpty())
        } else {
            CertAuthPage(code = o?.string("code")?.toIntOrNull() ?: 0, html = "")
        }
    }

    /** GET /certification/status → {code, refresh}。 */
    fun certPollStatus(data: JsonElement?): CertPollStatus {
        val o = data?.takeIf { it.isJsonObject }?.asJsonObject
        return CertPollStatus(
            code = o?.string("code")?.toIntOrNull() ?: 0,
            refresh = o?.string("refresh")?.toIntOrNull() ?: 0
        )
    }

    /** GET /certification/custom_fields → {custom_fields:[{title,field,type,options[]?}]}。 */
    fun certCustomFields(data: JsonElement?): List<CertCustomField> =
        (data?.takeIf { it.isJsonObject }?.asJsonObject?.get("custom_fields")
            ?.takeIf { it.isJsonArray }?.asJsonArray?.toList() ?: emptyList())
            .mapNotNull { el ->
                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val field = o.string("field") ?: return@mapNotNull null
                val options = o.get("options")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { runCatching { it.asString }.getOrNull() }
                    .orEmpty()
                CertCustomField(
                    title = o.string("title", "name").orEmpty().ifBlank { field },
                    field = field,
                    options = options
                )
            }

    /** 证件类型字典（官方语义：1 身份证 … 8 其他），提交时 card_type 传值。 */
    val CARD_TYPES: List<Pair<String, String>> = listOf(
        "1" to "身份证", "2" to "港澳通行证", "3" to "台湾通行证",
        "4" to "港澳居住证", "5" to "台湾居住证", "6" to "海外护照",
        "7" to "中国以外驾照", "8" to "其他"
    )

    // ---- v1.4.2：已购产品详情（已订购的配置信息 + 支付信息，只读）----

    /** 带时分的时间戳格式化（详情页「开通时间 / 下次续费时间」）。 */
    private fun dateTime(v: String?): String {
        val ts = v?.toLongOrNull() ?: return v.orEmpty()
        if (ts <= 0) return "--"
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            .format(java.util.Date(ts * 1000))
    }

    /** 数字字段 → 整数字符串（8.0 → 8），缺失返回空串。 */
    private fun JsonElement?.intText(vararg keys: String): String =
        this.number(*keys)?.toInt()?.toString().orEmpty()

    /**
     * 合并三份实测数据：
     *  - hostData: GET /host/{id} → data.host（支付信息、addition 登录信息）
     *  - cloudData: GET /remf_cloud/{id} → data（CPU/内存/带宽/磁盘/镜像/电源状态）
     *  - specificData: GET /host/{id}/specific_info → data（IP、IP 数量、地区）
     * cloudData 允许为 null（非 mf_cloud 模块商品），此时只展示支付信息。
     */
    fun purchasedDetail(
        hostData: JsonElement?,
        cloudData: JsonElement?,
        specificData: JsonElement?
    ): PurchasedDetail? {
        val h = hostData.objectAt("host") ?: hostData.objectAt() ?: return null
        // 主体数据无效（如站点返回空对象）时不构造详情页
        if (h.string("id").isNullOrBlank()) return null
        val add = h.objectAt("addition")
        val c = cloudData.objectAt()
        val s = specificData.objectAt()
        val image = c.objectAt("image")
        return PurchasedDetail(
            id = h.string("id").orEmpty(),
            name = h.string("name").orEmpty(),
            productId = h.string("product_id").orEmpty(),
            productName = h.string("product_name").orEmpty(),
            orderId = h.string("order_id").orEmpty(),
            status = h.string("status").orEmpty(),
            powerStatus = c.string("power_status").orEmpty(),
            firstPaymentAmount = h.string("first_payment_amount").orEmpty(),
            renewAmount = h.string("renew_amount").orEmpty(),
            billingCycleName = h.string("billing_cycle_name").orEmpty(),
            payType = h.string("pay_type").orEmpty(),
            autoRenew = h.string("auto_renew") == "1" || h.string("auto_renew") == "true",
            activeTime = dateTime(h.string("active_time")),
            dueTime = dateTime(h.string("due_time")),
            baseInfo = h.string("base_info").orEmpty(),
            ip = s.string("dedicate_ip").orEmpty()
                .ifBlank { c.string("ip").orEmpty() },
            ipNum = s.intText("ip_num").ifBlank { c.intText("ip_num") },
            ipv6Num = c.intText("ipv6_num"),
            username = add.string("username").orEmpty().ifBlank { c.string("username").orEmpty() },
            password = add.string("password").orEmpty().ifBlank { c.string("password").orEmpty() },
            port = add.string("port").orEmpty().ifBlank { "22" },
            imageName = image.string("name").orEmpty()
                .ifBlank { add.string("image_name").orEmpty() },
            country = s.string("country").orEmpty().ifBlank { add.string("city").orEmpty() },
            city = s.string("city").orEmpty().ifBlank { add.string("city").orEmpty() },
            area = s.string("area").orEmpty().ifBlank { add.string("area").orEmpty() },
            lineName = c.objectAt("line").string("name").orEmpty(),
            securityGroup = c.objectAt("security_group").string("name").orEmpty(),
            cpu = c.intText("cpu"),
            memory = c.intText("memory"),
            systemDisk = c.objectAt("system_disk").intText("size"),
            dataDiskCount = c.objectAt("data_disk").intText("count"),
            dataDiskSize = c.objectAt("data_disk").intText("total_size"),
            bw = c.intText("bw"),
            gpu = c.string("gpu").orEmpty(),
            hasCloudConfig = c != null
        )
    }
}

/**
 * 待支付/未支付主机状态判定（v1.4.11）：
 * 已购机器列表（首页已购服务、我的-已购产品面板）统一隐藏未支付订单产生的机器，
 * 这些机器尚未开通，不应出现在「已购」语义的列表里。
 */
fun isUnpaidHostStatus(status: String?): Boolean =
    status != null && Regex("(?i)unpaid|待支付|待付款|未支付").containsMatchIn(status)

data class HostLine(
    val id: String,
    /** 关联商品 id（/host 返回 product_id），用于跳转商品配置页。 */
    val productId: String,
    val name: String,
    val productName: String,
    val status: String,
    val dueTime: String,
    val activeTime: String,
    val billingCycle: String,
    val renewAmount: String,
    val ip: String,
    val baseInfo: String,
    val imageName: String,
    val renewForbidden: Boolean
)

data class OrderLine(
    val id: String,
    val amount: String,
    val status: String,
    val gateway: String,
    val createTime: String,
    val payTime: String,
    val hostName: String,
    val productNames: List<String>,
    val description: String,
    val type: String
)

data class TxnLine(
    val id: String,
    val amount: String,
    val gateway: String,
    val number: String,
    val orderId: String,
    val createTime: String,
    val type: String
)

data class TicketLine(
    val id: String,
    val title: String,
    val status: String,
    val createTime: String,
    val lastReply: String,
    /** 工单号 ticket_num（对外展示的编号）。 */
    val num: String = "",
    /** 工单类型名称。 */
    val typeName: String = "",
    val typeId: String = "",
    /** 状态 id（4 = 已关闭，站点据此禁用催单/关闭按钮）。 */
    val statusId: String = "",
    /** 站点返回的状态色（#RRGGBB），用于动态徽章配色。 */
    val color: String = ""
) {
    val isClosed: Boolean get() = statusId == "4" || status.equals("已关闭", true)
    /** 编号优先展示工单号，缺失回退 id。 */
    val displayNum: String get() = num.ifBlank { id }
}

data class SubAccountLine(
    val id: String,
    val username: String,
    val email: String,
    val status: String,
    val phone: String = "",
    /** 是否启用（status=1）。 */
    val enabled: Boolean = false,
    val lastActionTime: String = "",
    val createTime: String = ""
)

/** 订单详情（GET /order/{id}）。 */
data class OrderDetail(
    val id: String,
    val type: String,
    val amount: String,
    val amountUnpaid: String,
    val refundAmount: String,
    val credit: String,
    val status: String,
    val gateway: String,
    val notes: String,
    val createTime: String,
    val payTime: String,
    val items: List<OrderItemLine>
)

/** 订单明细项（含逐项配置描述与关联主机）。 */
data class OrderItemLine(
    val id: String,
    val description: String,
    val amount: String,
    val hostId: String,
    val productName: String,
    val hostName: String,
    val billingCycle: String,
    val hostStatus: String
)

/** 实名认证信息。 */
data class CertInfo(
    val open: Boolean,
    val isCertified: Boolean,
    /** 企业认证是否开启（本站实测关闭）。 */
    val companyOpen: Boolean = false,
    /** 提交认证是否要求上传证件照（控制表单是否显示上传入口）。 */
    val uploadRequired: Boolean = false,
    /** 未实名是否禁止购买商品（用于提示 urgency）。 */
    val buyBlocked: Boolean = false,
    val person: CertRecord?,
    val company: CertRecord?
)

/** 个人/企业认证记录。 */
data class CertRecord(
    val name: String,
    val cardName: String,
    val cardNumber: String,
    val status: String,
    val createTime: String,
    val failReason: String
) {
    /**
     * 状态文案。语义取自官方插件源码（idcsmart_certification 控制器文档）：
     * 1=已认证，2=未通过（含驳回原因），3=待审核，4=已提交资料；其余未实名。
     */
    val statusText: String
        get() = when (status) {
            "1" -> "已实名"
            "2" -> "未通过"
            "3" -> "待审核"
            "4" -> "已提交资料"
            else -> "未实名"
        }
}

/** 认证方式（插件）。 */
data class CertPlugin(
    val id: String,
    val title: String,
    val types: List<String>
)

/** GET /certification/plugin/config → 认证收费配置（本站实测：free=1, amount=1, pay=1）。 */
data class CertPluginConfig(
    /** 免费认证次数。 */
    val free: Int,
    /** 每次认证金额（元）。 */
    val amount: String,
    /** 是否需要支付。 */
    val pay: Boolean
) {
    /** 给用户看的一句话费用说明；空串表示免费额度内。 */
    val feeHint: String
        get() = when {
            !pay -> "当前认证方式免费"
            free > 0 -> "享有 $free 次免费认证，超出后每次 ¥$amount"
            else -> "本次认证需支付 ¥$amount"
        }
}

/** GET /certification/auth → 验证页（人脸认证渲染 data.html；400 时按 code 跳转）。 */
data class CertAuthPage(
    /** 10000=去选择认证方式；10001=加载基础信息页（已通过/待审核/未通过）。 */
    val code: Int,
    /** status==200 时的人脸认证页面 HTML（内含第三方脚本与状态轮询）。 */
    val html: String
)

/** GET /certification/status → 轮询响应：仅 code==2 && refresh==0 时继续轮询。 */
data class CertPollStatus(
    /** 1通过 2未通过 3待审核 4已提交资料。 */
    val code: Int,
    val refresh: Int
)

/** GET /certification/custom_fields → 自定义认证字段。 */
data class CertCustomField(
    val title: String,
    val field: String,
    /** 文本输入；下拉选项非空时为 select。 */
    val options: List<String>
)

/**
 * 已购产品详情（v1.4.2）：展示「已经订购的配置信息 + 支付信息」，不是购买页。
 * 来自 GET /host/{id}、GET /remf_cloud/{id}、GET /host/{id}/specific_info 三接口合并。
 */
data class PurchasedDetail(
    val id: String,
    val name: String,
    val productId: String,
    val productName: String,
    val orderId: String,
    val status: String,
    val powerStatus: String,
    val firstPaymentAmount: String,
    val renewAmount: String,
    val billingCycleName: String,
    val payType: String,
    val autoRenew: Boolean,
    val activeTime: String,
    val dueTime: String,
    val baseInfo: String,
    val ip: String,
    val ipNum: String,
    val ipv6Num: String,
    val username: String,
    val password: String,
    val port: String,
    val imageName: String,
    val country: String,
    val city: String,
    val area: String,
    val lineName: String,
    val securityGroup: String,
    val cpu: String,
    val memory: String,
    val systemDisk: String,
    val dataDiskCount: String,
    val dataDiskSize: String,
    val bw: String,
    val gpu: String,
    /** remf_cloud 实例配置是否可用（非套餐型商品为 false，只展示支付信息）。 */
    val hasCloudConfig: Boolean
)

/** 测试辅助：构造空包装对象。 */
internal fun listWrapper(vararg items: JsonObject): JsonObject =
    JsonObject().apply {
        add("list", com.google.gson.JsonArray().apply { items.forEach { add(it) } })
        addProperty("count", items.size)
    }
