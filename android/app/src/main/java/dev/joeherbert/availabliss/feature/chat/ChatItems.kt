package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.Attachment
import dev.joeherbert.availabliss.core.network.model.AttachmentKind
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.core.network.model.MessageLocation
import dev.joeherbert.availabliss.core.network.model.MessageReply
import dev.joeherbert.availabliss.core.network.model.Poll
import dev.joeherbert.availabliss.core.network.model.PollSlot
import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.core.network.model.WalletPass
import dev.joeherbert.availabliss.core.network.model.asReply
import dev.joeherbert.availabliss.core.network.model.attachmentsSummary
import dev.joeherbert.availabliss.core.network.model.canReply
import dev.joeherbert.availabliss.core.network.model.displayText
import dev.joeherbert.availabliss.core.network.model.isDatePoll
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.chat.AttachmentUi
import dev.joeherbert.availabliss.designsystem.component.chat.AttachmentUiKind
import dev.joeherbert.availabliss.designsystem.component.chat.BubbleQuote
import dev.joeherbert.availabliss.designsystem.component.chat.PassUi
import dev.joeherbert.availabliss.designsystem.component.chat.PollOptionUi
import dev.joeherbert.availabliss.designsystem.component.chat.PollUi
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardTone
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardUi
import dev.joeherbert.availabliss.designsystem.component.chat.fileIconFor
import dev.joeherbert.availabliss.designsystem.component.chat.formatFileSize
import dev.joeherbert.availabliss.designsystem.component.chat.passColor
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import dev.joeherbert.availabliss.feature.proposal.ProposalFormatting
import java.time.Duration
import java.time.Instant
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
        /** Can be replied to (swipe right, or Reply in the long-press menu). */
        val canReply: Boolean = false,
        /** The message this one replies to, quoted above the text. */
        val quote: BubbleQuote? = null,
        /** A reply, or replied to: long-press offers View thread. */
        val inThread: Boolean = false,
        /** Photos, GIFs and files; [text] is then the caption (possibly empty). */
        val attachments: List<AttachmentUi> = emptyList(),
        /** A shared place, drawn as a map card ([text] is then empty). */
        val location: MessageLocation? = null,
    ) : ChatItem {
        override val key get() = id
    }

    data class System(val id: String, val text: String) : ChatItem {
        override val key get() = id
    }

    /**
     * "Everyone's in": the system message posted when a proposal is confirmed, once its
     * event has loaded. [recent] (sent within the last day) lets it play the confetti burst.
     * [headline] is "It's on" when it was confirmed before everyone accepted, and then [count]
     * says who can make it, with maybes apart: "3 of 5 in · 1 maybe".
     */
    data class Confirmed(
        val id: String,
        val eventId: String,
        val proposalId: String?,
        val title: String,
        /** "Thu 26 Nov · from 18:00 · Priya's flat" */
        val detail: String,
        val recent: Boolean,
        val headline: String = "Everyone's in",
        val count: String? = null,
    ) : ChatItem {
        override val key get() = id
    }

    /** [card] is null while the proposal is loading. */
    data class ProposalCard(val id: String, val proposalId: String, val card: ProposalCardUi?) : ChatItem {
        override val key get() = id
    }

    data class EventCard(val id: String, val eventId: String, val proposalId: String?, val card: ProposalCardUi) : ChatItem {
        override val key get() = id
    }

    data class PollItem(val id: String, val poll: PollUi, val own: Boolean, val author: AvatarPerson?, val canReply: Boolean = true) : ChatItem {
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
    now: Instant = Instant.now(),
    /** Who's busy for each date poll option's time, by [pollSlotKey]; missing while it loads. */
    conflicts: Map<String, List<String>> = emptyMap(),
    /** You're an owner or admin: you can propose any date poll's winner, not just your own. */
    isAdmin: Boolean = false,
): List<ChatItem> {
    val items = mutableListOf<ChatItem>()
    // Deleted messages get no receipt (they can't be opened for info).
    val latestOwn = messages.lastOrNull { directory.isMe(it.authorId) && it.kind != MessageKind.System && !it.deleted }
    val repliedTo = messages.mapNotNullTo(HashSet()) { it.replyTo?.id }
    var lastDate: LocalDate? = null
    // Each message's date parsed once (it's needed for the message and its neighbours).
    val dates = messages.map { DateTimes.localDate(it.createdAt) }

    messages.forEachIndexed { index, m ->
        val date = dates[index]
        if (date != null && date != lastDate) {
            items += ChatItem.Day(dayLabel(date, today), date.toString())
            lastDate = date
        }
        val own = directory.isMe(m.authorId)
        val receipt = if (m.id == latestOwn?.id) receiptFor(m, directory) else null
        val authorName = directory.displayName(m.authorId, youSuffix = false)
        when {
            // A confirmation; until its event loads (or if it was deleted) it stays a plain line.
            m.kind == MessageKind.System && m.eventId != null && events[m.eventId] != null ->
                items += confirmedItem(m, events.getValue(m.eventId), proposals, now)

            m.kind == MessageKind.System || (m.authorId == null && m.kind == MessageKind.Text) ->
                items += ChatItem.System(m.id, m.body.orEmpty())

            m.kind == MessageKind.Poll && m.poll != null ->
                items += ChatItem.PollItem(
                    m.id,
                    m.poll.toUi(directory, canClose = own, title = if (own) "Your poll" else "Poll by $authorName", canPropose = own || isAdmin, conflicts = conflicts),
                    own,
                    m.authorId?.let(directory::avatar),
                )

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
                    !m.deleted -> m.displayText.orEmpty()
                    own -> "You deleted this message"
                    else -> "This message was deleted"
                }
                val attachments = if (m.deleted || m.attachments.isEmpty()) emptyList() else m.attachments.map { it.toUi() }
                val location = if (m.deleted) null else m.location
                if (text.isNotBlank() || attachments.isNotEmpty() || location != null) {
                    val place = location?.let { listOfNotNull("Location: ${it.name ?: "shared location"}", it.address).joinToString(", ") }.orEmpty()
                    val spoken = listOf(attachmentsSpoken(m.attachments), place, text).filter { it.isNotBlank() }.joinToString(", ")
                    val next = messages.getOrNull(index + 1)
                    val continues = next != null && next.authorId == m.authorId && next.kind == MessageKind.Text &&
                        dates[index + 1] == date
                    val prev = messages.getOrNull(index - 1)
                    val startsRun = prev == null || prev.authorId != m.authorId || prev.kind != MessageKind.Text ||
                        dates[index - 1] != date
                    items += ChatItem.Text(
                        id = m.id,
                        text = text,
                        own = own,
                        author = m.authorId?.let(directory::avatar),
                        showAvatar = !continues,
                        accessibilityLabel = when {
                            m.deleted -> "Message deleted"
                            own -> "Your message: $spoken${if (m.isEdited) ", edited" else ""}. Tap for message info"
                            else -> "$authorName: $spoken${if (m.isEdited) ", edited" else ""}"
                        },
                        authorName = if (!own && startsRun) m.authorId?.let { directory.avatar(it).name } else null,
                        // Your latest message says "Edited" on its receipt line instead.
                        edited = m.isEdited && receipt == null,
                        deleted = m.deleted,
                        canEdit = own && m.kind == MessageKind.Text && !m.deleted && location == null,
                        canReply = m.canReply,
                        quote = if (m.deleted) null else m.replyTo?.let { quoteFor(it, directory) },
                        inThread = m.replyTo != null || m.id in repliedTo,
                        attachments = attachments,
                        location = location,
                    )
                }
            }
        }

        receipt?.let { items += it }
    }
    return items
}

/** A sent attachment as the bubble draws it. */
fun Attachment.toUi(): AttachmentUi = AttachmentUi(
    id = id,
    kind = when (kind) {
        AttachmentKind.Image -> AttachmentUiKind.Image
        AttachmentKind.Gif -> AttachmentUiKind.Gif
        AttachmentKind.Video -> AttachmentUiKind.Video
        AttachmentKind.File, AttachmentKind.Pass -> AttachmentUiKind.File
    },
    name = name,
    sizeLabel = formatFileSize(size),
    preview = when (kind) {
        AttachmentKind.Image -> thumbnailUrl ?: url
        AttachmentKind.Gif -> url
        AttachmentKind.Video -> thumbnailUrl
        AttachmentKind.File, AttachmentKind.Pass -> null
    },
    width = width,
    height = height,
    icon = fileIconFor(name, mime),
    pass = pass?.takeIf { kind == AttachmentKind.Pass }?.toUi(thumbnailUrl),
    duration = duration,
)

/** A pass's card: the event (or what it is), when, where and up to three other details. */
fun WalletPass.toUi(logo: String?): PassUi {
    val headline = if (count > 1) title else eventName ?: title
    val dated = relevantDate?.let { DateTimes.dayAndTime(it).ifBlank { null } }
    val fields = fields
        .filter { it.value != headline && it.value != organizationName && !(it.date && dated != null) }
        .take(3)
        .map { f -> f.label to (if (f.date) DateTimes.dayAndTime(f.value).ifBlank { f.value } else f.value) }
    return PassUi(
        organization = logoText ?: organizationName,
        headline = headline,
        dateLabel = dated,
        venue = venue,
        fields = fields,
        background = passColor(backgroundColor),
        foreground = passColor(foregroundColor),
        label = passColor(labelColor),
        logo = logo,
        boarding = style == "boardingPass",
        voided = voided,
        count = count,
    )
}

/** The attachments for a screen reader: "Photo", "3 photos", "File: Plan.pdf, 1.2 MB". */
fun attachmentsSpoken(attachments: List<Attachment>): String =
    if (attachments.size == 1 && (attachments[0].kind == AttachmentKind.File || attachments[0].kind == AttachmentKind.Pass)) attachments[0].toUi().accessibilityLabel
    else attachmentsSummary(attachments)

private fun confirmedItem(m: Message, e: GroupEvent, proposals: Map<String, Proposal>, now: Instant): ChatItem.Confirmed {
    val sent = DateTimes.parseInstant(m.createdAt)
    val detail = listOfNotNull(
        DateTimes.rangeLabel(e.start, e.end, e.allDay).takeIf { it.isNotEmpty() },
        e.location?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    return ChatItem.Confirmed(
        id = m.id,
        eventId = e.id,
        proposalId = e.proposalId,
        title = e.title,
        detail = detail,
        recent = sent != null && !sent.isBefore(now.minus(BURST_WINDOW)) && !sent.isAfter(now.plus(BURST_WINDOW)),
        headline = if (m.body?.startsWith("It's on") == true) "It's on" else "Everyone's in",
        count = confirmedCount(m, e.proposalId?.let(proposals::get)),
    )
}

/** "(3 of 5 can make it, 1 maybe)" at the end of an "It's on for …" confirmation. */
private val CONFIRMED_COUNT = Regex("""\((\d+) of (\d+) can make it(?:, (\d+) maybe)?\)""")

/**
 * Who can make a plan confirmed before everyone accepted: "3 of 5 in · 1 maybe". From the
 * proposal when it's loaded (so later reply changes show), else from the message; null when
 * everyone's in.
 */
private fun confirmedCount(m: Message, proposal: Proposal?): String? {
    if (proposal != null) {
        if (proposal.everyoneIn) return null
        return ProposalFormatting.withMaybes(
            "${ProposalFormatting.acceptedCount(proposal)} of ${proposal.responses.size} in",
            ProposalFormatting.maybeCount(proposal),
        )
    }
    val match = CONFIRMED_COUNT.find(m.body.orEmpty()) ?: return null
    val (coming, total, maybe) = match.destructured
    return ProposalFormatting.withMaybes("$coming of $total in", maybe.toIntOrNull() ?: 0)
}

/** How recent a confirmation must be to burst. */
private val BURST_WINDOW: Duration = Duration.ofHours(24)

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

/** "You", the member's name, or the name the server sent (someone who has left). */
fun replyAuthorName(authorId: String?, fallback: String?, directory: MemberDirectory): String = when {
    directory.isMe(authorId) -> "You"
    else -> authorId?.let { directory[it]?.name } ?: fallback ?: "Someone"
}

private fun quoteFor(reply: MessageReply, directory: MemberDirectory) = BubbleQuote(
    messageId = reply.id,
    name = replyAuthorName(reply.authorId, reply.authorName, directory),
    snippet = reply.snippet,
    deleted = reply.deleted,
)

/** Quotes aren't republished when the original changes: refreshes loaded replies to [original]. */
private fun List<Message>.withQuotesOf(original: Message): List<Message> = map { m ->
    val quote = m.replyTo
    if (quote?.id != original.id) m
    else {
        val fresh = original.asReply()
        m.copy(replyTo = quote.copy(text = fresh.text, deleted = fresh.deleted))
    }
}

/**
 * Applies an edited or deleted message (from `message.updated` or your own PATCH) to
 * the loaded list, by id. The update's `receipt` is null on live events, so the
 * receipt already held is kept. Unknown ids are ignored.
 */
fun List<Message>.withUpdated(updated: Message): List<Message> = map { m ->
    if (m.id == updated.id) updated.copy(receipt = updated.receipt ?: m.receipt) else m
}.withQuotesOf(updated)

/** Marks a message deleted locally after your own DELETE succeeds. */
fun List<Message>.withDeleted(messageId: String): List<Message> {
    val deleted = firstOrNull { it.id == messageId }?.copy(body = null, deleted = true, attachments = emptyList(), caption = null, location = null) ?: return this
    return map { m -> if (m.id == messageId) deleted else m }.withQuotesOf(deleted)
}

private fun Poll.toUi(
    directory: MemberDirectory,
    canClose: Boolean,
    title: String,
    canPropose: Boolean = false,
    conflicts: Map<String, List<String>> = emptyMap(),
): PollUi {
    val counts = options.map { it.voterIds.size }
    val max = counts.maxOrNull() ?: 0
    val voters = options.flatMap { it.voterIds }
    val nameOf = { id: String -> if (directory.isMe(id)) "You" else directory.displayName(id, youSuffix = false) }
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
                detail = o.slot?.let { slot -> conflicts[pollSlotKey(slot)]?.let { busyLine(it, nameOf) } },
                busy = o.slot?.let { slot -> conflicts[pollSlotKey(slot)]?.isNotEmpty() } == true,
            ).let { ui -> o.slot?.let { ui.copy(label = DateTimes.rangeLabel(it.start, it.end, it.allDay)) } ?: ui }
        },
        isDatePoll = isDatePoll,
        canPropose = canPropose && isDatePoll,
    )
}

/** Identifies a date poll option's time, for the conflicts it's checked against. */
fun pollSlotKey(slot: PollSlot): String = "${slot.start}|${slot.end}"

/**
 * Who's busy for a date poll option: "Everyone's free", "You're busy", "Ana is busy",
 * "You and Ana are busy", "3 people are busy". [nameOf] gives "You" for the viewer.
 */
fun busyLine(ids: List<String>, nameOf: (String) -> String): String {
    val names = ids.map(nameOf).sortedBy { it != "You" }
    return when (names.size) {
        0 -> "Everyone's free"
        1 -> if (names[0] == "You") "You're busy" else "${names[0]} is busy"
        2 -> "${names[0]} and ${names[1]} are busy"
        else -> "${names.size} people are busy"
    }
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
