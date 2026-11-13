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
 * 本地生成二维码位图（v1.4.9）。
 *
 * 等待支付页不再自动跳网页，而是把支付跳转链接（payUrl，即支付宝/易支付深链）
 * 直接编码成二维码展示在「打开支付宝」按钮上方，用户扫码或点按钮都能拉起支付。
 * 纯设备端计算，不依赖任何外部服务，断网也可生成。
 */
object QrBitmap {
    /** 把内容编码为高清二维码位图；内容为空或编码失败时返回 null。 */
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
