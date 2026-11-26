package dev.ehssan.parcels

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.Normalizer

class Parcel(
    val id: Long,
    var name: String,
    var tracking: String,
    var shelf: String,
    var note: String,
    var photo: String,
    var label: String,
    val created: Long,
    var delivered: Long,
    var gid: String = "",
    var updated: Long = 0,
    var by: String = ""
)

class Db(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "parcels.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE parcels(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, tracking TEXT, " +
                "shelf TEXT, note TEXT, photo TEXT, label TEXT, created INTEGER, delivered INTEGER, " +
                "gid TEXT, updated INTEGER DEFAULT 0, addedBy TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE parcels ADD COLUMN gid TEXT")
            db.execSQL("ALTER TABLE parcels ADD COLUMN updated INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE parcels ADD COLUMN addedBy TEXT")
            val ids = ArrayList<Long>()
            db.rawQuery("SELECT id FROM parcels", null).use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
            for (i in ids) {
                db.execSQL(
                    "UPDATE parcels SET gid=?, updated=created WHERE id=?",
                    arrayOf<Any>(java.util.UUID.randomUUID().toString(), i)
                )
            }
        }
    }

    private fun values(p: Parcel): ContentValues {
        val v = ContentValues()
        v.put("name", p.name)
        v.put("tracking", p.tracking)
        v.put("shelf", p.shelf)
        v.put("note", p.note)
        v.put("photo", p.photo)
        v.put("label", p.label)
        v.put("created", p.created)
        v.put("delivered", p.delivered)
        v.put("gid", p.gid)
        v.put("updated", p.updated)
        v.put("addedBy", p.by)
        return v
    }

    private fun read(c: Cursor) = Parcel(
        c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "",
        c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "", c.getLong(7), c.getLong(8),
        c.getString(9) ?: "", c.getLong(10), c.getString(11) ?: ""
    )

    private val cols = "id,name,tracking,shelf,note,photo,label,created,delivered,gid,updated,addedBy"

    fun insert(p: Parcel): Long = writableDatabase.insert("parcels", null, values(p))

    fun update(p: Parcel) {
        writableDatabase.update("parcels", values(p), "id=?", arrayOf(p.id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("parcels", "id=?", arrayOf(id.toString()))
    }

    fun getByGid(gid: String): Parcel? {
        readableDatabase.rawQuery("SELECT $cols FROM parcels WHERE gid=?", arrayOf(gid)).use { c ->
            return if (c.moveToFirst()) read(c) else null
        }
    }

    /** Applies a parcel that came from the cloud. Returns 1 if inserted, 2 if updated, 0 if ignored. */
    fun upsertRemote(p: Parcel): Int {
        val old = getByGid(p.gid)
        if (old == null) {
            writableDatabase.insert("parcels", null, values(p))
            return 1
        }
        if (p.updated < old.updated) return 0
        val p2 = Parcel(old.id, p.name, p.tracking, p.shelf, p.note, p.photo, p.label, p.created, p.delivered, p.gid, p.updated, p.by)
        update(p2)
        return 2
    }

    fun deleteByGid(gid: String): Parcel? {
        val old = getByGid(gid) ?: return null
        delete(old.id)
        return old
    }

    fun get(id: Long): Parcel? {
        readableDatabase.rawQuery("SELECT $cols FROM parcels WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) read(c) else null
        }
    }

    fun all(delivered: Boolean): List<Parcel> {
        val where = if (delivered) "delivered>0" else "delivered=0"
        val order = if (delivered) "delivered DESC" else "created DESC"
        val out = ArrayList<Parcel>()
        readableDatabase.rawQuery("SELECT $cols FROM parcels WHERE $where ORDER BY $order", null).use { c ->
            while (c.moveToNext()) out.add(read(c))
        }
        return out
    }

    /** Removes delivered parcels delivered before [before]; returns the removed rows so their photos can be deleted. */
    fun purgeDelivered(before: Long): List<Parcel> {
        val out = ArrayList<Parcel>()
        readableDatabase.rawQuery(
            "SELECT $cols FROM parcels WHERE delivered>0 AND delivered<?", arrayOf(before.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(read(c))
        }
        for (p in out) delete(p.id)
        return out
    }

    fun clearAll(): List<Parcel> {
        val out = ArrayList<Parcel>()
        readableDatabase.rawQuery("SELECT $cols FROM parcels", null).use { c ->
            while (c.moveToNext()) out.add(read(c))
        }
        writableDatabase.delete("parcels", null, null)
        return out
    }
}

/** Typo-tolerant search over names, tracking numbers, shelf spots and notes. */
object Search {

    private val marks = Regex("\\p{M}+")
    private val splitter = Regex("[^\\p{L}\\p{N}]+")

    fun norm(s: String): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "")
        t = t.replace("ß", "ss").replace("ø", "o").replace("æ", "ae").replace("ı", "i")
        t = t.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ى", "ي").replace("ة", "ه")
        // German digraphs (mueller == muller == müller)
        t = t.replace("ae", "a").replace("oe", "o").replace("ue", "u")
        return t
    }

    private fun tokens(s: String): List<String> = norm(s).split(splitter).filter { it.isNotEmpty() }

    private fun lev(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }

    private fun allowed(len: Int) = if (len >= 7) 2 else if (len >= 4) 1 else 0

    /** Cost of matching [q] against one candidate word, or -1 if it does not match. */
    private fun cost(q: String, t: String): Int {
        if (t.startsWith(q)) return 0
        if (q.length >= 3 && t.contains(q)) return 1
        val a = allowed(q.length)
        if (a == 0) return -1
        val prefix = t.substring(0, minOf(t.length, q.length))
        val d = minOf(lev(q, prefix), lev(q, t))
        return if (d <= a) 1 + d else -1
    }

    fun filter(list: List<Parcel>, query: String): List<Parcel> {
        val qs = tokens(query)
        if (qs.isEmpty()) return list
        val scored = ArrayList<Pair<Int, Parcel>>()
        for (p in list) {
            val words = tokens(p.name) + tokens(p.tracking) + tokens(p.shelf) + tokens(p.note)
            var total = 0
            var ok = true
            for (q in qs) {
                var best = -1
                for (w in words) {
                    val c = cost(q, w)
                    if (c >= 0 && (best < 0 || c < best)) best = c
                }
                if (best < 0) {
                    ok = false
                    break
                }
                total += best
            }
            if (ok) scored.add(total to p)
        }
        return scored.sortedWith(compareBy({ it.first }, { -it.second.created })).map { it.second }
    }
}
