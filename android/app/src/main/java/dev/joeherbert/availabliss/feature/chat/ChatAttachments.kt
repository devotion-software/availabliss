package dev.joeherbert.availabliss.feature.chat

import android.net.Uri
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.AttachmentFiles
import dev.joeherbert.availabliss.core.data.PreparedVideo
import dev.joeherbert.availabliss.core.data.VideoExportException
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Attachment
import dev.joeherbert.availabliss.core.network.model.AttachmentKind
import dev.joeherbert.availabliss.designsystem.component.chat.AttachmentUiKind
import dev.joeherbert.availabliss.designsystem.component.chat.MAX_ATTACHMENTS
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Photos and files: picked and uploaded in the composer, and opened, shared or saved from messages. */
internal class ChatAttachments(
    private val session: ChatSession,
    private val files: AttachmentFiles,
) {
    private val data get() = session.data
    private val uploadJobs = mutableMapOf<String, Job>()

    /** Photos or files picked to send: each starts uploading straight away (up to [MAX_ATTACHMENTS] per message). */
    fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val room = MAX_ATTACHMENTS - data.value.pending.size
        val taken = uris.distinct().take(room.coerceAtLeast(0))
        val entries = taken.map { PendingUpload(localId = UUID.randomUUID().toString(), uri = it) }
        data.update {
            it.copy(
                pending = it.pending + entries,
                menuOpen = false,
                message = if (taken.size < uris.distinct().size) UserMessage("You can send up to $MAX_ATTACHMENTS photos and files at a time") else it.message,
            )
        }
        entries.forEach { upload(it.localId) }
    }

    fun removeAttachment(localId: String) {
        uploadJobs.remove(localId)?.cancel()
        data.update { d -> d.copy(pending = d.pending.filterNot { it.localId == localId }) }
    }

    fun retryAttachment(localId: String) {
        updatePending(localId) { it.copy(error = null, progress = 0f) }
        upload(localId)
    }

    private fun updatePending(localId: String, transform: (PendingUpload) -> PendingUpload) = data.update { d ->
        d.copy(pending = d.pending.map { if (it.localId == localId) transform(it) else it })
    }

    private fun upload(localId: String) {
        uploadJobs.remove(localId)?.cancel()
        uploadJobs[localId] = session.scope.launch {
            val entry = data.value.pending.firstOrNull { it.localId == localId } ?: return@launch
            val file = entry.file ?: runCatching { files.describe(entry.uri) }.getOrNull()
            if (file == null) {
                updatePending(localId) { it.copy(error = "Couldn't read this file") }
                return@launch
            }
            val kind = when {
                file.isVideo -> AttachmentUiKind.Video
                file.isGif -> AttachmentUiKind.Gif
                file.isImage -> AttachmentUiKind.Image
                else -> AttachmentUiKind.File
            }
            updatePending(localId) { it.copy(file = file, kind = kind, name = file.name) }
            uploadLimitError(file.mime, file.size)?.let { error ->
                updatePending(localId) { it.copy(error = error) }
                return@launch
            }
            var reported = -1
            // A video is converted first (the first half of its progress), then uploaded.
            val share = if (file.isVideo) 0.5f else 1f
            fun report(fraction: Float, base: Float) {
                val percent = ((base + fraction * share) * 100).toInt().coerceIn(0, 100)
                if (percent != reported) {
                    reported = percent
                    updatePending(localId) { if (it.uploaded == null && it.error == null) it.copy(progress = percent / 100f) else it }
                }
            }
            val onBytes = { written: Long, total: Long -> if (total > 0) report(written.toFloat() / total, 1f - share) }
            var video: PreparedVideo? = null
            val source = if (file.isVideo) {
                val prepared = try {
                    files.prepareVideo(file, onConverting = { report(it, 0f) }, onProgress = onBytes)
                } catch (e: VideoExportException) {
                    updatePending(localId) { it.copy(error = e.message) }
                    return@launch
                }
                video = prepared.first
                updatePending(localId) { it.copy(poster = prepared.first.posterFile) }
                prepared.second
            } else {
                files.prepare(file, onBytes)
            }
            if (source == null) {
                updatePending(localId) { it.copy(error = "Couldn't read this photo") }
                return@launch
            }
            try {
                when (val result = session.chat.upload(session.groupId, source.name, source.body, video?.poster)) {
                    is ApiResult.Success -> updatePending(localId) { it.copy(uploaded = result.value, progress = 1f) }
                    is ApiResult.Failure -> updatePending(localId) { it.copy(error = result.error.message) }
                }
            } finally {
                video?.file?.delete()
            }
            uploadJobs.remove(localId)
        }
    }

    /** Taps on a photo (opens the viewer over the message's photos) or a file (downloads and opens it). */
    fun openAttachment(messageId: String, attachmentId: String) {
        val m = data.value.allMessages.firstOrNull { it.id == messageId } ?: return
        val a = m.attachments.firstOrNull { it.id == attachmentId } ?: return
        if (a.isVisual) {
            val visuals = m.attachments.filter { it.isVisual }
            viewPictures(ViewerUi(visuals, visuals.indexOf(a).coerceAtLeast(0)))
        } else {
            openFile(a)
        }
    }

    fun viewPictures(viewer: ViewerUi) = data.update { it.copy(viewer = viewer, menuOpen = false) }

    /** Downloads a file and opens it in an app that can. */
    fun openFile(a: Attachment) = withDownload(a) { file ->
        if (!files.open(file, a.mime)) {
            val why = if (a.kind == AttachmentKind.Pass) {
                "No app on this phone opens Wallet passes. Google Wallet can't add Apple Wallet passes directly: try an app that supports .pkpass files"
            } else {
                "No app on this phone can open ${a.name}"
            }
            data.update { it.copy(message = UserMessage(why)) }
        }
    }

    fun closeViewer() = data.update { it.copy(viewer = null) }

    fun shareAttachment(attachment: Attachment) = withDownload(attachment) { file -> files.share(file, attachment.mime) }

    fun saveAttachment(attachment: Attachment) = withDownload(attachment) { file ->
        val saved = files.saveToPictures(file, attachment.mime)
        val video = attachment.kind == AttachmentKind.Video
        val message = when {
            saved -> if (video) "Saved to Movies" else "Saved to Pictures"
            else -> if (video) "Couldn't save the video" else "Couldn't save the photo"
        }
        data.update { it.copy(message = UserMessage(message)) }
    }

    /** Downloads [attachment] to the cache (once at a time per attachment), then runs [then]. */
    private fun withDownload(attachment: Attachment, then: suspend (java.io.File) -> Unit) {
        if (attachment.id in data.value.downloads) return
        data.update { it.copy(downloads = it.downloads + (attachment.id to -1f)) }
        session.scope.launch {
            var reported = -1
            val result = runCatching {
                files.download(attachment) { p ->
                    val percent = (p * 100).toInt()
                    if (percent != reported) {
                        reported = percent
                        data.update { d -> if (attachment.id in d.downloads) d.copy(downloads = d.downloads + (attachment.id to p)) else d }
                    }
                }
            }
            data.update { it.copy(downloads = it.downloads - attachment.id) }
            result.fold(
                onSuccess = { then(it) },
                onFailure = { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    data.update { it.copy(message = UserMessage("Couldn't download ${attachment.name}")) }
                },
            )
        }
    }
}

private const val MAX_PHOTO_BYTES = 15L * 1024 * 1024
private const val MAX_FILE_BYTES = 25L * 1024 * 1024

/**
 * Why a picked file of [mime] and [size] bytes is too big to send, or null. GIFs are sent as they are;
 * other photos are shrunk on the device when they're over the limit, so only GIFs and files are checked.
 */
fun uploadLimitError(mime: String, size: Long?): String? {
    if (size == null) return null
    return when {
        mime == "image/gif" && size > MAX_PHOTO_BYTES -> "GIFs can be up to 15 MB"
        // Videos are shrunk to 720p before sending; their length is checked then.
        mime.startsWith("video/") -> null
        !mime.startsWith("image/") && size > MAX_FILE_BYTES -> "Files can be up to 25 MB"
        else -> null
    }
}
