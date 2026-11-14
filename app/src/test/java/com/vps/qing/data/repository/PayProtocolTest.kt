package com.vps.qing.data.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vps.qing.core.network.ApiEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.18 支付全链路真实性协议测试。
 *
 * 依据站点官方 payDialog.js、pay_ctrl.php 与真实抓包样本：
 *  - POST /pay 的 code 在**响应体顶层**（Paid / Unpaid），data.html 是站点返回的
 *    支付入口 HTML（真实样本是「立即支付」跳转按钮，不是二维码）；
 *  - GET /pay/{id}/status 的 code 也在**顶层**（Paid / Upaid），status=400 停止轮询；
 *  - 订单有效期来自 GET /order/{id} 的 order.unpaid_timeout / remain_pay_time。
 *
 * 这些用例把「App 绝不把收银台 href 当支付码」和「读顶层 code」固化下来，
 * 防止后续改动退回旧版假二维码 / 永远检测不到支付成功。
 */
class PayProtocolTest {

    /** 站点实测 POST /pay 返回的 data.html（易支付收银台跳转按钮）。 */
    private val cashierHtml = """<a href="https://zf.qingyayun.cn/submit.php?pid=1000&type=alipay&notify_url=https%3A%2F%2Fvps.qingyayun.cn%2Fgateway%2Fepay_ali%2Findex%2FnotifyHandle&out_trade_no=17946053744591105947&money=96.00&sign=1f34d173d916e0c24913e760cffe298d&sign_type=MD5" target="_blank" class="btn btn-success el-button el-button--success" id="submitpay">立即支付</a>"""

    private fun env(code: String, data: JsonObject, status: Int = 200) =
        ApiEnvelope(status = status, msg = "ok", data = data, code = code)

    private fun payData(html: String) = JsonObject().apply { addProperty("html", html) }

    @Test
    fun `POST pay 顶层 code Paid 直接判定支付完成`() {
        val out = CheckoutParser.payOutcomeEnvelope(env("Paid", payData("")))
        assertTrue(out.paid)
        assertNull(out.payUrl)
        assertTrue(out.payHtml.isEmpty())
    }

    @Test
    fun `POST pay 顶层 code Unpaid 保留站点原始 html 与真实收银台链接`() {
        val out = CheckoutParser.payOutcomeEnvelope(env("Unpaid", payData(cashierHtml)))
        assertFalse(out.paid)
        assertEquals(cashierHtml, out.payHtml)
        assertEquals("立即支付", out.payButtonLabel)
        assertTrue(out.payUrl!!.startsWith("https://zf.qingyayun.cn/submit.php"))
        assertTrue(out.payUrl!!.contains("out_trade_no=17946053744591105947"))
    }

    @Test
    fun `站点只给跳转按钮时不再伪造二维码内容与二维码图片`() {
        val out = CheckoutParser.payOutcomeEnvelope(env("Unpaid", payData(cashierHtml)))
        assertNull("收银台 href 不是支付码，绝不能当二维码内容", out.qrContent)
        assertNull("站点未给二维码图片，App 不本地造图", out.qrImageUrl)
    }

    @Test
    fun `旧签名 payOutcome 兼容 data 内的 code`() {
        val data = JsonObject().apply {
            addProperty("code", "Paid")
            addProperty("html", "")
        }
        assertTrue(CheckoutParser.payOutcome(data).paid)
    }

    @Test
    fun `支付状态读取响应体顶层 code`() {
        assertEquals(
            "PAID",
            CheckoutParser.payStatusEnvelope(env("Paid", JsonObject(), status = 200))
        )
        assertEquals(
            "UNPAID",
            CheckoutParser.payStatusEnvelope(env("Upaid", JsonObject(), status = 200))
        )
    }

    @Test
    fun `支付状态兼容旧版 data 内 code 与未知值`() {
        val legacy = env("", JsonObject().apply { addProperty("code", "Paid") })
        assertEquals("PAID", CheckoutParser.payStatusEnvelope(legacy))
        assertEquals(
            "UNPAID",
            CheckoutParser.payStatusEnvelope(env("", JsonObject()))
        )
    }

    @Test
    fun `订单真实有效期来自 order 字段而非写死的十分钟`() {
        val data = JsonParser.parseString(
            """{"order":{"unpaid_timeout":1,"remain_pay_time":287}}"""
        )
        val d = CheckoutParser.deadline(data)
        assertTrue(d.hasTimeout)
        assertEquals(287, d.remainSeconds)
    }

    @Test
    fun `订单未开启支付超时时明确标记无时限`() {
        val d = CheckoutParser.deadline(
            JsonParser.parseString("""{"order":{"unpaid_timeout":0,"remain_pay_time":0}}""")
        )
        assertFalse(d.hasTimeout)
        assertEquals(0, d.remainSeconds)
        assertFalse(CheckoutParser.deadline(null).hasTimeout)
    }

    @Test
    fun `有效期兼容 data 直接携带字段与负数兜底`() {
        val d = CheckoutParser.deadline(
            JsonParser.parseString("""{"unpaid_timeout":1,"remain_pay_time":-5}""")
        )
        assertTrue(d.hasTimeout)
        assertEquals(0, d.remainSeconds)
    }

    @Test
    fun `收银台链接优先取 submitphp 支付链接而非其它 href`() {
        val html = """<link href="https://cdn.example.com/pay.css"><a href="https://zf.qingyayun.cn/submit.php?pid=1">立即支付</a>"""
        val url = CheckoutParser.payOutcome(payData(html)).payUrl
        assertTrue(url!!.contains("submit.php"))
        assertFalse(url.contains("pay.css"))
    }

    @Test
    fun `站点真的给了二维码图片与支付码内容时原样提取`() {
        val withImg = """<div id="payBox"><img src="data:image/png;base64,AAA"></div>"""
        val img = CheckoutParser.payOutcome(payData(withImg))
        assertTrue(img.qrImageUrl!!.startsWith("data:image"))

        val withCode = """<script>var code_url = "weixin://wxpay/bizpayurl?pr=ABCDEF";</script>"""
        val code = CheckoutParser.payOutcome(payData(withCode))
        assertEquals("weixin://wxpay/bizpayurl?pr=ABCDEF", code.qrContent)
    }

    @Test
    fun `datahtml 为空时不产生任何支付入口`() {
        val out = CheckoutParser.payOutcomeEnvelope(env("Unpaid", JsonObject()))
        assertFalse(out.paid)
        assertNull(out.payUrl)
        assertTrue(out.payHtml.isEmpty())
    }
}
