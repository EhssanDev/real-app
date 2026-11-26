package dev.ehssan.parcels

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Lock icon drawn in code so it looks the same on every phone. */
private class LockGlyph(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = Color.WHITE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w * 0.065f
        p.style = Paint.Style.STROKE
        r.set(w * 0.37f, h * 0.22f, w * 0.63f, h * 0.52f)
        c.drawArc(r, 180f, 180f, false, p)
        c.drawLine(w * 0.37f, h * 0.37f, w * 0.37f, h * 0.48f, p)
        c.drawLine(w * 0.63f, h * 0.37f, w * 0.63f, h * 0.48f, p)
        p.style = Paint.Style.FILL
        r.set(w * 0.29f, h * 0.46f, w * 0.71f, h * 0.76f)
        c.drawRoundRect(r, w * 0.07f, w * 0.07f, p)
        p.color = Color.argb(200, 20, 30, 60)
        c.drawCircle(w * 0.5f, h * 0.58f, w * 0.045f, p)
        p.strokeWidth = w * 0.04f
        p.style = Paint.Style.STROKE
        c.drawLine(w * 0.5f, h * 0.59f, w * 0.5f, h * 0.67f, p)
    }
}

/** Full-screen PIN pad: unlock, verify, or choose a new PIN. Fingerprint / face is offered on unlock. */
class LockView(private val act: Activity) : FrameLayout(act) {

    private var step = 0 // 0 verify, 1 choose new, 2 confirm new
    private var first = ""
    private val entry = StringBuilder()
    private var onOk: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var allowBio = false
    private var cancellable = false
    private var bioActive = false

    private val titleView = TextView(act)
    private val msgView = TextView(act)
    private val dotsBox = LinearLayout(act)
    private val bioKey: TextView
    private val cancelView = TextView(act)

    private val keySize: Int

    private var submitView: TextView? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun circle(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        return d
    }

    private fun key(label: String, textSp: Float = 28f, onClick: () -> Unit): TextView {
        val t = TextView(act)
        t.text = label
        t.textSize = textSp
        t.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        val bg = StateListDrawable()
        bg.addState(intArrayOf(android.R.attr.state_pressed), circle(Color.rgb(74, 88, 128)))
        bg.addState(intArrayOf(), circle(Color.rgb(32, 38, 54)))
        t.background = bg
        t.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
        return t
    }

    private fun keyParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(keySize, keySize)
        p.setMargins(dp(7), dp(6), dp(7), dp(6))
        return p
    }

    private fun keyRow(): LinearLayout {
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        return row
    }

    init {
        // size the keys so the whole pad fits on small / short screens
        val dm = resources.displayMetrics
        val byWidth = (dm.widthPixels - dp(72)) / 3 - dp(14)
        val byHeight = (dm.heightPixels - dp(430)) / 4 - dp(12)
        keySize = minOf(dp(78), byWidth, byHeight).coerceAtLeast(dp(52))

        val bgd = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(12, 16, 28), Color.rgb(20, 26, 46))
        )
        background = bgd
        isClickable = true
        isFocusable = true
        visibility = GONE

        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(0, dp(12), 0, dp(12))
        addView(col, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val badge = FrameLayout(act)
        val g = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(79, 124, 255), Color.rgb(20, 184, 166))
        )
        g.shape = GradientDrawable.OVAL
        badge.background = g
        val glyph = LockGlyph(act)
        badge.addView(glyph, LayoutParams(dp(72), dp(72)))
        col.addView(badge, LinearLayout.LayoutParams(dp(72), dp(72)))

        titleView.textSize = 20f
        titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.setTextColor(Color.WHITE)
        titleView.gravity = Gravity.CENTER
        titleView.setPadding(dp(24), dp(16), dp(24), dp(4))
        col.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        msgView.textSize = 13f
        msgView.setTextColor(Color.rgb(255, 140, 140))
        msgView.gravity = Gravity.CENTER
        msgView.minHeight = dp(22)
        col.addView(msgView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dotsBox.orientation = LinearLayout.HORIZONTAL
        dotsBox.gravity = Gravity.CENTER
        col.addView(dotsBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))

        for (r in arrayOf("123", "456", "789")) {
            val row = keyRow()
            for (ch in r) row.addView(key(ch.toString()) { addDigit(ch) }, keyParams())
            col.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val last = keyRow()
        bioKey = key("👆", 24f) { promptBio() }
        last.addView(bioKey, keyParams())
        last.addView(key("0") { addDigit('0') }, keyParams())
        last.addView(key("⌫", 24f) { backspace() }, keyParams())
        col.addView(last, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val submit = TextView(act)
        submit.text = L.t("تأكيد")
        submitView = submit
        submit.textSize = 17f
        submit.typeface = Typeface.DEFAULT_BOLD
        submit.gravity = Gravity.CENTER
        submit.setTextColor(Color.WHITE)
        val sb = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(79, 124, 255), Color.rgb(54, 160, 235))
        )
        sb.cornerRadius = dp(26).toFloat()
        submit.background = sb
        submit.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            submit()
        }
        col.addView(submit, LinearLayout.LayoutParams(keySize * 3 + dp(14 * 3), dp(52)).also { it.topMargin = dp(14) })

        cancelView.text = L.t("إلغاء")
        cancelView.textSize = 15f
        cancelView.gravity = Gravity.CENTER
        cancelView.setTextColor(Color.rgb(170, 178, 192))
        cancelView.setPadding(dp(24), dp(12), dp(24), dp(12))
        cancelView.setOnClickListener {
            val cb = onCancel
            close()
            cb?.invoke()
        }
        col.addView(cancelView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    // ---------- entry points ----------

    fun showUnlock(
        title: String,
        withBio: Boolean,
        canCancel: Boolean,
        ok: () -> Unit,
        cancel: (() -> Unit)?,
        autoBio: Boolean
    ) {
        step = 0
        first = ""
        entry.setLength(0)
        submitView?.text = L.t("تأكيد")
        cancelView.text = L.t("إلغاء")
        titleView.text = L.t(title)
        msgView.text = ""
        allowBio = withBio && Bio.available(act)
        cancellable = canCancel
        onOk = ok
        onCancel = cancel
        refresh()
        visibility = VISIBLE
        bringToFront()
        if (autoBio) postDelayed({ maybeAutoBio() }, 250)
    }

    fun showSetup(canCancel: Boolean, ok: () -> Unit, cancel: (() -> Unit)?) {
        step = 1
        first = ""
        entry.setLength(0)
        submitView?.text = L.t("تأكيد")
        cancelView.text = L.t("إلغاء")
        titleView.text = L.t("اختر رمز قفل جديد")
        msgView.setTextColor(Color.rgb(170, 178, 192))
        msgView.text = L.t("٤ أرقام على الأقل")
        allowBio = false
        cancellable = canCancel
        onOk = ok
        onCancel = cancel
        refresh()
        visibility = VISIBLE
        bringToFront()
    }

    val isShowing: Boolean get() = visibility == VISIBLE

    fun maybeAutoBio() {
        if (visibility == VISIBLE && step == 0 && allowBio && !bioActive) promptBio()
    }

    // ---------- internals ----------

    private fun close() {
        visibility = GONE
        entry.setLength(0)
        onOk = null
        onCancel = null
    }

    private fun refresh() {
        dotsBox.removeAllViews()
        val n = maxOf(4, entry.length)
        val size = dp(14)
        for (i in 0 until n) {
            val d = View(act)
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            if (i < entry.length) {
                g.setColor(Color.rgb(120, 170, 255))
            } else {
                g.setColor(Color.TRANSPARENT)
                g.setStroke(dp(2), Color.rgb(90, 102, 135))
            }
            d.background = g
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(dp(7), 0, dp(7), 0)
            dotsBox.addView(d, lp)
        }
        bioKey.visibility = if (allowBio && step == 0) VISIBLE else INVISIBLE
        cancelView.visibility = if (cancellable) VISIBLE else INVISIBLE
    }

    private fun addDigit(c: Char) {
        if (entry.length >= 12) return
        entry.append(c)
        msgView.text = ""
        refresh()
    }

    private fun backspace() {
        if (entry.isNotEmpty()) entry.setLength(entry.length - 1)
        refresh()
    }

    private fun shake() {
        val d = dp(10).toFloat()
        dotsBox.animate().translationX(-d).setDuration(50).withEndAction {
            dotsBox.animate().translationX(d).setDuration(70).withEndAction {
                dotsBox.animate().translationX(-d / 2).setDuration(60).withEndAction {
                    dotsBox.animate().translationX(0f).setDuration(50).start()
                }.start()
            }.start()
        }.start()
    }

    private fun fail(message: String) {
        msgView.setTextColor(Color.rgb(255, 140, 140))
        msgView.text = message
        entry.setLength(0)
        refresh()
        shake()
        performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    private fun succeed() {
        val cb = onOk
        close()
        cb?.invoke()
    }

    private fun submit() {
        val pin = entry.toString()
        when (step) {
            0 -> {
                val wait = Pin.lockoutSeconds(act)
                if (wait > 0) {
                    fail(L.wait(wait))
                    return
                }
                if (Pin.verify(act, pin)) succeed() else fail(L.t("الرمز غير صحيح"))
            }

            1 -> {
                if (pin.length < 4) {
                    fail(L.t("٤ أرقام على الأقل"))
                    return
                }
                first = pin
                entry.setLength(0)
                step = 2
                titleView.text = L.t("أعد إدخال الرمز")
                msgView.text = ""
                refresh()
            }

            2 -> {
                if (pin == first) {
                    Pin.set(act, pin)
                    succeed()
                } else {
                    step = 1
                    first = ""
                    titleView.text = L.t("اختر رمز قفل جديد")
                    fail(L.t("الرمزان غير متطابقين"))
                }
            }
        }
    }

    private fun promptBio() {
        if (!allowBio || bioActive) return
        bioActive = true
        Bio.prompt(
            act, titleView.text.toString(),
            {
                bioActive = false
                if (visibility == VISIBLE && step == 0) succeed()
            },
            { bioActive = false }
        )
    }
}
