package dev.joeherbert.availabliss.core.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.joeherbert.availabliss.core.network.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import okio.source
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A file picked to send: what the content resolver says about it. */
data class PickedFile(val uri: Uri, val name: String, val mime: String, val size: Long?) {
    val isImage: Boolean get() = mime.startsWith("image/")
    val isGif: Boolean get() = mime == "image/gif"
}

/** A picked file ready to upload: the name to send it as and its body. */
class UploadSource(val name: String, val body: RequestBody)

/**
 * Chat attachments on the device: reading picked files for upload (HEIC photos become
 * JPEG), downloading sent ones to the cache, and opening, sharing or saving them.
 */
@Singleton
class AttachmentFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val resolver get() = context.contentResolver

    /** Name, type and size of a picked [uri]. */
    suspend fun describe(uri: Uri): PickedFile = withContext(Dispatchers.IO) {
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
        }
        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val mime = resolver.getType(uri)?.takeIf { it != "application/octet-stream" }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        val fallbackName = "attachment" + (MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { ".$it" } ?: "")
        PickedFile(uri, name?.takeIf { it.isNotBlank() } ?: fallbackName, mime, size)
    }

    /**
     * The body to upload [file] with, reporting bytes written to [onProgress]. HEIC/HEIF photos,
     * and photos over the server's limit, are re-encoded as JPEG; GIFs and everything else are sent
     * as they are. Null if a photo that needed converting couldn't be read.
     */
    suspend fun prepare(file: PickedFile, onProgress: (written: Long, total: Long) -> Unit): UploadSource? = withContext(Dispatchers.IO) {
        val heic = file.mime == "image/heic" || file.mime == "image/heif" ||
            file.name.lowercase().let { it.endsWith(".heic") || it.endsWith(".heif") }
        val tooBig = file.isImage && !file.isGif && (file.size ?: 0) > MAX_PHOTO_BYTES
        val source = if (file.isImage && !file.isGif && (heic || tooBig)) {
            val jpeg = photoAsJpeg(file.uri, if (tooBig) MAX_CONVERTED_SIDE else null) ?: return@withContext null
            UploadSource(file.name.substringBeforeLast('.') + ".jpg", jpeg.toRequestBody("image/jpeg".toMediaTypeOrNull()))
        } else {
            UploadSource(file.name, UriRequestBody(file.uri, file.mime.toMediaTypeOrNull(), file.size ?: -1))
        }
        UploadSource(source.name, ProgressRequestBody(source.body, onProgress))
    }

    private fun photoAsJpeg(uri: Uri, maxSide: Int?): ByteArray? = runCatching {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (maxSide != null && longest > maxSide) {
                    val scale = maxSide.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            if (maxSide != null) while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }.getOrNull()

    /** Downloads [attachment] into the cache (once), reporting progress from 0 to 1 when the size is known. */
    suspend fun download(attachment: Attachment, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "$CACHE_DIR/${attachment.id}").apply { mkdirs() }
        val target = File(dir, safeFileName(attachment.name.ifBlank { "attachment" }))
        if (target.exists() && target.length() > 0 && (attachment.size <= 0 || target.length() == attachment.size)) return@withContext target
        val partial = File(dir, target.name + ".part")
        client.newCall(Request.Builder().url(attachment.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: attachment.size
            body.source().use { source ->
                partial.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    val input = source.inputStream()
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (!partial.renameTo(target)) throw IOException("Couldn't save the file")
        target
    }

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Opens a downloaded file in another app. False if nothing on the phone can open it. */
    fun open(file: File, mime: String): Boolean {
        val uri = uriFor(file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** The share sheet for a downloaded file. */
    fun share(file: File, mime: String) {
        val uri = uriFor(file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Copies a downloaded photo into Pictures/Availabliss (Android 10+, no permission needed). */
    suspend fun saveToPictures(file: File, mime: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Availabliss")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("No stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            false
        }
    }

    /** Streams a content [uri] (re-opened on each write, so OkHttp can retry). */
    private inner class UriRequestBody(private val uri: Uri, private val type: MediaType?, private val length: Long) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = length
        override fun writeTo(sink: BufferedSink) {
            val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't read the file")
            input.source().use { sink.writeAll(it) }
        }
    }

    companion object {
        const val CACHE_DIR = "attachments"
        /** The server's limit for photos; bigger ones are shrunk before uploading. */
        const val MAX_PHOTO_BYTES = 15L * 1024 * 1024
        private const val MAX_CONVERTED_SIDE = 4096

        /** A name safe to use as a file name in the cache. */
        fun safeFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().take(120).ifBlank { "attachment" }
    }
}

/** Wraps a body, reporting bytes written as it goes out. */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (written: Long, total: Long) -> Unit,
) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun isOneShot() = delegate.isOneShot()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        val counting = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                onProgress(written, total)
            }
        }.buffer()
        delegate.writeTo(counting)
        counting.flush()
    }
}
