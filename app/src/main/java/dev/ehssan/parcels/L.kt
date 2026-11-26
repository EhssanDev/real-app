package dev.ehssan.parcels

import android.app.AlertDialog
import android.content.Context
import android.view.View

/** Tiny translation layer: source strings are Arabic, looked up for English / German. */
object L {
    var lang = "ar"

    fun load(ctx: Context) {
        lang = ctx.getSharedPreferences("pd_cfg", Context.MODE_PRIVATE).getString("lang", "ar") ?: "ar"
    }

    fun set(ctx: Context, l: String) {
        lang = l
        ctx.getSharedPreferences("pd_cfg", Context.MODE_PRIVATE).edit().putString("lang", l).apply()
    }

    fun next(): String = when (lang) {
        "ar" -> "en"
        "en" -> "de"
        else -> "ar"
    }

    fun name(l: String = lang) = when (l) {
        "en" -> "English"
        "de" -> "Deutsch"
        else -> "العربية"
    }

    fun dir() = if (lang == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

    fun t(ar: String): String {
        if (lang == "ar") return ar
        val e = map[ar] ?: return ar
        return if (lang == "de") e[1] else e[0]
    }

    private fun pick(ar: String, en: String, de: String) = when (lang) {
        "en" -> en
        "de" -> de
        else -> ar
    }

    fun daysAgo(d: Int) = pick("قبل $d أيام", "$d days ago", "vor $d Tagen")
    fun days(d: Int) = pick("$d أيام", "$d days", "$d Tage")
    fun dayCount(d: Int) = pick("$d يوم", "$d days", "$d Tage")
    fun waiting(n: Int, late: Int): String {
        val base = pick("$n شحنة بانتظار الاستلام", "$n parcels waiting", "$n Pakete warten")
        val l = if (late > 0) " · " + pick("$late متأخرة", "$late late", "$late überfällig") else ""
        return base + l
    }
    fun wait(s: Int) = pick("حاول بعد $s ثانية", "Try again in $s s", "Erneut versuchen in $s s")
    fun handover(n: String) = pick("تسليم الشحنة إلى $n؟", "Hand the parcel over to $n?", "Paket an $n übergeben?")

    private val map: Map<String, Array<String>> = mapOf(
        "تأكيد" to arrayOf("Confirm", "Bestätigen"),
        "إلغاء" to arrayOf("Cancel", "Abbrechen"),
        "اختر رمز قفل جديد" to arrayOf("Choose a new PIN", "Neuen PIN wählen"),
        "٤ أرقام على الأقل" to arrayOf("At least 4 digits", "Mindestens 4 Ziffern"),
        "الرمز غير صحيح" to arrayOf("Wrong PIN", "Falscher PIN"),
        "أعد إدخال الرمز" to arrayOf("Re-enter the PIN", "PIN erneut eingeben"),
        "الرمزان غير متطابقين" to arrayOf("PINs do not match", "PINs stimmen nicht überein"),
        "الرمز الحالي" to arrayOf("Current PIN", "Aktueller PIN"),
        "استخدم الرمز" to arrayOf("Use PIN", "PIN verwenden"),
        "افتح Mina's Kiosk DHL" to arrayOf("Unlock Mina's Kiosk DHL", "Mina's Kiosk DHL entsperren"),
        "تعذر تشغيل المزامنة" to arrayOf("Could not start sync", "Synchronisierung konnte nicht gestartet werden"),
        "جارٍ الاتصال..." to arrayOf("Connecting...", "Verbinde..."),
        "تحتاج إنترنت لأول اتصال" to arrayOf("Internet needed for the first connection", "Für die erste Verbindung ist Internet nötig"),
        "متصل" to arrayOf("Connected", "Verbunden"),
        "غير متصل" to arrayOf("Offline", "Offline"),
        "غير متصل (يعمل محلياً)" to arrayOf("Offline (working locally)", "Offline (lokal aktiv)"),
        "اليوم" to arrayOf("Today", "Heute"),
        "أمس" to arrayOf("Yesterday", "Gestern"),
        "شحنة جديدة" to arrayOf("New parcel", "Neues Paket"),
        "＋  شحنة جديدة" to arrayOf("＋  New parcel", "＋  Neues Paket"),
        "اكتب اسم الزبون أو رقم الشحنة" to arrayOf("Customer name or tracking number", "Kundenname oder Sendungsnummer"),
        "موجودة" to arrayOf("Waiting", "Vorhanden"),
        "مسلّمة" to arrayOf("Delivered", "Übergeben"),
        "لا توجد شحنات" to arrayOf("No parcels", "Keine Pakete"),
        "لا نتائج مطابقة" to arrayOf("No matches", "Keine Treffer"),
        "باركود" to arrayOf("Barcode", "Barcode"),
        "رف" to arrayOf("Shelf", "Regal"),
        "سُلّمت" to arrayOf("Delivered", "Übergeben"),
        "تعذر فتح ماسح الباركود" to arrayOf("Could not open the barcode scanner", "Barcode-Scanner konnte nicht geöffnet werden"),
        "تعديل الشحنة" to arrayOf("Edit parcel", "Paket bearbeiten"),
        "١) صوّر الشحنة كاملة   ٢) امسح الملصق ليُقرأ الاسم والباركود" to arrayOf(
            "1) Photograph the whole parcel   2) Scan the label to read name and barcode",
            "1) Ganzes Paket fotografieren   2) Etikett scannen, um Name und Barcode zu lesen"
        ),
        "اسم المستلم *" to arrayOf("Recipient name *", "Empfängername *"),
        "رقم التتبع" to arrayOf("Tracking number", "Sendungsnummer"),
        "قراءة الباركود بالكاميرا" to arrayOf("Read barcode with camera", "Barcode mit Kamera lesen"),
        "تم" to arrayOf("Done", "Fertig"),
        "مكان الرف (مثل A3)" to arrayOf("Shelf spot (e.g. A3)", "Regalplatz (z. B. A3)"),
        "ملاحظة" to arrayOf("Note", "Notiz"),
        "اضغط سطراً من الملصق ليوضع في الحقل المحدد:" to arrayOf(
            "Tap a label line to put it in the selected field:",
            "Zeile vom Etikett antippen, um sie ins gewählte Feld zu übernehmen:"
        ),
        "حفظ التعديلات" to arrayOf("Save changes", "Änderungen speichern"),
        "حفظ" to arrayOf("Save", "Speichern"),
        "حفظ وإضافة شحنة أخرى" to arrayOf("Save and add another", "Speichern und weiteres hinzufügen"),
        "الشحنة · إعادة" to arrayOf("Parcel · retake", "Paket · neu"),
        "الملصق · إعادة" to arrayOf("Label · rescan", "Etikett · neu"),
        "صوّر الشحنة" to arrayOf("Photograph parcel", "Paket fotografieren"),
        "امسح الملصق" to arrayOf("Scan label", "Etikett scannen"),
        "لا يوجد تطبيق كاميرا" to arrayOf("No camera app found", "Keine Kamera-App gefunden"),
        "تعذر حفظ الصورة" to arrayOf("Could not save the photo", "Foto konnte nicht gespeichert werden"),
        "تعذر قراءة الملصق" to arrayOf("Could not read the label", "Etikett konnte nicht gelesen werden"),
        "جارٍ قراءة الملصق..." to arrayOf("Reading label...", "Etikett wird gelesen..."),
        "تم العثور على رقم التتبع" to arrayOf("Tracking number found", "Sendungsnummer gefunden"),
        "لم يُعثر على رقم تتبع" to arrayOf("No tracking number found", "Keine Sendungsnummer gefunden"),
        "والاسم" to arrayOf("and name", "und Name"),
        "؛ اختر الاسم من الأسطر" to arrayOf("; pick the name from the lines", "; Name aus den Zeilen wählen"),
        "تمت القراءة" to arrayOf("Scan done", "Gelesen"),
        "اكتب اسم المستلم" to arrayOf("Enter the recipient name", "Empfängernamen eingeben"),
        "تم الحفظ" to arrayOf("Saved", "Gespeichert"),
        "رقم التتبع هذا موجود مسبقاً بين الشحنات الموجودة. حفظ على أي حال؟" to arrayOf(
            "This tracking number already exists among waiting parcels. Save anyway?",
            "Diese Sendungsnummer ist bei den vorhandenen Paketen schon da. Trotzdem speichern?"
        ),
        "تفاصيل الشحنة" to arrayOf("Parcel details", "Paketdetails"),
        "لا توجد صورة" to arrayOf("No photo", "Kein Foto"),
        "الشحنة" to arrayOf("Parcel", "Paket"),
        "الملصق" to arrayOf("Label", "Etikett"),
        "الرف" to arrayOf("Shelf", "Regal"),
        "نسخ" to arrayOf("Copy", "Kopieren"),
        "تم النسخ" to arrayOf("Copied", "Kopiert"),
        "تتبع DHL" to arrayOf("Track on DHL", "Bei DHL verfolgen"),
        "أضافها" to arrayOf("Added by", "Hinzugefügt von"),
        "استُلمت" to arrayOf("Received", "Eingegangen"),
        "تم التسليم" to arrayOf("Mark as delivered", "Als übergeben markieren"),
        "تم التسليم ✓" to arrayOf("Delivered ✓", "Übergeben ✓"),
        "تسليم" to arrayOf("Hand over", "Übergeben"),
        "إرجاع إلى الشحنات الموجودة" to arrayOf("Move back to waiting", "Zurück zu vorhandenen Paketen"),
        "تعديل" to arrayOf("Edit", "Bearbeiten"),
        "حذف" to arrayOf("Delete", "Löschen"),
        "حذف هذه الشحنة نهائياً مع صورها؟" to arrayOf(
            "Delete this parcel and its photos permanently?",
            "Dieses Paket samt Fotos endgültig löschen?"
        ),
        "الإعدادات" to arrayOf("Settings", "Einstellungen"),
        "حذف الشحنات المسلّمة بعد" to arrayOf("Delete delivered parcels after", "Übergebene Pakete löschen nach"),
        "أبداً" to arrayOf("Never", "Nie"),
        "تنبيه التأخير بعد" to arrayOf("Late warning after", "Verspätungswarnung nach"),
        "غير مفعّلة" to arrayOf("Off", "Aus"),
        "مزامنة الكشك" to arrayOf("Kiosk sync", "Kiosk-Sync"),
        "اللغة" to arrayOf("Language", "Sprache"),
        "تغيير رمز القفل" to arrayOf("Change PIN", "PIN ändern"),
        "تم تغيير الرمز" to arrayOf("PIN changed", "PIN geändert"),
        "تصدير القائمة (CSV)" to arrayOf("Export list (CSV)", "Liste exportieren (CSV)"),
        "حذف كل البيانات" to arrayOf("Delete all data", "Alle Daten löschen"),
        "سيتم حذف كل الشحنات وصورها نهائياً" to arrayOf(
            "All parcels and their photos will be deleted permanently",
            "Alle Pakete und Fotos werden endgültig gelöscht"
        ),
        "عند جميع العاملين" to arrayOf("for all workers", "bei allen Mitarbeitern"),
        "متابعة؟" to arrayOf("Continue?", "Fortfahren?"),
        "حذف الكل" to arrayOf("Delete all", "Alles löschen"),
        "تم الحذف" to arrayOf("Deleted", "Gelöscht"),
        "كل البيانات والصور محفوظة على هذا الجوال فقط ومحمية بالرمز. لا يُرسل شيء إلى أي خادم." to arrayOf(
            "Data and photos are stored on this phone, protected by the PIN, and shared only with your kiosk through the sync code.",
            "Daten und Fotos liegen auf diesem Handy, sind per PIN geschützt und werden nur über den Sync-Code mit eurem Kiosk geteilt."
        ),
        "تصدير" to arrayOf("Export", "Exportieren"),
        "تعذر التصدير" to arrayOf("Export failed", "Export fehlgeschlagen"),
        "ربط الكشك" to arrayOf("Link kiosk", "Kiosk verbinden"),
        "أدخل نفس الرمز في جوالات كل العاملين لتظهر الشحنات عند الجميع فوراً. اختر رمزاً طويلاً يصعب تخمينه، فهو ما يفصل بياناتكم عن غيركم." to arrayOf(
            "Enter the same code on every worker's phone so parcels show up for everyone instantly. Choose a long code that is hard to guess; it keeps your data separate from others.",
            "Gib auf allen Handys denselben Code ein, damit Pakete sofort bei allen erscheinen. Wähle einen langen, schwer erratbaren Code; er trennt eure Daten von anderen."
        ),
        "رمز الكشك" to arrayOf("Kiosk code", "Kiosk-Code"),
        "اسمك (يظهر على الشحنات التي تضيفها)" to arrayOf(
            "Your name (shown on parcels you add)",
            "Dein Name (wird bei von dir hinzugefügten Paketen angezeigt)"
        ),
        "ربط" to arrayOf("Link", "Verbinden"),
        "الرمز قصير، استخدم 6 أحرف أو أرقام على الأقل" to arrayOf(
            "Code too short, use at least 6 letters or digits",
            "Code zu kurz, mindestens 6 Zeichen verwenden"
        ),
        "تم الربط" to arrayOf("Linked", "Verbunden"),
        "سيُمسح ما على هذا الجوال ويُحمَّل محتوى الكشك الجديد. متابعة؟" to arrayOf(
            "This phone's data will be cleared and the new kiosk's data loaded. Continue?",
            "Die Daten dieses Handys werden gelöscht und die des neuen Kiosks geladen. Fortfahren?"
        ),
        "متابعة" to arrayOf("Continue", "Weiter"),
        "فصل المزامنة عن هذا الجوال" to arrayOf("Disconnect sync from this phone", "Sync von diesem Handy trennen"),
        "تم الفصل" to arrayOf("Disconnected", "Getrennt"),
        "تخطي (بدون مزامنة)" to arrayOf("Skip (no sync)", "Überspringen (ohne Sync)")
    )
}

fun AlertDialog.Builder.msg(s: String): AlertDialog.Builder = setMessage(L.t(s))

fun AlertDialog.Builder.go() {
    val d = create()
    d.show()
    d.window?.decorView?.layoutDirection = L.dir()
}
