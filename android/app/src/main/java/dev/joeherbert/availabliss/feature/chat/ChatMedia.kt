package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Attachment
import dev.joeherbert.availabliss.core.network.model.GroupMediaItem
import dev.joeherbert.availabliss.designsystem.component.chat.formatFileSize
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Photos and files sheet's tabs, by the server's `kind`. */
enum class MediaKind(val wire: String, val title: String) {
    Media("media", "Photos"),
    Files("files", "Files"),
    Passes("passes", "Passes"),
}

internal data class MediaPage(
    val items: List<GroupMediaItem> = emptyList(),
    val hasMore: Boolean = false,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

internal data class MediaSession(
    val kind: MediaKind = MediaKind.Media,
    val pages: Map<MediaKind, MediaPage> = emptyMap(),
    /** Whether the chat has any passes (from the first page of any tab): the Passes tab only shows then. */
    val hasPasses: Boolean = false,
) {
    fun page(k: MediaKind = kind) = pages[k] ?: MediaPage()
}

data class MediaItemUi(
    val attachment: Attachment,
    val messageId: String,
    /** "Mo · Today" (photos), "13 KB · Mo · Today" (files). */
    val meta: String,
)

data class ChatMediaUi(
    val kind: MediaKind,
    /** The tabs to show: Passes only when the chat has any. */
    val kinds: List<MediaKind>,
    val items: List<MediaItemUi>,
    val hasMore: Boolean,
    val loaded: Boolean,
    val loading: Boolean,
    val failed: Boolean,
)

internal fun MediaSession.toUi(directory: MemberDirectory): ChatMediaUi {
    val p = page()
    return ChatMediaUi(
        kind = kind,
        kinds = if (hasPasses || kind == MediaKind.Passes) MediaKind.entries else listOf(MediaKind.Media, MediaKind.Files),
        items = p.items.map { item ->
            val who = replyAuthorName(item.authorId, null, directory)
            val day = DateTimes.localDateTime(item.createdAt)?.let { dayLabel(it.toLocalDate()) }.orEmpty()
            val parts = if (kind == MediaKind.Files) listOf(formatFileSize(item.attachment.size), who, day) else listOf(who, day)
            MediaItemUi(item.attachment, item.messageId, parts.filter { it.isNotEmpty() }.joinToString(" · "))
        },
        hasMore = p.hasMore,
        loaded = p.loaded,
        loading = p.loading,
        failed = p.failed,
    )
}

/** The Photos and files sheet: everything sent in the chat, a page at a time, newest first. */
internal class ChatMedia(private val session: ChatSession, private val threads: ChatThreads, private val attachments: ChatAttachments) {
    private val data get() = session.data

    fun open() {
        data.update { it.copy(media = MediaSession(), menuOpen = false) }
        load(MediaKind.Media)
    }

    fun close() = data.update { it.copy(media = null) }

    fun showKind(kind: MediaKind) {
        data.update { d -> d.copy(media = d.media?.copy(kind = kind)) }
        if (data.value.media?.page(kind)?.loaded == false) load(kind)
    }

    /** The next page, as the end of the list comes into view. */
    fun loadMore() {
        val kind = data.value.media?.kind ?: return
        load(kind, more = true)
    }

    fun load(kind: MediaKind, more: Boolean = false) {
        val current = data.value.media?.page(kind) ?: return
        if (current.loading || (more && !current.hasMore)) return
        update(kind) { it.copy(loading = true) }
        session.scope.launch {
            val before = if (more) current.items.lastOrNull()?.attachment?.id else null
            when (val result = session.chat.media(session.groupId, kind.wire, before)) {
                is ApiResult.Success -> {
                    result.value.hasPasses?.let { has -> data.update { d -> d.copy(media = d.media?.copy(hasPasses = has)) } }
                    update(kind) {
                        it.copy(
                            items = if (more) it.items + result.value.items else result.value.items,
                            hasMore = result.value.hasMore,
                            loaded = true,
                            loading = false,
                            failed = false,
                        )
                    }
                }
                is ApiResult.Failure -> update(kind) { it.copy(loading = false, failed = true) }
            }
        }
    }

    /** A photo tapped: the viewer, over every photo loaded so far, with Show in chat. */
    fun viewPhoto(index: Int) {
        val items = data.value.media?.page(MediaKind.Media)?.items ?: return
        attachments.viewPictures(ViewerUi(items.map { it.attachment }, index, items.map { it.messageId }))
    }

    fun openFile(attachment: Attachment) = attachments.openFile(attachment)

    /** Closes the sheet (and viewer) and goes to [messageId] in the chat. */
    fun showInChat(messageId: String) {
        data.update { it.copy(media = null, viewer = null) }
        threads.showInChat(messageId)
    }

    private fun update(kind: MediaKind, change: (MediaPage) -> MediaPage) = data.update { d ->
        val m = d.media ?: return@update d
        d.copy(media = m.copy(pages = m.pages + (kind to change(m.page(kind)))))
    }
}
