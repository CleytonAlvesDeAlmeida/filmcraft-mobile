package app.filmcraft.mobile.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import app.filmcraft.mobile.core.ClipView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exporta a linha do tempo para MP4 (H.264/AAC) com o Media3 Transformer, que usa os codecs de
 * hardware do aparelho. O resultado é salvo em Filmes/FilmCraft.
 *
 * O primeiro clipe define o formato de saída. Os demais são ajustados pelo Transformer.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object Exporter {
    suspend fun export(context: Context, clips: List<ClipView>, onProgress: (Float) -> Unit): Uri {
        require(clips.isNotEmpty()) { "A linha do tempo está vazia" }
        val appContext = context.applicationContext
        val cacheFile = File(appContext.cacheDir, "export-${System.currentTimeMillis()}.mp4")

        try {
            withContext(Dispatchers.Main) {
                coroutineScope {
                    val items = clips.map { c ->
                        val clipping = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(c.inMs)
                            .setEndPositionMs(c.outMs)
                            .build()
                        val mediaItem = MediaItem.Builder()
                            .setUri(c.uri)
                            .setClippingConfiguration(clipping)
                            .build()
                        EditedMediaItem.Builder(mediaItem).setRemoveAudio(c.muted).build()
                    }
                    val sequence = EditedMediaItemSequence.Builder(*items.toTypedArray()).build()
                    val composition = Composition.Builder(sequence).build()

                    val finished = CompletableDeferred<Unit>()
                    val transformer = Transformer.Builder(appContext)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                finished.complete(Unit)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                finished.completeExceptionally(exportException)
                            }
                        })
                        .build()

                    transformer.start(composition, cacheFile.absolutePath)

                    val holder = ProgressHolder()
                    val poller = launch {
                        while (isActive) {
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(holder.progress / 100f)
                            }
                            delay(250)
                        }
                    }
                    try {
                        finished.await()
                    } catch (e: CancellationException) {
                        transformer.cancel()
                        throw e
                    } finally {
                        poller.cancel()
                    }
                }
            }
            return withContext(Dispatchers.IO) { saveToMovies(appContext, cacheFile) }
        } finally {
            cacheFile.delete()
        }
    }

    private fun saveToMovies(context: Context, file: File): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "FilmCraft-$stamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FilmCraft")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Não foi possível criar o arquivo em Filmes")
        val out = resolver.openOutputStream(uri) ?: throw IOException("Não foi possível gravar o vídeo")
        out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
