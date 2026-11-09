package ai.venice.vaultview

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class VaultRepo(private val ctx: Context) {

    private val db = VaultDb(ctx)
    val dir: File = File(ctx.getExternalFilesDir(null), "vault").apply { mkdirs() }

    fun all(onlySuspicious: Boolean): List<Item> {
        val out = mutableListOf<Item>()
        val where = if (onlySuspicious) "WHERE suspicious = 1" else ""
        db.readableDatabase.rawQuery(
            "SELECT id, path, name, mime, kind, size, ts, suspicious FROM media $where ORDER BY ts DESC LIMIT 2000",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += Item(
                    c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getLong(5), c.getLong(6), c.getInt(7) == 1,
                )
            }
        }
        return out
    }

    suspend fun ingest(src: File, suspicious: Boolean): Boolean = withContext(Dispatchers.IO) {
        val sha = runCatching { sha256(src) }.getOrNull() ?: return@withContext false
        if (exists(sha)) return@withContext false

        val ext = src.extension.ifBlank { "bin" }
        val dest = File(dir, "${System.currentTimeMillis()}_${sha.take(10)}.$ext")
        val ok = runCatching { src.copyTo(dest, overwrite = true) }
            .recoverCatching { Thread.sleep(250); src.copyTo(dest, overwrite = true) }
            .isSuccess
        if (!ok) return@withContext false

        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        db.writableDatabase.insert("media", null, ContentValues().apply {
            put("path", dest.absolutePath)
            put("name", dest.name)
            put("mime", mime)
            put("kind", when {
                mime.startsWith("image") -> "image"
                mime.startsWith("video") -> "video"
                mime.startsWith("audio") -> "audio"
                else -> "file"
            })
            put("size", dest.length())
            put("sha", sha)
            put("ts", System.currentTimeMillis())
            put("suspicious", if (suspicious) 1 else 0)
        })
        true
    }

    /** Exporta a cópia interna para a galeria ou para Download/VaultView. */
    suspend fun saveToPublic(item: Item): Uri? = withContext(Dispatchers.IO) {
        val src = File(item.path)
        if (!src.exists() || src.length() == 0L) return@withContext null

        val mime = item.mime ?: "application/octet-stream"
        val name = exportName(item)
        val toGallery = item.kind == "image" || item.kind == "video"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = when {
                item.kind == "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                item.kind == "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
            val relative = when (item.kind) {
                "image" -> "Pictures/VaultView"
                "video" -> "Movies/VaultView"
                else -> "Download/VaultView"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = runCatching { ctx.contentResolver.insert(collection, values) }.getOrNull()
                ?: return@withContext null
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
            }.onFailure { return@withContext null }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            ctx.contentResolver.update(uri, values, null, null)
            return@withContext uri
        }

        @Suppress("DEPRECATION")
        val base = if (toGallery) {
            Environment.getExternalStoragePublicDirectory(
                if (item.kind == "video") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
            )
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
        val target = File(base, "VaultView").apply { mkdirs() }
        val out = File(target, name)
        runCatching { src.copyTo(out, overwrite = true) }.getOrNull() ?: return@withContext null
        MediaScannerConnection.scanFile(ctx, arrayOf(out.absolutePath), arrayOf(mime), null)
        return@withContext Uri.fromFile(out)
    }

    fun exportDirHint(item: Item): String = when (item.kind) {
        "image" -> "Pictures/VaultView"
        "video" -> "Movies/VaultView"
        else -> "Download/VaultView"
    }

    private fun exportName(item: Item): String {
        val ext = item.name.substringAfterLast('.', "bin")
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        return "VV_${stamp}_${item.id}.$ext"
    }

    fun markSuspicious(id: Long, value: Boolean) {
        db.writableDatabase.execSQL("UPDATE media SET suspicious = ? WHERE id = ?", arrayOf(if (value) 1 else 0, id))
    }

    fun delete(item: Item) {
        File(item.path).delete()
        db.writableDatabase.delete("media", "id = ?", arrayOf(item.id.toString()))
    }

    fun uriOf(item: Item): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(item.path))

    private fun exists(sha: String): Boolean =
        db.readableDatabase.rawQuery("SELECT 1 FROM media WHERE sha = ?", arrayOf(sha)).use { it.moveToFirst() }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    data class Item(
        val id: Long, val path: String, val name: String, val mime: String?,
        val kind: String?, val size: Long, val ts: Long, val suspicious: Boolean,
    )
}