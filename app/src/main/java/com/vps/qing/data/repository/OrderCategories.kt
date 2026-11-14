package com.vps.qing.data.repository

/**
 * 订单 / 交易记录分类字典（v1.4.14）。
 *
 * 事实来源：站点财务中心 `finance.htm` + `js/finance.js` + `lang/zh-cn/index.js`
 * 2026-11 实测。网站订单列表配了两个筛选下拉：
 *  - 订单类型 `params1.type`（orderType，7 项）+ 状态 `params1.status`（Unpaid/Paid）；
 *  - 交易列表无分类下拉，但 `type` 列用 orderTypeObj（同一套订单类型字典）映射、
 *    `gateway` 列展示支付渠道。
 *
 * v1.4.13 之前 App 从当前页数据 distinct 动态造分类、且交易 type 用了
 * recharge/consume/refund 一套站点根本不返回的字典，导致分类显示不全、筛选命中为空。
 * 这里改为「网站固定字典 + 当前数据计数」，只显示真实存在数据的分类，
 * 并把筛选抽成纯函数，保证每个分类都能命中对应记录（可单测）。
 */
object OrderCategories {

    /** 网站 orderType / orderTypeObj 全量（含 combine、credit_limit 两种展示态）。 */
    val ORDER_TYPES: List<Pair<String, String>> = listOf(
        "new" to "新订单",
        "renew" to "续费订单",
        "on_demand" to "按需续费",
        "upgrade" to "升降级",
        "artificial" to "人工订单",
        "recharge" to "充值订单",
        "change_billing_cycle" to "按需转包年包月",
        "combine" to "合并订单",
        "credit_limit" to "还款订单"
    )

    /** 网站订单状态（finance.htm 下拉 + 列表渲染的 status 分支）。 */
    val ORDER_STATUSES: List<Pair<String, String>> = listOf(
        "Unpaid" to "未付款",
        "Paid" to "已付款",
        "Refunded" to "已退款",
        "WaitUpload" to "待上传",
        "WaitReview" to "待审核",
        "ReviewFail" to "未通过"
    )

    /** 订单类型 → 中文；未知值原样返回（不猜测）。 */
    fun typeText(type: String): String =
        ORDER_TYPES.firstOrNull { it.first.equals(type, ignoreCase = true) }?.second
            ?: type.ifBlank { "--" }

    /** 订单状态 → 中文；未知值原样返回。 */
    fun statusText(status: String): String =
        ORDER_STATUSES.firstOrNull { it.first.equals(status, ignoreCase = true) }?.second
            ?: status.ifBlank { "--" }

    /** 未付款判定（供「继续支付」等复用）。 */
    fun isUnpaid(status: String): Boolean = status.equals("Unpaid", ignoreCase = true)

    /**
     * 订单分类胶囊：全部 + 网站字典中存在数据的类型 + 状态，按网站字典顺序。
     * id 为 null 表示全部；"type:xxx" / "status:xxx" 为筛选键。
     */
    fun orderOptions(orders: List<OrderLine>): List<Pair<String?, String>> = buildList {
        add(null to "全部 ${orders.size}")
        ORDER_TYPES.forEach { (value, label) ->
            val n = orders.count { it.type.equals(value, ignoreCase = true) }
            if (n > 0) add("type:$value" to "$label $n")
        }
        // 站点新增但字典未收录的类型：原样兜底，避免丢数据
        orders.map { it.type }
            .filter { raw -> raw.isNotBlank() && ORDER_TYPES.none { it.first.equals(raw, true) } }
            .distinctBy { it.lowercase() }
            .forEach { raw -> add("type:$raw" to "$raw ${orders.count { it.type.equals(raw, true) }}") }
        ORDER_STATUSES.forEach { (value, label) ->
            val n = orders.count { it.status.equals(value, ignoreCase = true) }
            if (n > 0) add("status:$value" to "$label $n")
        }
    }

    /**
     * 交易分类胶囊：全部 + 网站订单类型字典（交易 type 同源）+ 站点实际支付渠道。
     * 渠道名由站点动态返回，按出现顺序 distinct。
     */
    fun txnOptions(txns: List<TxnLine>): List<Pair<String?, String>> = buildList {
        add(null to "全部 ${txns.size}")
        ORDER_TYPES.forEach { (value, label) ->
            val n = txns.count { it.type.equals(value, ignoreCase = true) }
            if (n > 0) add("type:$value" to "$label $n")
        }
        txns.map { it.type }
            .filter { raw -> raw.isNotBlank() && ORDER_TYPES.none { it.first.equals(raw, true) } }
            .distinctBy { it.lowercase() }
            .forEach { raw -> add("type:$raw" to "$raw ${txns.count { it.type.equals(raw, true) }}") }
        txns.map { it.gateway.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { g -> add("gw:$g" to "$g ${txns.count { it.gateway.trim() == g }}") }
    }

    /** 订单筛选：null 全部；type:/status: 精确（忽略大小写）命中。 */
    fun filterOrders(orders: List<OrderLine>, filter: String?): List<OrderLine> = when {
        filter == null -> orders
        filter.startsWith("type:") ->
            orders.filter { it.type.equals(filter.removePrefix("type:"), ignoreCase = true) }
        filter.startsWith("status:") ->
            orders.filter { it.status.equals(filter.removePrefix("status:"), ignoreCase = true) }
        else -> orders
    }

    /** 交易筛选：null 全部；type:/gw: 命中。 */
    fun filterTxns(txns: List<TxnLine>, filter: String?): List<TxnLine> = when {
        filter == null -> txns
        filter.startsWith("type:") ->
            txns.filter { it.type.equals(filter.removePrefix("type:"), ignoreCase = true) }
        filter.startsWith("gw:") ->
            txns.filter { it.gateway.trim() == filter.removePrefix("gw:") }
        else -> txns
    }
}
