package com.vps.qing.data.repository

import com.google.gson.JsonElement
import com.vps.qing.core.network.arrayAt
import com.vps.qing.core.network.number
import com.vps.qing.core.network.string

/**
 * 续费模型与解析（v1.4.26）。
 *
 * 依据魔方 V10 官方 addon idcsmart_renew：
 * - `GET  /console/v1/host/{id}/renew` → data.host[] 可续费周期；
 * - `POST /console/v1/host/{id}/renew` → 顶层 code=Paid（余额/免费自动续费）或
 *   code=Unpaid + data.id（生成的待支付订单ID，前端 showPayDialog 用它拉起支付）。
 *
 * 周期字段来自 CloudLogic::durationPrice（云产品）与通用产品 duration_price：
 * - id：周期记录标识（前端 renewActiveId 用它做选中态，提交时实际传 billing_cycle）
 * - billing_cycle：周期编码（云产品英文如 "month"/"year"，通用产品中文如 "小时"）——**提交必须原样回传**
 * - duration：周期秒数（如 2592000）
 * - price：售价（用户实付，下单金额取它）
 * - base_price：成本价 / 原价
 * - renew_amount：续费成本价
 * - max_renew：是否最大续费（优惠码不可叠加标记）
 */
data class RenewCycle(
    val id: String = "",
    val billingCycle: String = "",
    val description: String = "",
    val duration: Long = 0L,
    val price: Double = 0.0,
    val basePrice: Double = 0.0,
    val renewAmount: Double = 0.0,
    val maxRenew: Boolean = false
)

/** 提交续费的结果：支付完成 or 生成待支付订单。 */
data class RenewResult(
    /** 顶层 code：Paid / Unpaid。 */
    val code: String = "",
    /** 是否直接支付完成（余额抵扣 / 免费）。 */
    val paid: Boolean = false,
    /** 待支付订单ID（code=Unpaid 时用于拉起支付）。 */
    val orderId: String = "",
    /** 接口消息。 */
    val message: String = ""
)

object RenewParser {

    /** 解析 GET renew 的 data.host[]。兼容 data 直接是数组的形态。 */
    fun cycles(data: JsonElement?): List<RenewCycle> {
        val arr = data.arrayAt("host") ?: data.arrayAt("list")
            ?: (data?.takeIf { it.isJsonArray }?.asJsonArray)
            ?: return emptyList()
        return arr.map { el ->
            RenewCycle(
                id = el.string("id").orEmpty(),
                billingCycle = el.string("billing_cycle", "billing_cycle_name").orEmpty(),
                description = el.string("description", "name", "billing_cycle_name").orEmpty(),
                duration = (el.number("duration", "billing_cycle_time") ?: 0.0).toLong(),
                price = el.number("price", "amount") ?: 0.0,
                basePrice = el.number("base_price") ?: 0.0,
                renewAmount = el.number("renew_amount") ?: 0.0,
                maxRenew = el.string("max_renew") == "1" || el.string("max_renew") == "true"
            )
        }
    }

    /**
     * 解析顶层 code + data.id。站点 code 与 status 并列在响应体顶层，
     * 故此处接收完整 envelope 的 code 与 data。
     */
    fun result(code: String?, data: JsonElement?, msg: String?): RenewResult {
        val c = code.orEmpty()
        val orderId = data.string("id", "order_id").orEmpty()
        return RenewResult(
            code = c,
            paid = c.equals("Paid", true) || data.string("code").equals("Paid", true),
            orderId = orderId,
            message = msg.orEmpty()
        )
    }

    /** 自动续费状态：data.status 1开启 0关闭，兼容布尔与字符串。 */
    fun autoStatus(data: JsonElement?): Boolean {
        val v = data.string("status", "auto_renew", "value")
            ?: return false
        return v == "1" || v.equals("true", true)
    }
}
