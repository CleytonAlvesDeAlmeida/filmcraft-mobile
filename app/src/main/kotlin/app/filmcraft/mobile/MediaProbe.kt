package app.filmcraft.mobile

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

data class Probed(val name: String, val durationMs: Long)

/** Leitura de metadados e miniaturas com as APIs do Android (o núcleo Rust cuida só da edição). */
object MediaProbe {
    fun probe(context: Context, uri: Uri): Probed? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (duration == null || duration <= 0L) null else Probed(displayName(context, uri), duration)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) {
                    val n = c.getString(i)
                    if (!n.isNullOrBlank()) return n
                }
            }
        }
        return uri.lastPathSegment ?: "video"
    }

    fun thumbnail(context: Context, uri: Uri, atMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val h = 160
            val w = (frame.width * h / frame.height.coerceAtLeast(1)).coerceAtLeast(1)
            Bitmap.createScaledBitmap(frame, w, h, true)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
