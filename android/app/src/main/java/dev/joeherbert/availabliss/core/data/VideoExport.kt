package dev.joeherbert.availabliss.core.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.graphics.scale
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** A picked video ready to send: shrunk to at most 720p (H.264 MP4), with a frame from it as its poster. */
class PreparedVideo(val file: File, val name: String, val poster: ByteArray?, val posterFile: File?, val seconds: Double)

/** Why a video can't be sent, worded for the composer. */
class VideoExportException(message: String) : IOException(message)

/**
 * Shrinks videos before they're sent, like iOS: the server takes up to 3 minutes and 100 MB
 * (10 minutes and 300 MB with Pro), and 720p keeps most phone videos well under that.
 */
internal object VideoExport {
    /** Longest the app will even try (the server has the last word: 3 minutes without Pro). */
    const val MAX_SECONDS = 600

    private const val SHORT_SIDE = 720

    suspend fun prepare(context: Context, uri: Uri, name: String, onProgress: (Float) -> Unit): PreparedVideo {
        val info = withContext(Dispatchers.IO) { read(context, uri) } ?: throw VideoExportException("That video couldn't be read")
        if (info.seconds > MAX_SECONDS + 1) throw VideoExportException("Videos can be up to 10 minutes (3 minutes without Pro)")
        val dir = File(context.cacheDir, "videos").apply { mkdirs() }
        val output = File.createTempFile("video-", ".mp4", dir)
        try {
            transcode(context, uri, output, shrink = info.shortSide > SHORT_SIDE, onProgress)
        } catch (e: Exception) {
            output.delete()
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw VideoExportException("That video couldn't be prepared")
        }
        val poster = withContext(Dispatchers.IO) { poster(context, Uri.fromFile(output), info.seconds) }
        val posterFile = poster?.let { bytes -> File.createTempFile("poster-", ".jpg", dir).apply { writeBytes(bytes) } }
        val base = name.substringBeforeLast('.').ifBlank { "Video" }
        return PreparedVideo(output, "$base.mp4", poster, posterFile, info.seconds)
    }

    private class Info(val seconds: Double, val shortSide: Int)

    private fun read(context: Context, uri: Uri): Info? = MediaMetadataRetriever().useRetriever { r ->
        r.setDataSource(context, uri)
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return@useRetriever null
        val width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        if (ms <= 0) null else Info(ms / 1000.0, minOf(width, height))
    }

    /** Re-encodes as H.264 / AAC MP4, scaled so its short side is 720 px when [shrink]. Runs on the main thread, as Transformer needs. */
    @OptIn(UnstableApi::class)
    private suspend fun transcode(context: Context, uri: Uri, output: File, shrink: Boolean, onProgress: (Float) -> Unit) = withContext(Dispatchers.Main) {
        val done = CompletableDeferred<Unit>()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    done.complete(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    done.completeExceptionally(exportException)
                }
            })
            .build()
        val effects = if (shrink) Effects(emptyList(), listOf(Presentation.createForShortSide(SHORT_SIDE))) else Effects.EMPTY
        transformer.start(EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(effects).build(), output.absolutePath)
        val ticker = launch {
            val holder = ProgressHolder()
            while (true) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                delay(250)
            }
        }
        try {
            done.await()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) transformer.cancel()
            throw e
        } finally {
            ticker.cancel()
        }
    }

    /** A JPEG frame from near the start, at most 720 px on its long side. */
    private fun poster(context: Context, uri: Uri, seconds: Double): ByteArray? = MediaMetadataRetriever().useRetriever { r ->
        r.setDataSource(context, uri)
        val at = (minOf(0.5, seconds / 2) * 1_000_000).toLong()
        val frame = r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@useRetriever null
        val longest = maxOf(frame.width, frame.height)
        val scaled = if (longest > SHORT_SIDE) {
            val scale = SHORT_SIDE.toFloat() / longest
            frame.scale((frame.width * scale).toInt(), (frame.height * scale).toInt())
        } else {
            frame
        }
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }

    private inline fun <T> MediaMetadataRetriever.useRetriever(block: (MediaMetadataRetriever) -> T?): T? = try {
        block(this)
    } catch (_: Exception) {
        null
    } finally {
        if (Build.VERSION.SDK_INT >= 29) close() else release()
    }
}
