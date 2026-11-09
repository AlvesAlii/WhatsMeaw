package ai.venice.vaultview

import android.content.Context
import android.provider.MediaStore

/** Consulta a galeria do sistema para saber se um arquivo foi indexado como mídia normal. */
object MediaIndex {
    fun contains(ctx: Context, path: String): Boolean {
        val cols = arrayOf(MediaStore.MediaColumns._ID)
        val sel = "${MediaStore.MediaColumns.DATA} = ?"
        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        )
        for (uri in uris) {
            runCatching {
                ctx.contentResolver.query(uri, cols, sel, arrayOf(path), null)?.use {
                    if (it.moveToFirst()) return true
                }
            }
        }
        return false
    }
}