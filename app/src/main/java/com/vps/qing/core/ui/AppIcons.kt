package com.vps.qing.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 全应用图标：按「字段大意」用 SVG path 自绘，不依赖 material-icons 或其它图标库。
 * 每个图标都是一段手写矢量路径（描边卡通风格，圆头圆角），通过 Icon(tint=) 着色。
 *
 * 构造方式与 SVG <path d="..."> 一一对应：moveTo/lineTo/cubicTo/close。
 * 圆与圆角矩形用贝塞尔近似（kappa = 0.5523）画，避免依赖平台 arc 指令。
 */
object AppIcons {

    private val INK = SolidColor(Color.Black)

    private fun vector(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    private fun ImageVector.Builder.s(block: PathBuilder.() -> Unit) =
        path(
            stroke = INK, strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block
        )

    private fun ImageVector.Builder.f(block: PathBuilder.() -> Unit) =
        path(fill = INK, pathBuilder = block)

    private const val K = 0.5523f
    private fun PathBuilder.oval(cx: Float, cy: Float, rx: Float, ry: Float = rx) {
        moveTo(cx + rx, cy)
        curveTo(cx + rx, cy + K * ry, cx + K * rx, cy + ry, cx, cy + ry)
        curveTo(cx - K * rx, cy + ry, cx - rx, cy + K * ry, cx - rx, cy)
        curveTo(cx - rx, cy - K * ry, cx - K * rx, cy - ry, cx, cy - ry)
        curveTo(cx + K * rx, cy - ry, cx + rx, cy - K * ry, cx + rx, cy)
        close()
    }

    private fun PathBuilder.rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        lineTo(r - rad, t)
        curveTo(r - rad + K * rad, t, r, t + rad - K * rad, r, t + rad)
        lineTo(r, b - rad)
        curveTo(r, b - rad + K * rad, r - rad + K * rad, b, r - rad, b)
        lineTo(l + rad, b)
        curveTo(l + rad - K * rad, b, l, b - rad + K * rad, l, b - rad)
        lineTo(l, t + rad)
        curveTo(l, t + rad - K * rad, l + rad - K * rad, t, l + rad, t)
        close()
    }

    // ---- 底栏 ----
    val Dashboard: ImageVector by lazy {
        vector("Dashboard") {
            s {
                rrect(3f, 3f, 10.5f, 10.5f, 2.6f)
                rrect(13.5f, 3f, 21f, 10.5f, 2.6f)
                rrect(3f, 13.5f, 10.5f, 21f, 2.6f)
                rrect(13.5f, 13.5f, 21f, 21f, 2.6f)
            }
        }
    }
    val Shop: ImageVector by lazy {
        vector("Shop") {
            s {
                moveTo(4.5f, 8f); lineTo(19.5f, 8f); lineTo(18.4f, 20.5f); lineTo(5.6f, 20.5f); close()
                moveTo(8.5f, 8f); curveTo(8.5f, 4.6f, 15.5f, 4.6f, 15.5f, 8f)
            }
        }
    }
    val Message: ImageVector by lazy {
        vector("Message") {
            s {
                rrect(3f, 4.5f, 21f, 17f, 4f)
                moveTo(8f, 17f); lineTo(7f, 21f); lineTo(11.5f, 17f)
            }
            f { oval(8.5f, 10.7f, 1f); oval(12f, 10.7f, 1f); oval(15.5f, 10.7f, 1f) }
        }
    }
    val Profile: ImageVector by lazy {
        vector("Profile") {
            s {
                oval(12f, 8f, 3.6f)
                moveTo(5f, 20f)
                curveTo(5f, 15.5f, 8.5f, 13.4f, 12f, 13.4f)
                curveTo(15.5f, 13.4f, 19f, 15.5f, 19f, 20f)
            }
        }
    }

    // ---- 通用操作 ----
    val Cart: ImageVector by lazy {
        vector("Cart") {
            s {
                moveTo(2.5f, 4f); lineTo(5f, 4f); lineTo(7f, 15f); lineTo(19f, 15f)
                lineTo(21f, 7f); lineTo(6.3f, 7f)
                oval(8.5f, 19f, 1.6f); oval(17.5f, 19f, 1.6f)
            }
        }
    }
    val Refresh: ImageVector by lazy {
        vector("Refresh") {
            s {
                moveTo(4.6f, 9f)
                curveTo(5.6f, 6.1f, 8.5f, 4f, 12f, 4f)
                curveTo(16.5f, 4f, 20f, 7.5f, 20f, 12f)
                curveTo(20f, 16.5f, 16.5f, 20f, 12f, 20f)
                curveTo(8.1f, 20f, 5.1f, 17.6f, 4.2f, 14f)
                moveTo(4f, 4.4f); lineTo(4.6f, 9.2f); lineTo(9.4f, 8.6f)
            }
        }
    }
    val Back: ImageVector by lazy {
        vector("Back") { s { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) } }
    }
    val Forward: ImageVector by lazy {
        vector("Forward") { s { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) } }
    }
    val Menu: ImageVector by lazy {
        vector("Menu") {
            s { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(20f, 18f) }
        }
    }
    val PanelOpen: ImageVector by lazy {
        vector("PanelOpen") {
            s {
                rrect(3f, 4f, 21f, 20f, 2.6f)
                moveTo(9.5f, 4f); lineTo(9.5f, 20f)
                moveTo(14f, 9.5f); lineTo(17.5f, 12f); lineTo(14f, 14.5f)
            }
        }
    }
    val PanelClose: ImageVector by lazy {
        vector("PanelClose") {
            s {
                rrect(3f, 4f, 21f, 20f, 2.6f)
                moveTo(9.5f, 4f); lineTo(9.5f, 20f)
                moveTo(17.5f, 9.5f); lineTo(14f, 12f); lineTo(17.5f, 14.5f)
            }
        }
    }
    val Plus: ImageVector by lazy {
        vector("Plus") { s { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } }
    }
    val Minus: ImageVector by lazy {
        vector("Minus") { s { moveTo(5f, 12f); lineTo(19f, 12f) } }
    }
    val Close: ImageVector by lazy {
        vector("Close") { s { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } }
    }
    val Check: ImageVector by lazy {
        vector("Check") { s { moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(20f, 6.5f) } }
    }
    val ChevronDown: ImageVector by lazy {
        vector("ChevronDown") { s { moveTo(6f, 9.5f); lineTo(12f, 15f); lineTo(18f, 9.5f) } }
    }
    val Trash: ImageVector by lazy {
        vector("Trash") {
            s {
                moveTo(4f, 7f); lineTo(20f, 7f)
                moveTo(9f, 7f); lineTo(9f, 4.5f); lineTo(15f, 4.5f); lineTo(15f, 7f)
                moveTo(6f, 7f); lineTo(7f, 20.5f); lineTo(17f, 20.5f); lineTo(18f, 7f)
                moveTo(10f, 11f); lineTo(10f, 17f); moveTo(14f, 11f); lineTo(14f, 17f)
            }
        }
    }

    // ---- 仪表盘 / 服务 ----
    val Cloud: ImageVector by lazy {
        vector("Cloud") {
            s {
                moveTo(7f, 18.5f)
                curveTo(3.6f, 18.5f, 3.2f, 13.2f, 6.6f, 12.2f)
                curveTo(6.4f, 8.2f, 10.9f, 6.6f, 13f, 9.6f)
                curveTo(14.1f, 8.1f, 17.9f, 8.6f, 18.6f, 12.2f)
                curveTo(21.6f, 12.2f, 21.4f, 18.5f, 18f, 18.5f)
                close()
            }
        }
    }
    val Server: ImageVector by lazy {
        vector("Server") {
            s {
                rrect(3.5f, 3.5f, 20.5f, 9f, 2f)
                rrect(3.5f, 10f, 20.5f, 15.5f, 2f)
                rrect(3.5f, 16.5f, 20.5f, 22f, 2f)
            }
            f { oval(7f, 6.2f, 1f); oval(7f, 12.7f, 1f); oval(7f, 19.2f, 1f) }
        }
    }
    val Cdn: ImageVector by lazy {
        vector("Cdn") {
            s {
                moveTo(13f, 2.5f); lineTo(6f, 13f); lineTo(11f, 13f)
                lineTo(9.5f, 21.5f); lineTo(18.5f, 10f); lineTo(13.5f, 10f); close()
            }
        }
    }
    val Gift: ImageVector by lazy {
        vector("Gift") {
            s {
                rrect(3.5f, 9f, 20.5f, 21f, 1.6f)
                moveTo(3f, 9f); lineTo(21f, 9f); moveTo(12f, 9f); lineTo(12f, 21f)
                moveTo(12f, 9f); curveTo(8f, 9f, 7f, 4f, 9.5f, 4f); curveTo(11f, 4f, 12f, 6f, 12f, 9f)
                moveTo(12f, 9f); curveTo(16f, 9f, 17f, 4f, 14.5f, 4f); curveTo(13f, 4f, 12f, 6f, 12f, 9f)
            }
        }
    }
    val Category: ImageVector by lazy {
        vector("Category") {
            s { moveTo(9f, 6f); lineTo(20f, 6f); moveTo(9f, 12f); lineTo(20f, 12f); moveTo(9f, 18f); lineTo(20f, 18f) }
            f { oval(5.5f, 6f, 1.4f); oval(5.5f, 12f, 1.4f); oval(5.5f, 18f, 1.4f) }
        }
    }
    val Bell: ImageVector by lazy {
        vector("Bell") {
            s {
                moveTo(6f, 17f)
                curveTo(6f, 17f, 7f, 16f, 7f, 12f)
                curveTo(7f, 8f, 9f, 6f, 12f, 6f)
                curveTo(15f, 6f, 17f, 8f, 17f, 12f)
                curveTo(17f, 16f, 18f, 17f, 18f, 17f); close()
                moveTo(10f, 20f); curveTo(10f, 20f, 10.5f, 21.5f, 12f, 21.5f)
                curveTo(13.5f, 21.5f, 14f, 20f, 14f, 20f)
            }
        }
    }

    // ---- 个人中心 / 账号服务 ----
    val Order: ImageVector by lazy {
        vector("Order") {
            s {
                rrect(4f, 3f, 20f, 21f, 2.6f)
                moveTo(8f, 8f); lineTo(16f, 8f)
                moveTo(8f, 12f); lineTo(16f, 12f)
                moveTo(8f, 16f); lineTo(13f, 16f)
            }
        }
    }
    val Wallet: ImageVector by lazy {
        vector("Wallet") {
            s {
                rrect(3f, 6f, 21f, 19f, 2.6f)
                moveTo(3f, 10f); lineTo(21f, 10f)
                rrect(15f, 12.5f, 20f, 16.5f, 1.6f)
            }
        }
    }
    val Ticket: ImageVector by lazy {
        vector("Ticket") {
            s {
                moveTo(3f, 7f); lineTo(21f, 7f); lineTo(21f, 10.5f)
                curveTo(19.4f, 10.5f, 19.4f, 13.5f, 21f, 13.5f)
                lineTo(21f, 17f); lineTo(3f, 17f); lineTo(3f, 13.5f)
                curveTo(4.6f, 13.5f, 4.6f, 10.5f, 3f, 10.5f); close()
                moveTo(9f, 7f); lineTo(9f, 17f)
            }
        }
    }
    val Shield: ImageVector by lazy {
        vector("Shield") {
            s {
                moveTo(12f, 3f); lineTo(20f, 6f); lineTo(20f, 12f)
                curveTo(20f, 17f, 16f, 20f, 12f, 21.5f)
                curveTo(8f, 20f, 4f, 17f, 4f, 12f); lineTo(4f, 6f); close()
            }
        }
    }
    val Captcha: ImageVector by lazy {
        vector("Captcha") {
            s {
                moveTo(12f, 3f); lineTo(20f, 6f); lineTo(20f, 12f)
                curveTo(20f, 17f, 16f, 20f, 12f, 21.5f)
                curveTo(8f, 20f, 4f, 17f, 4f, 12f); lineTo(4f, 6f); close()
                moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(15.5f, 9.5f)
            }
        }
    }
    val Lock: ImageVector by lazy {
        vector("Lock") {
            s {
                rrect(5f, 10.5f, 19f, 20.5f, 2.6f)
                moveTo(8f, 10.5f); lineTo(8f, 7.5f)
                curveTo(8f, 4.4f, 16f, 4.4f, 16f, 7.5f); lineTo(16f, 10.5f)
            }
            f { oval(12f, 15.5f, 1.2f) }
        }
    }
    val Settings: ImageVector by lazy {
        vector("Settings") {
            s { moveTo(4f, 7f); lineTo(20f, 7f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 17f); lineTo(20f, 17f) }
            s { oval(9f, 7f, 1.8f); oval(15f, 12f, 1.8f); oval(10.5f, 17f, 1.8f) }
        }
    }
    val Info: ImageVector by lazy {
        vector("Info") {
            s { oval(12f, 12f, 9f); moveTo(12f, 11f); lineTo(12f, 16.5f) }
            f { oval(12f, 7.8f, 1f) }
        }
    }
    val Logout: ImageVector by lazy {
        vector("Logout") {
            s {
                moveTo(11f, 4f); lineTo(5f, 4f); lineTo(5f, 20f); lineTo(11f, 20f)
                moveTo(14f, 12f); lineTo(21f, 12f)
                moveTo(18f, 9f); lineTo(21f, 12f); lineTo(18f, 15f)
            }
        }
    }
    val Mail: ImageVector by lazy {
        vector("Mail") {
            s {
                rrect(3f, 5f, 21f, 19f, 2.6f)
                moveTo(3.6f, 6.6f); lineTo(12f, 13f); lineTo(20.4f, 6.6f)
            }
        }
    }
    val Tag: ImageVector by lazy {
        vector("Tag") {
            s { moveTo(4f, 12f); lineTo(12f, 4f); lineTo(20f, 4f); lineTo(20f, 12f); lineTo(12f, 20f); close() }
            f { oval(16f, 8f, 1.3f) }
        }
    }
    val Theme: ImageVector by lazy {
        vector("Theme") {
            s {
                moveTo(12f, 3f)
                curveTo(17f, 3f, 21f, 7f, 21f, 12f)
                curveTo(21f, 16.2f, 18f, 20f, 12f, 21f)
                curveTo(6f, 20f, 3f, 16.5f, 3f, 12f)
                curveTo(3f, 7f, 7f, 3f, 12f, 3f); close()
                moveTo(12f, 3f); lineTo(12f, 21f)
            }
        }
    }
    val Sparkle: ImageVector by lazy {
        vector("Sparkle") {
            s {
                moveTo(12f, 2.5f)
                curveTo(13f, 8f, 16f, 11f, 21.5f, 12f)
                curveTo(16f, 13f, 13f, 16f, 12f, 21.5f)
                curveTo(11f, 16f, 8f, 13f, 2.5f, 12f)
                curveTo(8f, 11f, 11f, 8f, 12f, 2.5f); close()
            }
        }
    }
    val EmptyBox: ImageVector by lazy {
        vector("EmptyBox") {
            s {
                moveTo(3.5f, 8f); lineTo(12f, 4f); lineTo(20.5f, 8f); lineTo(12f, 12f); close()
                moveTo(3.5f, 8f); lineTo(3.5f, 17f); lineTo(12f, 21f)
                lineTo(20.5f, 17f); lineTo(20.5f, 8f)
                moveTo(12f, 12f); lineTo(12f, 21f)
            }
        }
    }
    val Sliders: ImageVector by lazy {
        vector("Sliders") {
            s {
                moveTo(4f, 6f); lineTo(20f, 6f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 18f); lineTo(20f, 18f)
            }
            s { oval(8.5f, 6f, 2f); oval(15f, 12f, 2f); oval(10.5f, 18f, 2f) }
        }
    }

    /**
     * 根据分类/菜单名的「字段大意」挑一个自绘图标，呼应需求里「按字段大意自绘图标」。
     * 关键字命中不到时回退到云图标。
     */
    fun forCategory(name: String): ImageVector {
        val n = name.lowercase()
        return when {
            "cdn" in n || "加速" in n || "高防" in n -> Cdn
            "云" in n || "cloud" in n || "服务器" in n || "vps" in n -> Cloud
            "活动" in n || "特惠" in n || "双11" in n || "惠" in n -> Gift
            "轻量" in n || "大带宽" in n || "带宽" in n || "加盟" in n -> Server
            else -> Tag
        }
    }
}
