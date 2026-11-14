package com.vps.qing.core.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 本地生成二维码位图（v1.4.9 建立 / v1.4.18 用途纠正）。
 *
 * v1.4.18 重要修正：**只允许编码站点真实给出的支付码内容**
 * （payHtml 里的 code_url / alipays:// / weixin:// 等），绝不能把收银台跳转链接
 * （如易支付 submit.php?...）当支付码内容。后者只是网页端「立即支付」按钮的 href，
 * 扫出来是一个网页链接，不是网页端给用户的支付码 —— 这正是用户反馈的问题根因。
 * 站点若未返回二维码内容，支付页改为原样渲染站点 data.html，不再本地伪造二维码。
 *
 * 纯设备端计算，不依赖任何外部服务，断网也可生成。
 */
object QrBitmap {
    /** 把**站点真实支付码内容**编码为高清二维码位图；为空或失败返回 null。 */
    fun encode(content: String?, sizePx: Int = 480): ImageBitmap? {
        val text = content?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
