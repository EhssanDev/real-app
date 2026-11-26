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

private class Ln(val text: String, val block: Int)

object Ocr {

    private val trackRe = Regex("(JJD\\d{10,}|\\d{12,20})")

    // "12345 Berlin", "D-12345 Berlin", "1234 AB Amsterdam", "1010 Wien"
    private val postRe = Regex("^(?:[A-Za-z]{1,2}[- ]?)?\\d{4,5}\\s+\\p{L}.*")

    private val nameKeys = listOf(
        "ship to", "deliver to", "recipient", "empfänger", "empfaenger", "lieferadresse", "versand an", "to:", "an:"
    )

    private val senderWords = listOf("absender", "sender", "retoure", "return", "rücksendung", "from:", "von:")

    private val noise = listOf(
        "dhl", "paket", "germany", "deutschland", "austria", "österreich", "sendung", "tracking", "packstation",
        "gogreen", "www.", "einlieferung", "empfänger", "empfaenger", "ship to", "deliver to", "recipient",
        "absender", "sender", "express", "standard", "weight", "gewicht", "retoure", "versand", "postfiliale"
    )

    /** Pulls a likely tracking number out of free text or a barcode payload. */
    fun trackingIn(s: String): String? {
        val compact = s.replace(" ", "").replace("-", "")
        return trackRe.findAll(compact).map { it.value }.maxByOrNull { it.length }
    }

    private fun looksLikeName(s: String): Boolean {
        val t = s.trim()
        if (t.length < 3 || t.length > 40) return false
        if (t.any { it.isDigit() } || t.contains('@')) return false
        val compact = t.count { !it.isWhitespace() }
        if (compact == 0 || t.count { it.isLetter() } < compact * 0.8) return false
        if (t.split(Regex("\\s+")).size > 5) return false
        val low = t.lowercase()
        return noise.none { low.contains(it) }
    }

    private fun clean(raw: String): String {
        var t = raw.trim().trim(',', ';', ':', '-', '.').trim()
        t = t.replace(Regex("(?i)^(herr|frau|mr|mrs|ms)\\.?\\s+"), "")
        if (t.none { it.isLowerCase() }) {
            t = t.lowercase().split(' ').joinToString(" ") { w ->
                var up = true
                val sb = StringBuilder()
                for (c in w) {
                    sb.append(if (up) c.uppercaseChar() else c)
                    up = c == '-' || c == '\''
                }
                sb.toString()
            }
        }
        return t
    }

    private fun nameByKeyword(lines: List<Ln>): String? {
        for ((i, l) in lines.withIndex()) {
            val low = l.text.lowercase()
            val key = nameKeys.firstOrNull { low.contains(it) } ?: continue
            val after = l.text.substring(low.indexOf(key) + key.length).trim(' ', ':', '-')
            if (looksLikeName(after)) return clean(after)
            val next = lines.getOrNull(i + 1)?.text
            if (next != null && looksLikeName(next)) return clean(next)
        }
        return null
    }

    /** The recipient block normally sits below the sender's and ends with "ZIP City"; the name is above the street line. */
    private fun nameByAddress(lines: List<Ln>): String? {
        val senderBlocks = HashSet<Int>()
        for (l in lines) {
            val low = l.text.lowercase()
            if (senderWords.any { low.contains(it) }) senderBlocks.add(l.block)
        }
        val posts = lines.indices.filter { postRe.matches(lines[it].text.trim()) && lines[it].block !in senderBlocks }
        for (i in posts.reversed()) {
            var j = i - 1
            while (j >= 0 && i - j <= 4) {
                val tx = lines[j].text
                if (!tx.any { it.isDigit() } && looksLikeName(tx)) return clean(tx)
                j--
            }
        }
        return null
    }

    fun analyze(ctx: Context, uri: Uri, done: (ScanResult) -> Unit) {
        val image = try {
            InputImage.fromFilePath(ctx, uri)
        } catch (e: Exception) {
            done(ScanResult(emptyList(), null, null))
            return
        }
        val lines = ArrayList<Ln>()
        val codes = ArrayList<String>()
        var pending = 2

        fun finish() {
            pending--
            if (pending == 0) done(build(lines, codes))
        }

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { t ->
                for ((bi, b) in t.textBlocks.withIndex()) for (l in b.lines) {
                    val x = l.text.trim()
                    if (x.length >= 2) lines.add(Ln(x, bi))
                }
            }
            .addOnCompleteListener { finish() }

        BarcodeScanning.getClient().process(image)
            .addOnSuccessListener { list ->
                for (b in list) b.rawValue?.let { codes.add(it) }
            }
            .addOnCompleteListener { finish() }
    }

    private fun build(lines: List<Ln>, codes: List<String>): ScanResult {
        var tracking: String? = codes.mapNotNull { trackingIn(it) }.maxByOrNull { it.length }
        if (tracking == null) {
            tracking = lines.mapNotNull { trackingIn(it.text) }.maxByOrNull { it.length }
        }
        val name = nameByKeyword(lines) ?: nameByAddress(lines)
        val chips = lines.map { it.text }.distinct().filter { it.length <= 60 }.take(30)
        return ScanResult(chips, name, tracking)
    }
}
