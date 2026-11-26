package dev.ehssan.parcels

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScanResult(
    val lines: List<String>,
    val nameGuess: String?,
    val trackingGuess: String?
)

object Ocr {

    private val trackRe = Regex("(JJD\\d{10,}|\\d{12,20})")

    private val nameKeys = listOf(
        "ship to", "deliver to", "recipient", "empfänger", "empfaenger", "lieferadresse", "versand an", "to:", "an:"
    )

    /** Pulls a likely tracking number out of free text or a barcode payload. */
    fun trackingIn(s: String): String? {
        val compact = s.replace(" ", "").replace("-", "")
        return trackRe.findAll(compact).map { it.value }.maxByOrNull { it.length }
    }

    private fun letters(s: String) = s.count { it.isLetter() }

    private fun looksLikeName(s: String): Boolean {
        val t = s.trim()
        if (t.length < 3 || t.length > 50) return false
        return letters(t) >= t.length / 2
    }

    fun analyze(ctx: Context, uri: Uri, done: (ScanResult) -> Unit) {
        val image = try {
            InputImage.fromFilePath(ctx, uri)
        } catch (e: Exception) {
            done(ScanResult(emptyList(), null, null))
            return
        }
        val lines = ArrayList<String>()
        val codes = ArrayList<String>()
        var pending = 2

        fun finish() {
            pending--
            if (pending == 0) done(build(lines, codes))
        }

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { t ->
                for (b in t.textBlocks) for (l in b.lines) {
                    val x = l.text.trim()
                    if (x.length >= 2) lines.add(x)
                }
            }
            .addOnCompleteListener { finish() }

        BarcodeScanning.getClient().process(image)
            .addOnSuccessListener { list ->
                for (b in list) b.rawValue?.let { codes.add(it) }
            }
            .addOnCompleteListener { finish() }
    }

    private fun build(lines: List<String>, codes: List<String>): ScanResult {
        var tracking: String? = codes.mapNotNull { trackingIn(it) }.maxByOrNull { it.length }
        if (tracking == null) {
            tracking = lines.mapNotNull { trackingIn(it) }.maxByOrNull { it.length }
        }

        var name: String? = null
        for ((i, line) in lines.withIndex()) {
            val low = line.lowercase()
            val key = nameKeys.firstOrNull { low.contains(it) } ?: continue
            val after = line.substring(low.indexOf(key) + key.length).trim(' ', ':', '-')
            if (looksLikeName(after)) {
                name = after
                break
            }
            val next = lines.getOrNull(i + 1)
            if (next != null && looksLikeName(next)) {
                name = next.trim()
                break
            }
        }

        val chips = lines.distinct().filter { it.length <= 60 }.take(30)
        return ScanResult(chips, name, tracking)
    }
}
