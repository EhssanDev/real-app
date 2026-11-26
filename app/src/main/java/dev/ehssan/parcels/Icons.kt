package dev.ehssan.parcels

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

object Ic {
    const val BACK = 0
    const val FWD = 1
    const val HOME = 2
    const val MENU = 3
    const val RELOAD = 4
    const val CLOSE = 5
    const val PLUS = 6
    const val TABS = 7
    const val SHIELD = 8
    const val LOCK = 9
    const val STAR = 10
    const val SEARCH = 11
    const val CLOCK = 12
    const val SHARE = 13
    const val MONITOR = 14
    const val MOON = 15
    const val GEAR = 16
    const val DOWNLOAD = 17
    const val PRINT = 18
    const val TRASH = 19
    const val COPY = 20
    const val UP = 21
    const val DOWN = 22
    const val EXTERNAL = 23
}

class IconView(ctx: Context, var kind: Int, var tint: Int = Color.WHITE) : View(ctx) {
    var iconDp = 24
    var filled = false
    var label = ""

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    private fun poly(c: Canvas, vararg pts: Float) {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density * iconDp / 24f
        c.save()
        c.translate((width - 24f * d) / 2f, (height - 24f * d) / 2f)
        c.scale(d, d)
        p.color = tint
        p.strokeWidth = 1.9f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.STROKE

        when (kind) {
            Ic.BACK -> poly(c, 15f, 5f, 8f, 12f, 15f, 19f)
            Ic.FWD -> poly(c, 9f, 5f, 16f, 12f, 9f, 19f)
            Ic.UP -> poly(c, 5f, 15f, 12f, 8f, 19f, 15f)
            Ic.DOWN -> poly(c, 5f, 9f, 12f, 16f, 19f, 9f)
            Ic.HOME -> {
                poly(c, 3.5f, 11f, 12f, 3.8f, 20.5f, 11f)
                poly(c, 6f, 9.5f, 6f, 20f, 18f, 20f, 18f, 9.5f)
            }
            Ic.MENU -> {
                p.style = Paint.Style.FILL
                c.drawCircle(12f, 5f, 1.9f, p)
                c.drawCircle(12f, 12f, 1.9f, p)
                c.drawCircle(12f, 19f, 1.9f, p)
            }
            Ic.RELOAD -> {
                r.set(5f, 5f, 19f, 19f)
                c.drawArc(r, -45f, 270f, false, p)
                poly(c, 4.5f, 5.2f, 8.2f, 5.2f, 8.2f, 8.9f)
            }
            Ic.CLOSE -> {
                poly(c, 6f, 6f, 18f, 18f)
                poly(c, 18f, 6f, 6f, 18f)
            }
            Ic.PLUS -> {
                poly(c, 12f, 5f, 12f, 19f)
                poly(c, 5f, 12f, 19f, 12f)
            }
            Ic.TABS -> {
                c.drawRoundRect(4.5f, 5f, 19.5f, 19f, 3.5f, 3.5f, p)
                tp.color = tint
                tp.typeface = Typeface.DEFAULT_BOLD
                tp.textAlign = Paint.Align.CENTER
                tp.textSize = if (label.length > 2) 7.5f else 10f
                c.drawText(label, 12f, if (label.length > 2) 14.6f else 15.5f, tp)
            }
            Ic.SHIELD -> {
                path.reset()
                path.moveTo(12f, 3f)
                path.lineTo(19f, 5.8f)
                path.lineTo(19f, 11.5f)
                path.cubicTo(19f, 16f, 16f, 19.5f, 12f, 21f)
                path.cubicTo(8f, 19.5f, 5f, 16f, 5f, 11.5f)
                path.lineTo(5f, 5.8f)
                path.close()
                if (filled) p.style = Paint.Style.FILL_AND_STROKE
                c.drawPath(path, p)
            }
            Ic.LOCK -> {
                if (filled) p.style = Paint.Style.FILL_AND_STROKE
                c.drawRoundRect(6f, 11f, 18f, 20.5f, 2.5f, 2.5f, p)
                p.style = Paint.Style.STROKE
                r.set(8.5f, 4f, 15.5f, 13f)
                c.drawArc(r, 180f, 180f, false, p)
                poly(c, 8.5f, 8.5f, 8.5f, 11f)
                poly(c, 15.5f, 8.5f, 15.5f, 11f)
            }
            Ic.STAR -> {
                path.reset()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val rad = if (i % 2 == 0) 9f else 4f
                    val x = 12f + (Math.cos(a) * rad).toFloat()
                    val y = 12.8f + (Math.sin(a) * rad).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                if (filled) p.style = Paint.Style.FILL_AND_STROKE
                c.drawPath(path, p)
            }
            Ic.SEARCH -> {
                c.drawCircle(10.8f, 10.8f, 6.2f, p)
                poly(c, 15.4f, 15.4f, 20f, 20f)
            }
            Ic.CLOCK -> {
                c.drawCircle(12f, 12f, 9f, p)
                poly(c, 12f, 7f, 12f, 12f, 15.5f, 14f)
            }
            Ic.SHARE -> {
                c.drawCircle(18f, 5.5f, 2.6f, p)
                c.drawCircle(6f, 12f, 2.6f, p)
                c.drawCircle(18f, 18.5f, 2.6f, p)
                poly(c, 8.3f, 10.8f, 15.7f, 6.7f)
                poly(c, 8.3f, 13.2f, 15.7f, 17.3f)
            }
            Ic.MONITOR -> {
                c.drawRoundRect(3f, 4.5f, 21f, 16f, 2f, 2f, p)
                poly(c, 8f, 20f, 16f, 20f)
                poly(c, 12f, 16f, 12f, 20f)
            }
            Ic.MOON -> {
                val a = Path()
                a.addCircle(12f, 12f, 8.5f, Path.Direction.CW)
                val b = Path()
                b.addCircle(16.5f, 8.5f, 7f, Path.Direction.CW)
                a.op(b, Path.Op.DIFFERENCE)
                if (filled) p.style = Paint.Style.FILL_AND_STROKE
                c.drawPath(a, p)
            }
            Ic.GEAR -> {
                c.drawCircle(12f, 12f, 3f, p)
                c.drawCircle(12f, 12f, 6.8f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val cs = Math.cos(a).toFloat()
                    val sn = Math.sin(a).toFloat()
                    c.drawLine(12f + cs * 8.6f, 12f + sn * 8.6f, 12f + cs * 10.6f, 12f + sn * 10.6f, p)
                }
            }
            Ic.DOWNLOAD -> {
                poly(c, 12f, 4f, 12f, 15f)
                poly(c, 7f, 10.5f, 12f, 15.5f, 17f, 10.5f)
                poly(c, 5f, 20f, 19f, 20f)
            }
            Ic.PRINT -> {
                c.drawRect(7f, 3.5f, 17f, 9f, p)
                c.drawRoundRect(4f, 9f, 20f, 17f, 2f, 2f, p)
                c.drawRect(7f, 14f, 17f, 20.5f, p)
            }
            Ic.TRASH -> {
                poly(c, 4f, 7f, 20f, 7f)
                poly(c, 6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f)
                poly(c, 9.5f, 4f, 14.5f, 4f)
            }
            Ic.COPY -> {
                c.drawRoundRect(5f, 5f, 15f, 15f, 2f, 2f, p)
                c.drawRoundRect(9f, 9f, 19f, 19f, 2f, 2f, p)
            }
            Ic.EXTERNAL -> {
                poly(c, 14f, 4f, 20f, 4f, 20f, 10f)
                poly(c, 20f, 4f, 11f, 13f)
                poly(c, 18f, 14f, 18f, 19f, 5f, 19f, 5f, 6f, 10f, 6f)
            }
        }
        c.restore()
    }
}
