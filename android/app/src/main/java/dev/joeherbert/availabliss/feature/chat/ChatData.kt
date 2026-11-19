package dev.joeherbert.availabliss.feature.chat

import android.net.Uri
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.PickedFile
import dev.joeherbert.availabliss.core.network.model.Attachment
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageInfo
import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.designsystem.component.chat.AttachmentUiKind
import dev.joeherbert.availabliss.designsystem.component.chat.ComposerAttachment

/*
 * The chat screen's internal state: everything ChatViewModel and its collaborators keep in one
 * MutableStateFlow<ChatData>, mapped to ChatUiState in ChatUiMapping.kt.
 */

/** A photo or file picked in the composer: uploading, uploaded (ready to send) or failed. */
internal data class PendingUpload(
    val localId: String,
    val uri: Uri,
    val file: PickedFile? = null,
    val kind: AttachmentUiKind = AttachmentUiKind.File,
    val name: String = "",
    val progress: Float = 0f,
    val uploaded: Attachment? = null,
    val error: String? = null,
    /** A video's poster frame (a cached JPEG), once it's been prepared. */
    val poster: java.io.File? = null,
) {
    fun toUi() = ComposerAttachment(
        id = localId,
        kind = kind,
        name = name,
        preview = when (kind) {
            AttachmentUiKind.File -> null
            AttachmentUiKind.Video -> poster
            else -> uri
        },
        progress = progress,
        uploaded = uploaded != null,
        error = error,
    )
}

/** Editing [messageId]; [original] is its text, [stashedDraft] the composer draft to restore afterwards. */
internal data class EditSession(val messageId: String, val original: String, val stashedDraft: String, val hasAttachments: Boolean = false)

internal data class ThreadSession(
    val focusId: String,
    val messages: List<Message> = emptyList(),
    val truncated: Boolean = false,
    val loading: Boolean = true,
    val pickedId: String? = null,
    val sending: Boolean = false,
    val actionsForId: String? = null,
)

internal data class InfoSheet(val messageId: String, val info: MessageInfo? = null, val error: String? = null)

internal data class ChatData(
    val group: Group? = null,
    val messages: List<Message> = emptyList(),
    val proposals: Map<String, Proposal> = emptyMap(),
    val events: Map<String, GroupEvent> = emptyMap(),
    /** Who's busy for each date poll option's time, by [pollSlotKey]. */
    val conflicts: Map<String, List<String>> = emptyMap(),
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val error: String? = null,
    val menuOpen: Boolean = false,
    val sending: Boolean = false,
    val pollDraft: PollDraft? = null,
    val infoSheet: InfoSheet? = null,
    val editing: EditSession? = null,
    val replyingToId: String? = null,
    val confirmDeleteId: String? = null,
    val thread: ThreadSession? = null,
    val showInChat: ShowInChatRequest? = null,
    val message: UserMessage? = null,
    val pending: List<PendingUpload> = emptyList(),
    val downloads: Map<String, Float> = emptyMap(),
    val viewer: ViewerUi? = null,
    val locationSheet: LocationSheetUi? = null,
    val search: SearchSession? = null,
    val media: MediaSession? = null,
) {
    /** The chat's loaded messages and the open thread's, to find one tapped in either. */
    val allMessages: List<Message> get() = messages + thread?.messages.orEmpty()
}

/** What the message list is built from; everything else in [ChatData] changes without rebuilding it. */
internal data class ItemInputs(
    val group: Group?,
    val messages: List<Message>,
    val proposals: Map<String, Proposal>,
    val events: Map<String, GroupEvent>,
    val conflicts: Map<String, List<String>>,
)
