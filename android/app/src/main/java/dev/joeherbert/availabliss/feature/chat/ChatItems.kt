package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.core.network.model.Poll
import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.chat.PollOptionUi
import dev.joeherbert.availabliss.designsystem.component.chat.PollUi
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardTone
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardUi
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import dev.joeherbert.availabliss.feature.proposal.ProposalFormatting
import java.time.LocalDate

/** One row of the chat list. [key] is stable for LazyColumn. */
sealed interface ChatItem {
    val key: String

    data class Day(val label: String, val date: String) : ChatItem {
        override val key get() = "day-$date"
    }

    data class Text(
        val id: String,
        val text: String,
        val own: Boolean,
        val author: AvatarPerson?,
        val showAvatar: Boolean,
        val accessibilityLabel: String,
        /** Shown above the first incoming message in a run from the same person. */
        val authorName: String? = null,
        /** Shows "Edited" under the bubble (unless the receipt line below already says so). */
        val edited: Boolean = false,
        /** Deleted by its author: a dashed "This message was deleted" bubble, not tappable. */
        val deleted: Boolean = false,
        /** Your own, non-deleted text message: long-press offers Edit and Delete. */
        val canEdit: Boolean = false,
    ) : ChatItem {
        override val key get() = id
    }

    data class System(val id: String, val text: String) : ChatItem {
        override val key get() = id
    }

    /** [card] is null while the proposal is loading. */
    data class ProposalCard(val id: String, val proposalId: String, val card: ProposalCardUi?) : ChatItem {
        override val key get() = id
    }

    data class EventCard(val id: String, val eventId: String, val proposalId: String?, val card: ProposalCardUi) : ChatItem {
        override val key get() = id
    }

    data class PollItem(val id: String, val poll: PollUi, val own: Boolean, val author: AvatarPerson?) : ChatItem {
        override val key get() = id
    }

    /** [label] starts with "Edited · " when the message above it was edited. */
    data class Receipt(val messageId: String, val label: String, val seen: Boolean, val seenBy: List<AvatarPerson>) : ChatItem {
        override val key get() = "receipt-$messageId"
    }
}

/**
 * Builds the chat rows (oldest first) from raw messages: day separators, bubbles,
 * cards, polls, and the read receipt under your latest message.
 */
fun buildChatItems(
    messages: List<Message>,
    directory: MemberDirectory,
    memberCount: Int,
    proposals: Map<String, Proposal>,
    events: Map<String, GroupEvent>,
    today: LocalDate = DateTimes.today(),
): List<ChatItem> {
    val items = mutableListOf<ChatItem>()
    // Deleted messages get no receipt (they can't be opened for info).
    val latestOwn = messages.lastOrNull { directory.isMe(it.authorId) && it.kind != MessageKind.System && !it.deleted }
    var lastDate: LocalDate? = null

    messages.forEachIndexed { index, m ->
        val date = DateTimes.localDate(m.createdAt)
        if (date != null && date != lastDate) {
            items += ChatItem.Day(dayLabel(date, today), date.toString())
            lastDate = date
        }
        val own = directory.isMe(m.authorId)
        val receipt = if (m.id == latestOwn?.id) receiptFor(m, directory) else null
        val authorName = directory.displayName(m.authorId, youSuffix = false)
        when {
            m.kind == MessageKind.System || m.authorId == null && m.kind == MessageKind.Text ->
                items += ChatItem.System(m.id, m.body.orEmpty())

            m.kind == MessageKind.Poll && m.poll != null ->
                items += ChatItem.PollItem(m.id, m.poll.toUi(directory, canClose = own, title = if (own) "Your poll" else "Poll by $authorName"), own, m.authorId?.let(directory::avatar))

            m.kind == MessageKind.Proposal && m.proposalId != null -> {
                val p = proposals[m.proposalId]
                items += ChatItem.ProposalCard(
                    m.id,
                    m.proposalId,
                    p?.let { ProposalFormatting.card(it, directory, memberCount, caption = "${if (own) "You" else authorName} proposed a time") },
                )
            }

            m.kind == MessageKind.Event && m.eventId != null -> {
                val e = events[m.eventId]
                if (e != null) {
                    val d = DateTimes.localDate(e.start)
                    items += ChatItem.EventCard(
                        m.id,
                        e.id,
                        e.proposalId,
                        ProposalCardUi(
                            month = d?.let(DateTimes::monthAbbrev).orEmpty(),
                            day = d?.dayOfMonth?.toString().orEmpty(),
                            caption = "${if (own) "You" else authorName} added an event",
                            title = e.title,
                            status = DateTimes.rangeLabel(e.start, e.end, e.allDay),
                            tone = ProposalCardTone.Confirmed,
                            image = e.image,
                        ),
                    )
                } else if (!m.body.isNullOrBlank()) {
                    items += ChatItem.System(m.id, m.body)
                }
            }

            else -> {
                val text = when {
                    !m.deleted -> m.body.orEmpty()
                    own -> "You deleted this message"
                    else -> "This message was deleted"
                }
                if (text.isNotBlank()) {
                    val next = messages.getOrNull(index + 1)
                    val continues = next != null && next.authorId == m.authorId && next.kind == MessageKind.Text &&
                        DateTimes.localDate(next.createdAt) == date
                    val prev = messages.getOrNull(index - 1)
                    val startsRun = prev == null || prev.authorId != m.authorId || prev.kind != MessageKind.Text ||
                        DateTimes.localDate(prev.createdAt) != date
                    items += ChatItem.Text(
                        id = m.id,
                        text = text,
                        own = own,
                        author = m.authorId?.let(directory::avatar),
                        showAvatar = !continues,
                        accessibilityLabel = when {
                            m.deleted -> "Message deleted"
                            own -> "Your message: $text${if (m.isEdited) ", edited" else ""}. Tap for message info"
                            else -> "$authorName: $text${if (m.isEdited) ", edited" else ""}"
                        },
                        authorName = if (!own && startsRun) m.authorId?.let { directory.avatar(it).name } else null,
                        // Your latest message says "Edited" on its receipt line instead.
                        edited = m.isEdited && receipt == null,
                        deleted = m.deleted,
                        canEdit = own && m.kind == MessageKind.Text && !m.deleted,
                    )
                }
            }
        }

        receipt?.let { items += it }
    }
    return items
}

private fun receiptFor(m: Message, directory: MemberDirectory): ChatItem.Receipt? {
    val prefix = if (m.isEdited) "Edited · " else ""
    val r = m.receipt ?: return ChatItem.Receipt(m.id, "${prefix}Sent", seen = false, seenBy = emptyList())
    val seenBy = r.seenBy.map(directory::avatar)
    return when {
        r.recipients == 0 -> null
        r.seen > 0 -> ChatItem.Receipt(m.id, "${prefix}Seen by ${r.seen} of ${r.recipients}", seen = true, seenBy = seenBy)
        r.delivered > 0 -> ChatItem.Receipt(m.id, "${prefix}Delivered to ${r.delivered} of ${r.recipients}", seen = false, seenBy = emptyList())
        else -> ChatItem.Receipt(m.id, "${prefix}Sent", seen = false, seenBy = emptyList())
    }
}

private val Message.isEdited: Boolean get() = editedAt != null && !deleted

/**
 * Applies an edited or deleted message (from `message.updated` or your own PATCH) to
 * the loaded list, by id. The update's `receipt` is null on live events, so the
 * receipt already held is kept. Unknown ids are ignored.
 */
fun List<Message>.withUpdated(updated: Message): List<Message> = map { m ->
    if (m.id == updated.id) updated.copy(receipt = updated.receipt ?: m.receipt) else m
}

/** Marks a message deleted locally after your own DELETE succeeds. */
fun List<Message>.withDeleted(messageId: String): List<Message> = map { m ->
    if (m.id == messageId) m.copy(body = null, deleted = true) else m
}

private fun Poll.toUi(directory: MemberDirectory, canClose: Boolean, title: String): PollUi {
    val counts = options.map { it.voterIds.size }
    val max = counts.maxOrNull() ?: 0
    val voters = options.flatMap { it.voterIds }
    return PollUi(
        id = id,
        question = question,
        totalVotes = if (multi) voters.size else voters.distinct().size,
        multi = multi,
        closed = closed,
        canClose = canClose,
        title = title,
        options = options.map { o ->
            PollOptionUi(
                id = o.id,
                label = o.label,
                count = o.voterIds.size,
                mine = o.voterIds.any(directory::isMe),
                leading = max > 0 && o.voterIds.size == max,
            )
        },
    )
}

/** "Today", "Yesterday", "Wed 11 Nov". */
fun dayLabel(date: LocalDate, today: LocalDate = DateTimes.today()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> DateTimes.shortDay(date)
}

/** "today at 14:20", "yesterday at 09:05", "on Wed 11 Nov at 18:30". */
fun sentAtLabel(iso: String, today: LocalDate = DateTimes.today()): String {
    val dt = DateTimes.localDateTime(iso) ?: return ""
    val time = DateTimes.time(dt)
    return when (dt.toLocalDate()) {
        today -> "today at $time"
        today.minusDays(1) -> "yesterday at $time"
        else -> "on ${DateTimes.shortDay(dt.toLocalDate())} at $time"
    }
}
