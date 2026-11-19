package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.firstName
import dev.joeherbert.availabliss.core.common.joinNamesSummary
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.core.network.model.asReply
import dev.joeherbert.availabliss.core.network.model.attachmentsSummary
import dev.joeherbert.availabliss.core.network.model.canReply
import dev.joeherbert.availabliss.core.network.model.displayText
import dev.joeherbert.availabliss.feature.common.AddToCalendarState
import dev.joeherbert.availabliss.feature.common.MemberDirectory

/** The message list (newest first) from what it's built of. */
internal fun ItemInputs.toItems(me: String?): List<ChatItem> = buildChatItems(
    messages = messages,
    directory = MemberDirectory(group?.members.orEmpty(), me),
    memberCount = group?.memberCount ?: 0,
    proposals = proposals,
    events = events,
    conflicts = conflicts,
    isAdmin = group?.role?.isAdmin == true,
).asReversed()

internal fun ChatData.toUi(items: List<ChatItem>, me: String?, cal: AddToCalendarState): ChatUiState {
    val g = group
    val others = g?.members.orEmpty().filter { it.userId != me }.map { it.name.firstName() }
    return ChatUiState(
        groupName = g?.name.orEmpty(),
        monogram = g?.monogram.orEmpty(),
        tileColor = g?.color ?: TileColor.T1,
        groupImage = g?.image,
        membersSummary = others.joinNamesSummary(),
        items = items,
        loading = loading,
        loadingOlder = loadingOlder,
        hasMore = hasMore,
        error = error,
        menuOpen = menuOpen,
        sending = sending,
        pollDraft = pollDraft,
        editing = editing != null,
        replying = replyingToId?.let { id -> messages.firstOrNull { it.id == id } }?.let { m ->
            val directory = MemberDirectory(g?.members.orEmpty(), me)
            ReplyingUi(m.id, replyAuthorName(m.authorId, null, directory), m.asReply().snippet)
        },
        confirmDeleteId = confirmDeleteId,
        calendar = cal,
        thread = thread?.toUi(MemberDirectory(g?.members.orEmpty(), me), g?.memberCount ?: 0, me),
        showInChat = showInChat,
        info = infoSheet?.let { sheet ->
            sheet.toUi(messages.firstOrNull { it.id == sheet.messageId }, MemberDirectory(g?.members.orEmpty(), me))
        },
        message = message,
        attachments = pending.map { it.toUi() },
        editingCaption = editing?.hasAttachments == true,
        downloads = downloads,
        viewer = viewer,
        locationSheet = locationSheet,
        search = search?.toUi(MemberDirectory(g?.members.orEmpty(), me)),
        media = media?.toUi(MemberDirectory(g?.members.orEmpty(), me)),
    )
}

private fun ThreadSession.toUi(directory: MemberDirectory, memberCount: Int, me: String?): ThreadUi {
    val target = threadReplyTarget(messages, pickedId, me)
    val items = buildChatItems(messages, directory, memberCount, proposals = emptyMap(), events = emptyMap())
        // A thread is for replying: proposing a date poll's winner happens in the chat.
        .map { if (it is ChatItem.PollItem) it.copy(poll = it.poll.copy(canPropose = false)) else it }
        // No receipt rows, and no Edit / Delete / View thread from inside a thread.
        .filterNot { it is ChatItem.Receipt }
        .map { if (it is ChatItem.Text) it.copy(canEdit = false, inThread = false) else it }
    return ThreadUi(
        focusId = focusId,
        items = items,
        count = messages.size,
        loading = loading,
        truncated = truncated,
        replying = target?.let { ReplyingUi(it.id, replyAuthorName(it.authorId, null, directory), it.asReply().snippet) },
        picked = pickedId != null && target?.id == pickedId,
        sending = sending,
        actions = actionsForId?.let { id -> messages.firstOrNull { it.id == id } }?.let { m ->
            val name = replyAuthorName(m.authorId, null, directory)
            ThreadActionsUi(
                messageId = m.id,
                title = if (name == "You") "Your message" else "$name’s message",
                snippet = m.asReply().snippet,
                canReply = m.canReply,
            )
        },
    )
}

private fun InfoSheet.toUi(message: Message?, directory: MemberDirectory): MessageInfoUi {
    fun person(userId: String, time: String) = InfoPerson(directory.avatar(userId), directory.displayName(userId), time)
    fun clock(iso: String) = DateTimes.localDateTime(iso)?.let(DateTimes::time).orEmpty()
    val text = message?.takeIf { it.attachments.isNotEmpty() }?.let { it.displayText ?: attachmentsSummary(it.attachments) }
        ?: message?.body ?: message?.poll?.question.orEmpty()
    val own = message == null || directory.isMe(message.authorId)
    val sent = sentAtLabel(info?.sentAt ?: message?.createdAt.orEmpty())
    return MessageInfoUi(
        text = text,
        // "Sent today at 18:20", or "Ana sent this today at 18:20".
        sentLabel = if (own) "Sent $sent" else "${directory[message?.authorId.orEmpty()]?.name?.substringBefore(' ') ?: "Someone"} sent this $sent",
        own = own,
        loading = info == null && error == null,
        seen = info?.seen.orEmpty().map { person(it.userId, clock(it.at)) },
        delivered = info?.delivered.orEmpty().map { person(it.userId, "Delivered ${clock(it.at)}") },
        pending = info?.pending.orEmpty().map { person(it, "Not delivered yet") },
        error = error,
    )
}
