package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.data.FoundPlace
import dev.joeherbert.availabliss.core.network.model.Attachment
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.chat.ComposerAttachment
import dev.joeherbert.availabliss.feature.common.AddToCalendarState

/** A person row in the Message info sheet. */
data class InfoPerson(val person: AvatarPerson, val name: String, val time: String)

data class MessageInfoUi(
    val text: String,
    val sentLabel: String,
    /** Your own message (bubble on the right) or someone else's (on the left). */
    val own: Boolean = true,
    val loading: Boolean,
    val seen: List<InfoPerson> = emptyList(),
    val delivered: List<InfoPerson> = emptyList(),
    val pending: List<InfoPerson> = emptyList(),
    val error: String? = null,
)

/** "Replying to Priya" above the composer, with one line of what they said. */
data class ReplyingUi(val messageId: String, val name: String, val snippet: String)

/** The thread sheet: a message's chain of replies, with a composer that replies within it. */
data class ThreadUi(
    /** The message it was opened from, shown and flashed once loaded. */
    val focusId: String,
    /** Oldest first. */
    val items: List<ChatItem> = emptyList(),
    val count: Int = 0,
    val loading: Boolean = true,
    val truncated: Boolean = false,
    /** Who your reply goes to; [picked] when you chose it rather than it being the latest. */
    val replying: ReplyingUi? = null,
    val picked: Boolean = false,
    val sending: Boolean = false,
    /** Non-null while a tapped message's Reply / Show in chat options are showing. */
    val actions: ThreadActionsUi? = null,
)

data class ThreadActionsUi(val messageId: String, val title: String, val snippet: String, val canReply: Boolean)

/** Scroll the chat to [messageId] and flash it ("Show in chat" from a thread). */
data class ShowInChatRequest(val messageId: String, val nonce: Long = System.nanoTime())

/** The full-screen photo viewer: a message's photos and GIFs, opened at [startIndex]. */
data class ViewerUi(val attachments: List<Attachment>, val startIndex: Int)

/** Poll creation form. */
data class PollDraft(
    val question: String = "",
    val options: List<String> = listOf("", ""),
    val multi: Boolean = false,
    val sending: Boolean = false,
    /** Editing your poll rather than making one. */
    val editingPollId: String? = null,
    /** Each option's id when editing (null for one added now), alongside [options], so votes stay. */
    val optionIds: List<String?> = listOf(null, null),
    /** A date poll being edited: its times stay, so only the question and pick-one / pick-any change. */
    val dated: Boolean = false,
) {
    val canAddOption: Boolean get() = options.size < MAX_OPTIONS
    val canSubmit: Boolean get() = question.isNotBlank() && (dated || options.count { it.isNotBlank() } >= 2) && !sending

    companion object {
        const val MAX_OPTIONS = 10
    }
}

data class ChatUiState(
    val groupName: String = "",
    val monogram: String = "",
    val tileColor: TileColor = TileColor.T1,
    /** The group's photo, instead of the monogram. */
    val groupImage: String? = null,
    val membersSummary: String = "",
    /** Newest first (the list is reverse-laid-out). */
    val items: List<ChatItem> = emptyList(),
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val menuOpen: Boolean = false,
    val sending: Boolean = false,
    /** Non-null while the poll sheet is open. */
    val pollDraft: PollDraft? = null,
    /** Non-null while the Message info sheet is open. */
    val info: MessageInfoUi? = null,
    /** The composer is editing one of your messages (the draft holds its text). */
    val editing: Boolean = false,
    /** The message your next one replies to, shown above the composer. */
    val replying: ReplyingUi? = null,
    /** Non-null while "Delete this message?" is showing. */
    val confirmDeleteId: String? = null,
    /** Add to calendar on "Everyone's in" banners (`added` holds event ids added this session). */
    val calendar: AddToCalendarState = AddToCalendarState(),
    /** Non-null while the thread sheet is open. */
    val thread: ThreadUi? = null,
    val showInChat: ShowInChatRequest? = null,
    val message: UserMessage? = null,
    /** Photos and files picked for the next message. */
    val attachments: List<ComposerAttachment> = emptyList(),
    /** Editing a message with attachments: its caption can be emptied. */
    val editingCaption: Boolean = false,
    /** Attachments downloading (to open, share or save): progress 0–1 by id, below 0 when unknown. */
    val downloads: Map<String, Float> = emptyMap(),
    /** Non-null while the photo viewer is open. */
    val viewer: ViewerUi? = null,
    /** Non-null while the Share a location sheet is open. */
    val locationSheet: LocationSheetUi? = null,
)

/** The Share a location sheet: a place search and what it found, or the place picked from it. */
data class LocationSheetUi(
    val query: String = "",
    val results: List<FoundPlace> = emptyList(),
    val searching: Boolean = false,
    /** A search finished with nothing found. */
    val noResults: Boolean = false,
    val picked: FoundPlace? = null,
    val sending: Boolean = false,
)
