package dev.ehssan.parcels

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Draft {
    var editId = 0L
    var created = 0L
    var delivered = 0L
    var name = ""
    var tracking = ""
    var shelf = ""
    var note = ""
    var photo = ""
    var label = ""
    var gid = ""
    var by = ""
    var lines: List<String> = emptyList()
    var status = ""
    val newFiles = ArrayList<String>()
    val oldFiles = ArrayList<String>()
}

class MainActivity : Activity() {

    companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val REQ_PHOTO = 21
        const val REQ_DOC = 22
        const val AUTH = "dev.ehssan.parcels.files"
        const val DAY = 24L * 3600 * 1000
    }

    private val bg = Color.rgb(18, 20, 26)
    private val card = Color.rgb(30, 34, 44)
    private val chipBg = Color.rgb(44, 49, 62)
    private val textMain = Color.rgb(237, 239, 244)
    private val textSub = Color.rgb(154, 163, 178)
    private val amber = Color.rgb(245, 166, 35)
    private val green = Color.rgb(52, 211, 153)
    private val red = Color.rgb(248, 113, 113)
    private val dark = Color.rgb(28, 24, 12)

    private val db by lazy { Db(this) }
    private lateinit var content: FrameLayout
    private lateinit var lock: LockView

    private var locked = true
    private var skipLock = false
    private var backAction: (() -> Unit)? = null

    private var homeTab = 0
    private var homeQuery = ""
    private var homeRefresh: (() -> Unit)? = null
    private var syncState = ""

    private var draft = Draft()
    private var captureTarget = 0
    private var nameEt: EditText? = null
    private var trackEt: EditText? = null
    private var shelfEt: EditText? = null
    private var noteEt: EditText? = null
    private var lastField: EditText? = null
    private var photoBox: LinearLayout? = null
    private var chipsBox: FlowLayout? = null
    private var chipsTitle: TextView? = null
    private var statusTv: TextView? = null

    private val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    // ---------- helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun rounded(color: Int, r: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(r).toFloat()
        return d
    }

    private fun lin(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun chip(text: String, color: Int = chipBg, textColor: Int = Color.WHITE, onClick: () -> Unit): TextView {
        val t = label(text, 13f, textColor, true)
        t.gravity = Gravity.CENTER
        t.background = rounded(color, 12)
        t.setPadding(dp(14), dp(9), dp(14), dp(9))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun primary(text: String, onClick: () -> Unit): TextView {
        val t = label(text, 16f, dark, true)
        t.gravity = Gravity.CENTER
        t.background = rounded(amber, 16)
        t.setPadding(dp(18), dp(15), dp(18), dp(15))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun secondary(text: String, onClick: () -> Unit): TextView {
        val t = label(text, 15f, Color.WHITE, true)
        t.gravity = Gravity.CENTER
        t.background = rounded(chipBg, 16)
        t.setPadding(dp(18), dp(14), dp(18), dp(14))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun icon(kind: Int, sizeDp: Int, color: Int, onClick: (() -> Unit)? = null): IconView {
        val v = IconView(this, kind, color)
        v.iconDp = sizeDp
        if (onClick != null) v.setOnClickListener { onClick() }
        return v
    }

    private fun cfg() = getSharedPreferences("pd_cfg", Context.MODE_PRIVATE)
    private fun retentionDays() = cfg().getInt("retention", 14)
    private fun warnDays() = cfg().getInt("warn", 7)

    private fun ago(t: Long): String {
        val d = ((System.currentTimeMillis() - t) / DAY).toInt()
        return when (d) {
            0 -> "اليوم"
            1 -> "أمس"
            else -> "قبل $d أيام"
        }
    }

    private fun waitColor(t: Long): Int {
        val d = ((System.currentTimeMillis() - t) / DAY).toInt()
        val w = warnDays()
        return if (d >= w) red else if (d >= w - 2) amber else green
    }

    private fun purge() {
        val days = retentionDays()
        if (days <= 0) return
        for (p in db.purgeDelivered(System.currentTimeMillis() - days * DAY)) {
            deleteFiles(p)
            Sync.remove(this, p)
        }
    }

    private fun touch(p: Parcel) {
        p.updated = System.currentTimeMillis()
        db.update(p)
        Sync.push(this, p)
    }

    private fun wipe(p: Parcel) {
        deleteFiles(p)
        db.delete(p.id)
        Sync.remove(this, p)
    }

    private fun startSync() {
        Sync.onChange = { homeRefresh?.invoke() }
        Sync.onNewParcel = { p ->
            toast("شحنة جديدة: " + p.name + if (p.by.isNotEmpty()) " (" + p.by + ")" else "")
        }
        Sync.onState = { s ->
            syncState = s
        }
        Sync.start(this, db)
    }

    private fun deleteFiles(p: Parcel) {
        if (p.photo.isNotEmpty()) Img.file(this, p.photo).delete()
        if (p.label.isNotEmpty()) Img.file(this, p.label).delete()
    }

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        content = FrameLayout(this)
        content.setBackgroundColor(bg)
        lock = LockView(this)
        val root = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(lock, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        purge()
        showHome()
        showLock()
        if (Sync.enabled(this)) startSync() else if (!Sync.skipped(this)) showSyncSetup(false)
    }

    override fun onDestroy() {
        Sync.onChange = null
        Sync.onNewParcel = null
        Sync.onState = null
        Sync.stop()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        if (!skipLock) lockNow()
    }

    override fun onResume() {
        super.onResume()
        skipLock = false
        if (locked) {
            if (!lock.isShowing) showLock()
            lock.maybeAutoBio()
        }
    }

    private fun showLock() {
        locked = true
        if (!Pin.isSet(this)) {
            lock.showSetup(false, { locked = false }, null)
        } else {
            lock.showUnlock("افتح Parcel Desk", true, false, { locked = false }, null, true)
        }
    }

    private fun lockNow() {
        if (locked) return
        showLock()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (lock.isShowing) {
            moveTaskToBack(true)
            return
        }
        val b = backAction
        if (b != null) b() else moveTaskToBack(true)
    }

    private fun setScreen(v: View) {
        homeRefresh = null
        content.removeAllViews()
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // ---------- home ----------

    private fun showHome() {
        backAction = null
        nameEt = null
        val screen = FrameLayout(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), 0)

        val waiting = db.all(false)
        val overdue = waiting.count { (System.currentTimeMillis() - it.created) / DAY >= warnDays() }

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(label("Parcel Desk", 24f, Color.WHITE, true))
        val sub = label(
            "${waiting.size} شحنة بانتظار الاستلام" + if (overdue > 0) " · $overdue متأخرة" else "",
            13f, if (overdue > 0) red else textSub
        )
        titles.addView(sub)
        head.addView(titles, lin(0, WRAP, 1f))
        val gear = icon(Ic.GEAR, 24, textMain) { showSettings() }
        head.addView(gear, lin(dp(44), dp(44)))
        col.addView(head)

        // search
        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.background = rounded(card, 18)
        pill.setPadding(dp(14), 0, dp(6), 0)
        pill.addView(icon(Ic.SEARCH, 20, textSub), lin(dp(26), dp(26)))
        val search = EditText(this)
        search.setSingleLine(true)
        search.hint = "اكتب اسم الزبون أو رقم الشحنة"
        search.textSize = 16f
        search.setTextColor(textMain)
        search.setHintTextColor(textSub)
        search.setBackgroundColor(Color.TRANSPARENT)
        search.setPadding(dp(10), 0, dp(10), 0)
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.setText(homeQuery)
        pill.addView(search, lin(0, dp(52), 1f))
        val clear = icon(Ic.CLOSE, 18, textSub) {
            search.setText("")
        }
        pill.addView(clear, lin(dp(40), dp(40)))
        col.addView(pill, lin(MATCH, dp(54)).also { it.topMargin = dp(14) })

        // tabs
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        val tabWaiting = chip("موجودة", if (homeTab == 0) amber else chipBg, if (homeTab == 0) dark else Color.WHITE) {
            homeTab = 0
            showHome()
        }
        val tabDone = chip("مسلّمة", if (homeTab == 1) amber else chipBg, if (homeTab == 1) dark else Color.WHITE) {
            homeTab = 1
            showHome()
        }
        tabs.addView(tabWaiting)
        tabs.addView(tabDone, lin(WRAP, WRAP).also { it.marginStart = dp(8) })
        col.addView(tabs, lin(WRAP, WRAP).also { it.topMargin = dp(12); it.bottomMargin = dp(8) })

        val list = ListView(this)
        list.divider = null
        list.clipToPadding = false
        list.setPadding(0, 0, 0, dp(110))
        val empty = label("لا توجد شحنات", 15f, textSub)
        empty.gravity = Gravity.CENTER
        empty.setPadding(0, dp(60), 0, 0)
        col.addView(list, lin(MATCH, 0, 1f))

        val adapter = ParcelAdapter()
        list.adapter = adapter

        fun refresh() {
            homeQuery = search.text.toString()
            val w = db.all(false)
            val od = w.count { (System.currentTimeMillis() - it.created) / DAY >= warnDays() }
            sub.text = "${w.size} شحنة بانتظار الاستلام" + if (od > 0) " · $od متأخرة" else ""
            sub.setTextColor(if (od > 0) red else textSub)
            val shown = Search.filter(db.all(homeTab == 1), homeQuery)
            adapter.set(shown)
            empty.text = if (homeQuery.isNotBlank()) "لا نتائج مطابقة" else "لا توجد شحنات"
            empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })

        screen.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        screen.addView(empty, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_HORIZONTAL or Gravity.TOP).also {
            it.topMargin = dp(220)
        })

        // bottom actions
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(16), dp(10), dp(16), dp(16))
        val grad = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(bg, Color.argb(0, 18, 20, 26))
        )
        bar.background = grad
        bar.addView(primary("＋  شحنة جديدة") { showAdd(null) }, lin(0, WRAP, 1f))
        bar.addView(secondary("باركود") {
            scanBarcode { v ->
                search.setText(Ocr.trackingIn(v) ?: v)
            }
        }, lin(WRAP, WRAP).also { it.marginStart = dp(10) })
        screen.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))

        setScreen(screen)
        refresh()
        homeRefresh = { refresh() }
    }

    inner class ParcelAdapter : BaseAdapter() {
        private var items: List<Parcel> = emptyList()

        fun set(l: List<Parcel>) {
            items = l
            notifyDataSetChanged()
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val p = items[position]
            val row = LinearLayout(this@MainActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = rounded(card, 18)
            row.setPadding(dp(10), dp(10), dp(14), dp(10))

            val iv = ImageView(this@MainActivity)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.background = rounded(chipBg, 14)
            iv.clipToOutline = true
            val file = if (p.photo.isNotEmpty()) p.photo else p.label
            if (file.isNotEmpty()) {
                iv.tag = file
                Thumbs.load(this@MainActivity, file, dp(160)) { b ->
                    if (iv.tag == file && b != null) iv.setImageBitmap(b)
                }
            }
            row.addView(iv, lin(dp(76), dp(76)))

            val col = LinearLayout(this@MainActivity)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(dp(12), 0, dp(6), 0)
            val name = label(p.name, 17f, Color.WHITE, true)
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            col.addView(name)
            if (p.tracking.isNotEmpty()) {
                val tr = label(p.tracking, 12f, textSub)
                tr.textDirection = View.TEXT_DIRECTION_LTR
                tr.gravity = Gravity.START
                tr.maxLines = 1
                col.addView(tr)
            }
            val meta = LinearLayout(this@MainActivity)
            meta.orientation = LinearLayout.HORIZONTAL
            meta.gravity = Gravity.CENTER_VERTICAL
            meta.setPadding(0, dp(6), 0, 0)
            if (p.shelf.isNotEmpty()) {
                val s = label("رف " + p.shelf, 12f, dark, true)
                s.background = rounded(amber, 8)
                s.setPadding(dp(8), dp(3), dp(8), dp(3))
                meta.addView(s)
            }
            val whenText = (if (p.delivered > 0) "سُلّمت " + ago(p.delivered) else ago(p.created)) +
                (if (p.by.isNotEmpty()) " · " + p.by else "")
            val w = label(whenText, 12f, if (p.delivered > 0) textSub else waitColor(p.created), true)
            w.setPadding(if (p.shelf.isNotEmpty()) dp(10) else 0, 0, 0, 0)
            meta.addView(w)
            col.addView(meta)
            row.addView(col, lin(0, WRAP, 1f))
            row.setOnClickListener { showDetail(p.id) }

            val wrap = FrameLayout(this@MainActivity)
            wrap.setPadding(0, 0, 0, dp(8))
            wrap.addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
            return wrap
        }
    }

    // ---------- barcode search ----------

    private fun scanBarcode(onValue: (String) -> Unit) {
        skipLock = true
        val opts = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, opts).startScan()
            .addOnSuccessListener { b ->
                val v = b.rawValue
                if (v != null) onValue(v)
            }
            .addOnFailureListener { toast("تعذر فتح ماسح الباركود") }
    }

    // ---------- add / edit ----------

    private fun discardDraft() {
        for (f in draft.newFiles) Img.file(this, f).delete()
        draft = Draft()
    }

    private fun showAdd(edit: Parcel?, fresh: Boolean = true) {
        if (fresh) {
            discardDraft()
            if (edit != null) {
                draft.editId = edit.id
                draft.created = edit.created
                draft.delivered = edit.delivered
                draft.name = edit.name
                draft.tracking = edit.tracking
                draft.shelf = edit.shelf
                draft.note = edit.note
                draft.photo = edit.photo
                draft.label = edit.label
                draft.gid = edit.gid
                draft.by = edit.by
            }
        }
        val editing = draft.editId != 0L
        backAction = {
            val id = draft.editId
            discardDraft()
            if (id != 0L) showDetail(id) else showHome()
        }

        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), dp(28))
        sv.addView(col)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(icon(Ic.BACK, 26, textMain) { backAction?.invoke() }, lin(dp(44), dp(44)))
        head.addView(
            label(if (editing) "تعديل الشحنة" else "شحنة جديدة", 21f, Color.WHITE, true),
            lin(0, WRAP, 1f)
        )
        col.addView(head)

        val photos = LinearLayout(this)
        photos.orientation = LinearLayout.HORIZONTAL
        photoBox = photos
        col.addView(photos, lin(MATCH, dp(170)).also { it.topMargin = dp(10) })

        col.addView(
            label("١) صوّر الشحنة كاملة   ٢) امسح الملصق ليُقرأ الاسم والباركود", 12f, textSub)
                .also { it.setPadding(dp(4), dp(8), dp(4), dp(4)) }
        )

        val st = label(draft.status, 13f, green, true)
        st.setPadding(dp(4), 0, dp(4), dp(6))
        statusTv = st
        col.addView(st)

        nameEt = field(col, "اسم المستلم *", false)
        nameEt?.setText(draft.name)
        trackEt = field(col, "رقم التتبع", true)
        trackEt?.setText(draft.tracking)
        col.addView(
            secondary("قراءة الباركود بالكاميرا") {
                scanBarcode { v ->
                    val t = Ocr.trackingIn(v) ?: v
                    trackEt?.setText(t)
                    toast("تم")
                }
            }, lin(MATCH, WRAP).also { it.topMargin = dp(8) }
        )
        shelfEt = field(col, "مكان الرف (مثل A3)", false)
        shelfEt?.setText(draft.shelf)
        noteEt = field(col, "ملاحظة", false)
        noteEt?.setText(draft.note)
        lastField = nameEt

        val ct = label("اضغط سطراً من الملصق ليوضع في الحقل المحدد:", 12f, textSub)
        ct.setPadding(dp(4), dp(16), dp(4), dp(8))
        chipsTitle = ct
        col.addView(ct)
        val flow = FlowLayout(this)
        flow.hGap = dp(8)
        flow.vGap = dp(8)
        chipsBox = flow
        col.addView(flow, lin(MATCH, WRAP))

        col.addView(primary(if (editing) "حفظ التعديلات" else "حفظ") { saveDraft(false) },
            lin(MATCH, WRAP).also { it.topMargin = dp(22) })
        if (!editing) {
            col.addView(secondary("حفظ وإضافة شحنة أخرى") { saveDraft(true) },
                lin(MATCH, WRAP).also { it.topMargin = dp(10) })
        }

        setScreen(sv)
        fillPhotoCells()
        fillChips()
    }

    private fun field(parent: LinearLayout, hint: String, ltr: Boolean): EditText {
        val l = label(hint, 12f, textSub, true)
        l.setPadding(dp(4), dp(14), dp(4), dp(6))
        parent.addView(l)
        val e = EditText(this)
        e.setSingleLine(true)
        e.textSize = 16f
        e.setTextColor(textMain)
        e.setHintTextColor(textSub)
        e.background = rounded(card, 14)
        e.setPadding(dp(14), 0, dp(14), 0)
        if (ltr) {
            e.textDirection = View.TEXT_DIRECTION_LTR
            e.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        } else {
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        e.setOnFocusChangeListener { _, f -> if (f) lastField = e }
        parent.addView(e, lin(MATCH, dp(50)))
        return e
    }

    private fun syncDraft() {
        draft.name = nameEt?.text?.toString() ?: draft.name
        draft.tracking = trackEt?.text?.toString() ?: draft.tracking
        draft.shelf = shelfEt?.text?.toString() ?: draft.shelf
        draft.note = noteEt?.text?.toString() ?: draft.note
    }

    private fun fillPhotoCells() {
        val box = photoBox ?: return
        box.removeAllViews()
        for (k in 0..1) {
            val file = if (k == 0) draft.photo else draft.label
            val cell = FrameLayout(this)
            cell.background = rounded(card, 16)
            cell.clipToOutline = true
            if (file.isNotEmpty()) {
                val iv = ImageView(this)
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                cell.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
                Thumbs.load(this, file, dp(420)) { b -> if (b != null) iv.setImageBitmap(b) }
                val tag = label(if (k == 0) "الشحنة · إعادة" else "الملصق · إعادة", 11f, Color.WHITE, true)
                tag.background = rounded(Color.argb(160, 0, 0, 0), 10)
                tag.setPadding(dp(10), dp(4), dp(10), dp(4))
                cell.addView(
                    tag,
                    FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                        .also { it.bottomMargin = dp(8) }
                )
            } else {
                val inner = LinearLayout(this)
                inner.orientation = LinearLayout.VERTICAL
                inner.gravity = Gravity.CENTER
                inner.addView(icon(if (k == 0) Ic.PLUS else Ic.SEARCH, 30, amber), lin(dp(40), dp(40)))
                inner.addView(label(if (k == 0) "صوّر الشحنة" else "امسح الملصق", 14f, Color.WHITE, true))
                cell.addView(inner, FrameLayout.LayoutParams(MATCH, MATCH))
            }
            cell.setOnClickListener { if (k == 0) takePhoto(0) else scanLabel() }
            box.addView(cell, lin(0, MATCH, 1f).also { it.setMargins(dp(4), 0, dp(4), 0) })
        }
    }

    private fun fillChips() {
        val box = chipsBox ?: return
        box.removeAllViews()
        chipsTitle?.visibility = if (draft.lines.isEmpty()) View.GONE else View.VISIBLE
        for (l in draft.lines) {
            box.addView(chip(l) {
                val f = lastField ?: nameEt
                if (f != null) {
                    val v = if (f === trackEt) (Ocr.trackingIn(l) ?: l.replace(" ", "")) else l
                    f.setText(v)
                    f.setSelection(v.length)
                }
            })
        }
    }

    private fun takePhoto(target: Int) {
        captureTarget = target
        syncDraft()
        val f = File(cacheDir, "cap.jpg")
        f.delete()
        val uri = FileProvider.getUriForFile(this, AUTH, f)
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        i.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("", uri)
        skipLock = true
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_PHOTO)
        } catch (e: Exception) {
            skipLock = false
            toast("لا يوجد تطبيق كاميرا")
        }
    }

    private fun scanLabel() {
        syncDraft()
        skipLock = true
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                try {
                    @Suppress("DEPRECATION")
                    startIntentSenderForResult(sender, REQ_DOC, null, 0, 0, 0)
                } catch (e: Exception) {
                    skipLock = false
                    takePhoto(1)
                }
            }
            .addOnFailureListener {
                skipLock = false
                takePhoto(1)
            }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_PHOTO -> {
                val uri = FileProvider.getUriForFile(this, AUTH, File(cacheDir, "cap.jpg"))
                val saved = Img.saveScaled(this, uri, if (captureTarget == 0) "p" else "l")
                if (saved == null) {
                    toast("تعذر حفظ الصورة")
                } else if (captureTarget == 0) {
                    setPhoto(saved)
                } else {
                    setLabel(saved)
                }
            }

            REQ_DOC -> {
                val res = GmsDocumentScanningResult.fromActivityResultIntent(data)
                val uri = res?.pages?.firstOrNull()?.imageUri
                val saved = if (uri != null) Img.saveScaled(this, uri, "l") else null
                if (saved == null) toast("تعذر قراءة الملصق") else setLabel(saved)
            }
        }
    }

    private fun replaceFile(old: String) {
        if (old.isEmpty()) return
        if (draft.newFiles.remove(old)) Img.file(this, old).delete() else draft.oldFiles.add(old)
    }

    private fun setPhoto(name: String) {
        replaceFile(draft.photo)
        draft.photo = name
        draft.newFiles.add(name)
        fillPhotoCells()
    }

    private fun setLabel(name: String) {
        replaceFile(draft.label)
        draft.label = name
        draft.newFiles.add(name)
        fillPhotoCells()
        draft.status = "جارٍ قراءة الملصق..."
        statusTv?.setTextColor(amber)
        statusTv?.text = draft.status
        Ocr.analyze(this, Uri.fromFile(Img.file(this, name))) { r ->
            draft.lines = r.lines
            val n = nameEt
            val t = trackEt
            if (n != null && n.text.isNullOrBlank() && r.nameGuess != null) n.setText(r.nameGuess)
            if (t != null && t.text.isNullOrBlank() && r.trackingGuess != null) t.setText(r.trackingGuess)
            val found = if (r.trackingGuess != null) "تم العثور على رقم التتبع" else "لم يُعثر على رقم تتبع"
            val nm = if (r.nameGuess != null) "والاسم" else "؛ اختر الاسم من الأسطر"
            draft.status = "تمت القراءة: $found $nm"
            statusTv?.setTextColor(green)
            statusTv?.text = draft.status
            fillChips()
        }
    }

    private fun saveDraft(andNew: Boolean) {
        syncDraft()
        if (draft.name.isBlank()) {
            toast("اكتب اسم المستلم")
            nameEt?.requestFocus()
            return
        }
        val doSave = {
            val p = Parcel(
                draft.editId, draft.name.trim(), draft.tracking.trim(), draft.shelf.trim(), draft.note.trim(),
                draft.photo, draft.label,
                if (draft.editId != 0L) draft.created else System.currentTimeMillis(),
                draft.delivered,
                if (draft.gid.isNotEmpty()) draft.gid else java.util.UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                if (draft.editId != 0L) draft.by else Sync.who(this)
            )
            val eid = draft.editId
            if (eid == 0L) db.insert(p) else db.update(p)
            Sync.push(this, p)
            for (f in draft.oldFiles) {
                Img.file(this, f).delete()
                Sync.removeImage(this, f)
            }
            draft = Draft()
            toast("تم الحفظ")
            if (andNew) showAdd(null) else if (eid != 0L) showDetail(eid) else showHome()
        }
        val tr = draft.tracking.trim()
        val dup = tr.isNotEmpty() && db.all(false).any { it.tracking == tr && it.id != draft.editId }
        if (dup) {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("رقم التتبع هذا موجود مسبقاً بين الشحنات الموجودة. حفظ على أي حال؟")
                .setPositiveButton("حفظ") { _, _ -> doSave() }
                .setNegativeButton("إلغاء", null)
                .show()
        } else {
            doSave()
        }
    }

    // ---------- detail ----------

    private fun showFull(file: String) {
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val iv = ImageView(this)
        iv.setBackgroundColor(Color.BLACK)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        Thumbs.load(this, file, 1600) { b -> if (b != null) iv.setImageBitmap(b) }
        iv.setOnClickListener { d.dismiss() }
        d.setContentView(iv)
        d.show()
    }

    private fun showDetail(id: Long) {
        val p = db.get(id)
        if (p == null) {
            showHome()
            return
        }
        backAction = { showHome() }
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), dp(28))
        sv.addView(col)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(icon(Ic.BACK, 26, textMain) { showHome() }, lin(dp(44), dp(44)))
        head.addView(label("تفاصيل الشحنة", 20f, Color.WHITE, true), lin(0, WRAP, 1f))
        col.addView(head)

        // images
        var which = if (p.photo.isNotEmpty()) 0 else 1
        val imgBox = FrameLayout(this)
        imgBox.background = rounded(Color.BLACK, 18)
        imgBox.clipToOutline = true
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        imgBox.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
        val empty = label("لا توجد صورة", 14f, textSub)
        empty.gravity = Gravity.CENTER
        imgBox.addView(empty, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(imgBox, lin(MATCH, dp(340)).also { it.topMargin = dp(10) })

        fun current() = if (which == 0) p.photo else p.label
        fun showImage() {
            val f = current()
            empty.visibility = if (f.isEmpty()) View.VISIBLE else View.GONE
            iv.setImageBitmap(null)
            if (f.isNotEmpty()) Thumbs.load(this, f, 1200) { b -> if (b != null) iv.setImageBitmap(b) }
        }
        imgBox.setOnClickListener { if (current().isNotEmpty()) showFull(current()) }
        showImage()

        if (p.photo.isNotEmpty() && p.label.isNotEmpty()) {
            val sw = LinearLayout(this)
            sw.orientation = LinearLayout.HORIZONTAL
            val a = chip("الشحنة", amber, dark) {}
            val b = chip("الملصق", chipBg, Color.WHITE) {}
            fun paint() {
                a.background = rounded(if (which == 0) amber else chipBg, 12)
                a.setTextColor(if (which == 0) dark else Color.WHITE)
                b.background = rounded(if (which == 1) amber else chipBg, 12)
                b.setTextColor(if (which == 1) dark else Color.WHITE)
            }
            a.setOnClickListener { which = 0; paint(); showImage() }
            b.setOnClickListener { which = 1; paint(); showImage() }
            paint()
            sw.addView(a)
            sw.addView(b, lin(WRAP, WRAP).also { it.marginStart = dp(8) })
            col.addView(sw, lin(WRAP, WRAP).also { it.topMargin = dp(10) })
        }

        // info
        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        info.background = rounded(card, 18)
        info.setPadding(dp(16), dp(14), dp(16), dp(14))
        info.addView(label(p.name, 24f, Color.WHITE, true))
        if (p.shelf.isNotEmpty()) {
            val s = label("الرف: " + p.shelf, 16f, dark, true)
            s.background = rounded(amber, 10)
            s.setPadding(dp(12), dp(5), dp(12), dp(5))
            info.addView(s, lin(WRAP, WRAP).also { it.topMargin = dp(10) })
        }
        if (p.tracking.isNotEmpty()) {
            val tr = label(p.tracking, 15f, textMain, true)
            tr.textDirection = View.TEXT_DIRECTION_LTR
            tr.gravity = Gravity.START
            tr.setPadding(0, dp(12), 0, dp(6))
            info.addView(tr)
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.addView(chip("نسخ") {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("tracking", p.tracking))
                toast("تم النسخ")
            })
            r.addView(chip("تتبع DHL") {
                skipLock = true
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.dhl.com/de-de/home/tracking/tracking-parcel.html?submit=1&tracking-id=" + p.tracking)
                        )
                    )
                } catch (e: Exception) {
                    skipLock = false
                }
            }, lin(WRAP, WRAP).also { it.marginStart = dp(8) })
            info.addView(r)
        }
        if (p.by.isNotEmpty()) {
            info.addView(label("أضافها: " + p.by, 13f, textSub).also { it.setPadding(0, dp(12), 0, 0) })
        }
        val recv = label("استُلمت: " + dateFmt.format(Date(p.created)) + " (" + ago(p.created) + ")", 13f, textSub)
        recv.setPadding(0, dp(4), 0, 0)
        info.addView(recv)
        if (p.delivered > 0) {
            info.addView(label("سُلّمت: " + dateFmt.format(Date(p.delivered)), 13f, green, true).also {
                it.setPadding(0, dp(4), 0, 0)
            })
        }
        if (p.note.isNotEmpty()) {
            info.addView(label(p.note, 14f, textMain).also { it.setPadding(0, dp(12), 0, 0) })
        }
        col.addView(info, lin(MATCH, WRAP).also { it.topMargin = dp(14) })

        // actions
        if (p.delivered == 0L) {
            col.addView(primary("تم التسليم") {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage("تسليم الشحنة إلى ${p.name}؟")
                    .setPositiveButton("تسليم") { _, _ ->
                        p.delivered = System.currentTimeMillis()
                        touch(p)
                        toast("تم التسليم")
                        showHome()
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }, lin(MATCH, WRAP).also { it.topMargin = dp(18) })
        } else {
            col.addView(secondary("إرجاع إلى الشحنات الموجودة") {
                p.delivered = 0L
                touch(p)
                homeTab = 0
                showDetail(p.id)
            }, lin(MATCH, WRAP).also { it.topMargin = dp(18) })
        }
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(secondary("تعديل") { showAdd(p) }, lin(0, WRAP, 1f))
        row.addView(
            chip("حذف", Color.rgb(120, 40, 40)) {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage("حذف هذه الشحنة نهائياً مع صورها؟")
                    .setPositiveButton("حذف") { _, _ ->
                        wipe(p)
                        showHome()
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }.also { it.setPadding(dp(26), dp(14), dp(26), dp(14)) },
            lin(WRAP, WRAP).also { it.marginStart = dp(10) }
        )
        col.addView(row, lin(MATCH, WRAP).also { it.topMargin = dp(10) })

        setScreen(sv)
    }

    // ---------- settings ----------

    private fun settingRow(title: String, value: String, onClick: () -> Unit): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = rounded(card, 16)
        r.setPadding(dp(16), dp(15), dp(16), dp(15))
        r.addView(label(title, 15f, textMain, true), lin(0, WRAP, 1f))
        r.addView(label(value, 14f, amber, true))
        r.setOnClickListener { onClick() }
        r.layoutParams = lin(MATCH, WRAP).also { it.bottomMargin = dp(8) }
        return r
    }

    private fun next(cur: Int, options: List<Int>): Int {
        val i = options.indexOf(cur)
        return options[(i + 1) % options.size]
    }

    private fun showSettings() {
        backAction = { showHome() }
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), dp(28))
        sv.addView(col)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(icon(Ic.BACK, 26, textMain) { showHome() }, lin(dp(44), dp(44)))
        head.addView(label("الإعدادات", 21f, Color.WHITE, true), lin(0, WRAP, 1f))
        col.addView(head)
        col.addView(Space(this), lin(MATCH, dp(10)))

        val ret = retentionDays()
        col.addView(settingRow("حذف الشحنات المسلّمة بعد", if (ret <= 0) "أبداً" else "$ret يوم") {
            cfg().edit().putInt("retention", next(ret, listOf(7, 14, 30, 90, 0))).apply()
            showSettings()
        })
        val warn = warnDays()
        col.addView(settingRow("تنبيه التأخير بعد", "$warn أيام") {
            cfg().edit().putInt("warn", next(warn, listOf(3, 5, 7, 10, 14))).apply()
            showSettings()
        })
        col.addView(settingRow("مزامنة الكشك", if (Sync.enabled(this)) Sync.kiosk(this) + " · " + syncState else "غير مفعّلة") {
            showSyncSetup(true)
        })
        col.addView(settingRow("تغيير رمز القفل", "") {
            lock.showUnlock("الرمز الحالي", false, true, {
                lock.showSetup(true, { toast("تم تغيير الرمز") }, null)
            }, null, false)
        })
        col.addView(settingRow("تصدير القائمة (CSV)", "") { exportCsv() })
        col.addView(settingRow("حذف كل البيانات", "") {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("سيتم حذف كل الشحنات وصورها نهائياً" + (if (Sync.enabled(this)) " عند جميع العاملين" else "") + ". متابعة؟")
                .setPositiveButton("حذف الكل") { _, _ ->
                    for (p in db.clearAll()) {
                        deleteFiles(p)
                        Sync.remove(this, p)
                    }
                    toast("تم الحذف")
                    showHome()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        })

        val note = label(
            "كل البيانات والصور محفوظة على هذا الجوال فقط ومحمية بالرمز. لا يُرسل شيء إلى أي خادم.",
            12f, textSub
        )
        note.setPadding(dp(6), dp(16), dp(6), 0)
        col.addView(note)
        setScreen(sv)
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun exportCsv() {
        try {
            val all = db.all(false) + db.all(true)
            val sb = StringBuilder("name,tracking,shelf,note,received,delivered\n")
            for (p in all) {
                sb.append(csv(p.name)).append(',').append(csv(p.tracking)).append(',').append(csv(p.shelf)).append(',')
                    .append(csv(p.note)).append(',').append(csv(dateFmt.format(Date(p.created)))).append(',')
                    .append(csv(if (p.delivered > 0) dateFmt.format(Date(p.delivered)) else "")).append('\n')
            }
            val f = File(cacheDir, "parcels.csv")
            f.writeText(sb.toString())
            val uri = FileProvider.getUriForFile(this, AUTH, f)
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/csv"
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            skipLock = true
            startActivity(Intent.createChooser(i, "تصدير"))
        } catch (e: Exception) {
            skipLock = false
            toast("تعذر التصدير")
        }
    }

    // ---------- sync setup ----------

    private fun showSyncSetup(fromSettings: Boolean) {
        backAction = { if (fromSettings) showSettings() else showHome() }
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(24), dp(20), dp(28))
        sv.addView(col)
        col.addView(label("ربط الكشك", 26f, Color.WHITE, true))
        col.addView(
            label(
                "أدخل نفس الرمز في جوالات كل العاملين لتظهر الشحنات عند الجميع فوراً. اختر رمزاً طويلاً يصعب تخمينه، فهو ما يفصل بياناتكم عن غيركم.",
                14f, textSub
            ).also { it.setPadding(0, dp(8), 0, dp(8)) }
        )
        val code = field(col, "رمز الكشك", true)
        code.setText(Sync.kiosk(this))
        val who = field(col, "اسمك (يظهر على الشحنات التي تضيفها)", false)
        who.setText(Sync.who(this))
        col.addView(primary("ربط") {
            val c = Sync.normCode(code.text.toString())
            val w = who.text.toString().trim()
            if (c.length < 6) {
                toast("الرمز قصير، استخدم 6 أحرف أو أرقام على الأقل")
                return@primary
            }
            val old = Sync.kiosk(this)
            val go = {
                if (old != c) {
                    for (p in db.clearAll()) deleteFiles(p)
                }
                Sync.connect(this, c, w)
                startSync()
                toast("تم الربط")
                if (fromSettings) showSettings() else showHome()
            }
            if (old.isNotEmpty() && old != c) {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage("سيُمسح ما على هذا الجوال ويُحمَّل محتوى الكشك الجديد. متابعة؟")
                    .setPositiveButton("متابعة") { _, _ -> go() }
                    .setNegativeButton("إلغاء", null)
                    .show()
            } else {
                go()
            }
        }, lin(MATCH, WRAP).also { it.topMargin = dp(22) })
        if (Sync.enabled(this)) {
            col.addView(secondary("فصل المزامنة عن هذا الجوال") {
                Sync.disconnect(this)
                syncState = ""
                toast("تم الفصل")
                showSettings()
            }, lin(MATCH, WRAP).also { it.topMargin = dp(10) })
        } else {
            col.addView(secondary("تخطي (بدون مزامنة)") {
                Sync.skip(this)
                if (fromSettings) showSettings() else showHome()
            }, lin(MATCH, WRAP).also { it.topMargin = dp(10) })
        }
        setScreen(sv)
    }
}
