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

    data class EventCard(val id: String, val proposalId: String?, val card: ProposalCardUi) : ChatItem {
        override val key get() = id
    }

    data class PollItem(val id: String, val poll: PollUi, val own: Boolean, val author: AvatarPerson?) : ChatItem {
        override val key get() = id
    }

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
    isAdmin: Boolean,
    today: LocalDate = DateTimes.today(),
): List<ChatItem> {
    val items = mutableListOf<ChatItem>()
    val latestOwn = messages.lastOrNull { directory.isMe(it.authorId) && it.kind != MessageKind.System }
    var lastDate: LocalDate? = null

    messages.forEachIndexed { index, m ->
        val date = DateTimes.localDate(m.createdAt)
        if (date != null && date != lastDate) {
            items += ChatItem.Day(dayLabel(date, today), date.toString())
            lastDate = date
        }
        val own = directory.isMe(m.authorId)
        val authorName = directory.displayName(m.authorId, youSuffix = false)
        when {
            m.kind == MessageKind.System || m.authorId == null && m.kind == MessageKind.Text ->
                items += ChatItem.System(m.id, m.body.orEmpty())

            m.kind == MessageKind.Poll && m.poll != null ->
                items += ChatItem.PollItem(m.id, m.poll.toUi(directory, own || isAdmin), own, m.authorId?.let(directory::avatar))

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
                        e.proposalId,
                        ProposalCardUi(
                            month = d?.let(DateTimes::monthAbbrev).orEmpty(),
                            day = d?.dayOfMonth?.toString().orEmpty(),
                            caption = "${if (own) "You" else authorName} added an event",
                            title = e.title,
                            status = DateTimes.rangeLabel(e.start, e.end, e.allDay),
                            tone = ProposalCardTone.Confirmed,
                        ),
                    )
                } else if (!m.body.isNullOrBlank()) {
                    items += ChatItem.System(m.id, m.body)
                }
            }

            else -> {
                val text = m.body.orEmpty()
                if (text.isNotBlank()) {
                    val next = messages.getOrNull(index + 1)
                    val continues = next != null && next.authorId == m.authorId && next.kind == MessageKind.Text &&
                        DateTimes.localDate(next.createdAt) == date
                    items += ChatItem.Text(
                        id = m.id,
                        text = text,
                        own = own,
                        author = m.authorId?.let(directory::avatar),
                        showAvatar = !continues,
                        accessibilityLabel = if (own) "Your message: $text. Tap for message info" else "$authorName: $text",
                    )
                }
            }
        }

        if (m.id == latestOwn?.id) {
            receiptFor(m, directory)?.let { items += it }
        }
    }
    return items
}

private fun receiptFor(m: Message, directory: MemberDirectory): ChatItem.Receipt? {
    val r = m.receipt ?: return ChatItem.Receipt(m.id, "Sent", seen = false, seenBy = emptyList())
    val seenBy = r.seenBy.map(directory::avatar)
    return when {
        r.recipients == 0 -> null
        r.seen > 0 -> ChatItem.Receipt(m.id, "Seen by ${r.seen} of ${r.recipients}", seen = true, seenBy = seenBy)
        r.delivered > 0 -> ChatItem.Receipt(m.id, "Delivered to ${r.delivered} of ${r.recipients}", seen = false, seenBy = emptyList())
        else -> ChatItem.Receipt(m.id, "Sent", seen = false, seenBy = emptyList())
    }
}

private fun Poll.toUi(directory: MemberDirectory, canClose: Boolean): PollUi {
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
