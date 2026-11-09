package ai.venice.vaultview

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Observa as pastas de mídia do WhatsApp, incluindo .Statuses, onde ficam as
 * mídias de visualização única. Copia o arquivo imediatamente, porque o
 * WhatsApp apaga o original assim que a prévia é fechada.
 */
class MediaWatcher(
    private val ctx: Context,
    private val repo: VaultRepo,
    private val onNew: () -> Unit,
) {
    private val observers = mutableListOf<FileObserver>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val inFlight = ConcurrentHashMap<String, Boolean>()
    private var mediaObserver: ContentObserver? = null

    private val roots: List<File>
        get() {
            val base = Environment.getExternalStorageDirectory()
            val pkgs = listOf("com.whatsapp", "com.whatsapp.w4b")
            val subs = listOf(
                ".Statuses",
                "WhatsApp Images",
                "WhatsApp Video",
                "WhatsApp Audio",
                "WhatsApp Voice Notes",
                "WhatsApp Animated Gifs",
            )
            return pkgs.flatMap { p ->
                subs.map { File(base, "Android/media/$p/Media/$it") }
            }
        }

    fun start() {
        roots.forEach { register(it) }
        startMediaStore()
    }

    private fun register(dir: File) {
        if (!dir.isDirectory) dir.mkdirs()
        if (!dir.isDirectory) return
        @Suppress("DEPRECATION")
        val o = object : FileObserver(
            dir.absolutePath,
            CREATE or CLOSE_WRITE or MOVED_TO,
        ) {
            override fun onEvent(event: Int, name: String?) {
                if (name == null) return
                if (name.startsWith(".") || name.endsWith(".nomedia") || name.endsWith(".enc")) return
                enqueue(File(dir, name))
            }
        }
        o.startWatching()
        observers += o
    }

    private fun startMediaStore() {
        val handler = Handler(Looper.getMainLooper())
        val o = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scope.launch { sweep() }
            }
        }
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        ).forEach { ctx.contentResolver.registerContentObserver(it, true, o) }
        mediaObserver = o
    }

    private suspend fun sweep() {
        val since = (System.currentTimeMillis() / 1000) - 20
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).forEach { uri ->
            runCatching {
                ctx.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATA),
                    "${MediaStore.MediaColumns.DATE_ADDED} > ?",
                    arrayOf(since.toString()),
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val p = c.getString(0) ?: continue
                        if (p.contains("com.whatsapp")) enqueue(File(p))
                    }
                }
            }
        }
    }

    private fun enqueue(file: File) {
        if (inFlight.putIfAbsent(file.absolutePath, true) != null) return
        scope.launch {
            try {
                if (!awaitReady(file)) return@launch
                if (repo.ingest(file) { !indexedWithRetry(file.absolutePath) }) onNew()
            } finally {
                inFlight.remove(file.absolutePath)
            }
        }
    }

    /** Espera curta só para o arquivo deixar de estar vazio. */
    private suspend fun awaitReady(f: File): Boolean {
        repeat(24) {
            if (!f.exists()) return false
            if (f.length() > 0L) return true
            delay(50)
        }
        return f.exists() && f.length() > 0L
    }

    /** O MediaStore demora a indexar; sem retry tudo vira "única" por engano. */
    private suspend fun indexedWithRetry(path: String): Boolean {
        repeat(3) {
            if (MediaIndex.contains(ctx, path)) return true
            delay(250)
        }
        return false
    }

    fun stop() {
        observers.forEach { runCatching { it.stopWatching() } }
        observers.clear()
        mediaObserver?.let { runCatching { ctx.contentResolver.unregisterContentObserver(it) } }
        mediaObserver = null
    }
}
