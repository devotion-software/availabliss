package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A search result's line, with the match picked out. */
data class SearchSnippet(val before: String, val match: String, val after: String)

/** Words kept either side of a match before the line is cut with "…". */
private const val SEARCH_CONTEXT = 32

/**
 * The first of the text (or caption), poll question and options, place and file names that contains
 * [query] (any case), cut down around the match. With no match there, the text from the start.
 * Same as web's `searchSnippet` and iOS's `Message.searchSnippet(for:)`.
 */
fun Message.searchSnippet(query: String): SearchSnippet {
    fun tidy(s: String?) = s?.trim()?.split(Regex("\\s+"))?.joinToString(" ")?.takeIf { it.isNotEmpty() }
    val candidates = buildList {
        val p = poll
        if (kind == MessageKind.Poll && p != null) {
            add(p.question)
            p.options.forEach { add("${p.question} · ${it.label}") }
        }
        add(if (attachments.isNotEmpty()) caption else if (location != null) null else body)
        add(location?.name)
        add(location?.address)
        attachments.forEach { add(it.name) }
    }.mapNotNull(::tidy)
    val needle = query.trim().split(Regex("\\s+")).joinToString(" ")
    if (needle.isNotEmpty()) {
        for (text in candidates) {
            val at = text.indexOf(needle, ignoreCase = true)
            if (at < 0) continue
            val start = (at - SEARCH_CONTEXT).coerceAtLeast(0)
            var before = text.substring(start, at)
            if (start > 0) before = "…" + before.substringAfter(' ', before)
            return SearchSnippet(before, text.substring(at, at + needle.length), text.substring(at + needle.length))
        }
    }
    return SearchSnippet(candidates.firstOrNull() ?: body.orEmpty(), "", "")
}

/** The search sheet's state: what's typed and what it found (null before a search has run). */
internal data class SearchSession(
    val query: String = "",
    val results: List<Message>? = null,
    val hasMore: Boolean = false,
    val searching: Boolean = false,
    val failed: Boolean = false,
)

data class SearchResultUi(val messageId: String, val author: String, val day: String, val snippet: SearchSnippet, val isPoll: Boolean)

data class ChatSearchUi(
    val query: String,
    /** Null until something's been searched for (2 characters or more). */
    val results: List<SearchResultUi>?,
    val hasMore: Boolean,
    val searching: Boolean,
    val failed: Boolean,
)

internal fun SearchSession.toUi(directory: MemberDirectory) = ChatSearchUi(
    query = query,
    results = results?.map { m ->
        SearchResultUi(
            messageId = m.id,
            author = replyAuthorName(m.authorId, null, directory),
            day = DateTimes.localDateTime(m.createdAt)?.let { dayLabel(it.toLocalDate()) }.orEmpty(),
            snippet = m.searchSnippet(query),
            isPoll = m.kind == MessageKind.Poll,
        )
    },
    hasMore = hasMore,
    searching = searching,
    failed = failed,
)

/** Search in the chat: matches as you type (from 2 characters), and going to one in the chat. */
internal class ChatSearch(private val session: ChatSession, private val threads: ChatThreads) {
    private val data get() = session.data
    private var job: Job? = null

    fun open() = data.update { it.copy(search = SearchSession(), menuOpen = false) }

    fun close() {
        job?.cancel()
        data.update { it.copy(search = null) }
    }

    /** A moment after you stop typing, searches for [value]; a new keystroke cancels the search before. */
    fun onQuery(value: String) {
        val query = value.take(MAX_QUERY)
        job?.cancel()
        val text = query.trim()
        if (text.length < MIN_QUERY) {
            update { it.copy(query = query, results = null, searching = false, failed = false) }
            return
        }
        update { it.copy(query = query, searching = true) }
        job = session.scope.launch {
            delay(DEBOUNCE_MS)
            when (val result = session.chat.search(session.groupId, text)) {
                is ApiResult.Success -> update { it.copy(results = result.value.messages, hasMore = result.value.hasMore, searching = false, failed = false) }
                is ApiResult.Failure -> update { it.copy(searching = false, failed = true) }
            }
        }
    }

    /** Closes the sheet and goes to [messageId] in the chat, paging back to it if needed. */
    fun pick(messageId: String) {
        close()
        threads.showInChat(messageId)
    }

    private fun update(change: (SearchSession) -> SearchSession) = data.update { d -> d.copy(search = d.search?.let(change)) }

    private companion object {
        const val MIN_QUERY = 2
        const val MAX_QUERY = 100
        const val DEBOUNCE_MS = 300L
    }
}
