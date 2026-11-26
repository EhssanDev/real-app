package dev.ehssan.parcels

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Wraps children onto new rows when they run out of width. */
class FlowLayout(ctx: Context) : ViewGroup(ctx) {
    var hGap = 0
    var vGap = 0

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxW = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            measureChild(c, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            val w = c.measuredWidth
            val h = c.measuredHeight
            if (x > 0 && x + w > maxW) {
                x = 0
                y += rowH + vGap
                rowH = 0
            }
            x += w + hGap
            rowH = maxOf(rowH, h)
        }
        val total = y + rowH + paddingTop + paddingBottom
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(total, heightSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            val w = c.measuredWidth
            val h = c.measuredHeight
            if (x > 0 && x + w > maxW) {
                x = 0
                y += rowH + vGap
                rowH = 0
            }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + w, paddingTop + y + h)
            x += w + hGap
            rowH = maxOf(rowH, h)
        }
    }
}
