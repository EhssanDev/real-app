package dev.ehssan.parcels

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.io.File

/** Keeps every worker's phone in step through Firestore. Local SQLite stays the store the UI reads from. */
object Sync {

    private const val API_KEY = "AIzaSyBt1jd7DLZupxxKHao8I14FXa00jpSKDVw"
    private const val APP_ID = "1:692424827243:android:6e7a26910daab9ae026c2e"
    private const val PROJECT = "parcel-desk-kiosk"

    private val main = Handler(Looper.getMainLooper())
    private var listener: ListenerRegistration? = null
    private var ready = false
    private var firstSnapshot = true
    private val pending = ArrayList<() -> Unit>()

    /** Called on the main thread after local data changed because of the cloud. */
    var onChange: (() -> Unit)? = null
    var onNewParcel: ((Parcel) -> Unit)? = null
    var onState: ((String) -> Unit)? = null

    private fun cfg(ctx: Context) = ctx.getSharedPreferences("pd_cfg", Context.MODE_PRIVATE)

    fun kiosk(ctx: Context): String = cfg(ctx).getString("kiosk", "") ?: ""
    fun who(ctx: Context): String = cfg(ctx).getString("who", "") ?: ""
    fun enabled(ctx: Context) = kiosk(ctx).isNotEmpty()
    fun skipped(ctx: Context) = cfg(ctx).getBoolean("skip_sync", false)

    fun normCode(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9\\u0600-\\u06FF_-]"), "")

    fun connect(ctx: Context, code: String, who: String) {
        cfg(ctx).edit().putString("kiosk", code).putString("who", who).putBoolean("skip_sync", false).apply()
    }

    fun skip(ctx: Context) {
        cfg(ctx).edit().putBoolean("skip_sync", true).apply()
    }

    fun disconnect(ctx: Context) {
        stop()
        cfg(ctx).edit().remove("kiosk").putBoolean("skip_sync", true).apply()
    }

    private fun init(ctx: Context) {
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            FirebaseApp.initializeApp(
                ctx.applicationContext,
                FirebaseOptions.Builder().setApiKey(API_KEY).setApplicationId(APP_ID).setProjectId(PROJECT).build()
            )
        }
    }

    private fun root(ctx: Context) =
        FirebaseFirestore.getInstance().collection("kiosks").document(kiosk(ctx))

    fun stop() {
        listener?.remove()
        listener = null
        ready = false
        firstSnapshot = true
    }

    fun start(ctx: Context, store: Db) {
        stop()
        if (!enabled(ctx)) return
        val app = ctx.applicationContext
        try {
            init(app)
        } catch (e: Exception) {
            onState?.invoke("تعذر تشغيل المزامنة")
            return
        }
        val auth = FirebaseAuth.getInstance()
        val go = {
            attach(app, store)
        }
        if (auth.currentUser != null) {
            go()
        } else {
            onState?.invoke("جارٍ الاتصال...")
            auth.signInAnonymously()
                .addOnSuccessListener { go() }
                .addOnFailureListener { onState?.invoke("تحتاج إنترنت لأول اتصال") }
        }
    }

    private fun attach(ctx: Context, store: Db) {
        ready = true
        onState?.invoke("متصل")
        val code = kiosk(ctx)
        val flag = "pushed_$code"
        if (!cfg(ctx).getBoolean(flag, false)) {
            for (p in store.all(false) + store.all(true)) push(ctx, p)
            cfg(ctx).edit().putBoolean(flag, true).apply()
        }
        val run = ArrayList(pending)
        pending.clear()
        for (r in run) r()

        firstSnapshot = true
        listener = root(ctx).collection("parcels").addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                onState?.invoke("غير متصل")
                return@addSnapshotListener
            }
            var changed = false
            for (dc in snap.documentChanges) {
                val doc = dc.document
                val gid = doc.id
                if (dc.type == DocumentChange.Type.REMOVED) {
                    val old = store.deleteByGid(gid)
                    if (old != null) {
                        if (old.photo.isNotEmpty()) Img.file(ctx, old.photo).delete()
                        if (old.label.isNotEmpty()) Img.file(ctx, old.label).delete()
                        changed = true
                    }
                    continue
                }
                if (doc.metadata.hasPendingWrites()) continue
                val p = Parcel(
                    0, doc.getString("name") ?: "", doc.getString("tracking") ?: "",
                    doc.getString("shelf") ?: "", doc.getString("note") ?: "",
                    doc.getString("photo") ?: "", doc.getString("label") ?: "",
                    doc.getLong("created") ?: 0L, doc.getLong("delivered") ?: 0L,
                    gid, doc.getLong("updated") ?: 0L, doc.getString("by") ?: ""
                )
                val r = store.upsertRemote(p)
                if (r != 0) {
                    changed = true
                    fetchImage(ctx, p.photo, 0)
                    fetchImage(ctx, p.label, 0)
                    if (r == 1 && !firstSnapshot && p.delivered == 0L) onNewParcel?.invoke(p)
                }
            }
            firstSnapshot = false
            onState?.invoke(if (snap.metadata.isFromCache) "غير متصل (يعمل محلياً)" else "متصل")
            if (changed) onChange?.invoke()
        }
    }

    private fun whenReady(r: () -> Unit) {
        if (ready) r() else pending.add(r)
    }

    fun push(ctx: Context, p: Parcel) {
        if (!enabled(ctx) || p.gid.isEmpty()) return
        val app = ctx.applicationContext
        whenReady {
            uploadImage(app, p.photo)
            uploadImage(app, p.label)
            val m = HashMap<String, Any>()
            m["name"] = p.name
            m["tracking"] = p.tracking
            m["shelf"] = p.shelf
            m["note"] = p.note
            m["photo"] = p.photo
            m["label"] = p.label
            m["created"] = p.created
            m["delivered"] = p.delivered
            m["updated"] = p.updated
            m["by"] = p.by
            root(app).collection("parcels").document(p.gid).set(m)
        }
    }

    fun remove(ctx: Context, p: Parcel) {
        if (!enabled(ctx) || p.gid.isEmpty()) return
        val app = ctx.applicationContext
        whenReady {
            root(app).collection("parcels").document(p.gid).delete()
            removeImage(app, p.photo)
            removeImage(app, p.label)
        }
    }

    fun removeImage(ctx: Context, name: String) {
        if (!enabled(ctx) || name.isEmpty()) return
        val app = ctx.applicationContext
        whenReady { root(app).collection("images").document(name).delete() }
    }

    private fun uploadImage(ctx: Context, name: String) {
        if (name.isEmpty()) return
        val f = Img.file(ctx, name)
        if (!f.exists()) return
        Thread {
            try {
                val data = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                val m = HashMap<String, Any>()
                m["data"] = data
                root(ctx).collection("images").document(name).set(m)
            } catch (e: Throwable) {
            }
        }.start()
    }

    private fun fetchImage(ctx: Context, name: String, attempt: Int) {
        if (name.isEmpty()) return
        val f: File = Img.file(ctx, name)
        if (f.exists()) return
        root(ctx).collection("images").document(name).get()
            .addOnSuccessListener { d ->
                val data = d.getString("data")
                if (data == null) {
                    if (attempt < 4) main.postDelayed({ fetchImage(ctx, name, attempt + 1) }, 4000)
                    return@addOnSuccessListener
                }
                Thread {
                    try {
                        val tmp = File(f.parentFile, name + ".tmp")
                        tmp.writeBytes(Base64.decode(data, Base64.DEFAULT))
                        tmp.renameTo(f)
                        main.post { onChange?.invoke() }
                    } catch (e: Throwable) {
                    }
                }.start()
            }
            .addOnFailureListener {
                if (attempt < 4) main.postDelayed({ fetchImage(ctx, name, attempt + 1) }, 4000)
            }
    }
}
