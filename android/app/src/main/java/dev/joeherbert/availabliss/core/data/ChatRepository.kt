package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.model.Attachment
import dev.joeherbert.availabliss.core.network.model.ChatListResponse
import dev.joeherbert.availabliss.core.network.model.GroupMediaPage
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageInfo
import dev.joeherbert.availabliss.core.network.model.MessageLocation
import dev.joeherbert.availabliss.core.network.model.MessageThread
import dev.joeherbert.availabliss.core.network.model.MessagesResponse
import dev.joeherbert.availabliss.core.network.model.NewDatePoll
import dev.joeherbert.availabliss.core.network.model.NewPoll
import dev.joeherbert.availabliss.core.network.model.Poll
import dev.joeherbert.availabliss.core.network.model.PollSlot
import dev.joeherbert.availabliss.core.network.model.ReceiptState
import dev.joeherbert.availabliss.core.network.model.ReceiptsRequest
import dev.joeherbert.availabliss.core.network.model.SendAttachmentsRequest
import dev.joeherbert.availabliss.core.network.model.SendDatePollRequest
import dev.joeherbert.availabliss.core.network.model.SendLocationRequest
import dev.joeherbert.availabliss.core.network.model.SendPollRequest
import dev.joeherbert.availabliss.core.network.model.SendTextRequest
import dev.joeherbert.availabliss.core.network.model.UpdatePollRequest
import dev.joeherbert.availabliss.core.network.model.VoteRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Group chat, polls and read receipts. */
@Singleton
class ChatRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    /** A page of messages, newest last. Pass the oldest loaded id as [before] to page back. */
    suspend fun page(groupId: String, before: String? = null, limit: Int = PAGE_SIZE): ApiResult<MessagesResponse> =
        call { api.getMessages(groupId, before, limit) }

    /** Every group chat you're in, latest message first. */
    suspend fun chats(): ApiResult<ChatListResponse> = call { api.getChats() }

    /** A chat's photos and GIFs ([kind] "media") or files and passes ("files"), newest first; [before] is the last attachment id. */
    suspend fun media(groupId: String, kind: String, before: String? = null): ApiResult<GroupMediaPage> =
        call { api.getGroupMedia(groupId, kind, before) }

    /** Messages containing [query] (2–100 characters, any case), newest first. */
    suspend fun search(groupId: String, query: String): ApiResult<MessagesResponse> =
        call { api.searchMessages(groupId, query) }

    suspend fun sendText(groupId: String, body: String, replyToId: String? = null): ApiResult<Message> =
        call { api.sendText(groupId, SendTextRequest(body.trim(), replyToId)) }

    /** Sends uploaded attachments with an optional caption. */
    suspend fun sendAttachments(groupId: String, attachmentIds: List<String>, caption: String, replyToId: String? = null): ApiResult<Message> =
        call { api.sendAttachments(groupId, SendAttachmentsRequest(attachmentIds, caption.trim().takeIf { it.isNotEmpty() }, replyToId)) }

    /** Uploads one file as [name] (not yet sent). */
    suspend fun upload(groupId: String, name: String, body: RequestBody): ApiResult<Attachment> =
        call { api.uploadAttachment(groupId, MultipartBody.Part.createFormData("file", name, body)) }

    /** Shares a place (your position, or one you found). */
    suspend fun sendLocation(groupId: String, location: MessageLocation, replyToId: String? = null): ApiResult<Message> =
        call { api.sendLocation(groupId, SendLocationRequest(location, replyToId)) }

    suspend fun sendPoll(groupId: String, question: String, options: List<String>, multi: Boolean): ApiResult<Message> =
        call { api.sendPoll(groupId, SendPollRequest(NewPoll(question.trim(), options.map { it.trim() }, multi))) }

    /** A date poll asking which of [slots] (2–5) works; anyone can pick more than one. */
    suspend fun sendDatePoll(groupId: String, question: String, slots: List<PollSlot>): ApiResult<Message> =
        call { api.sendDatePoll(groupId, SendDatePollRequest(NewDatePoll(question.trim(), slots, multi = true))) }

    suspend fun markReceipts(groupId: String, upTo: String, state: ReceiptState): ApiResult<Unit> =
        call { api.sendReceipts(groupId, ReceiptsRequest(upTo, state)) }

    /** Edits your own text message (with attachments, its caption; empty removes it). */
    suspend fun edit(messageId: String, body: String): ApiResult<Message> =
        call { api.editMessage(messageId, SendTextRequest(body.trim())) }

    /** Deletes your own text message (it stays in the chat, shown as deleted). */
    suspend fun delete(messageId: String): ApiResult<Unit> = call { api.deleteMessage(messageId) }

    suspend fun info(messageId: String): ApiResult<MessageInfo> = call { api.getMessageInfo(messageId) }

    /** The thread [messageId] is part of, which may reach back past the loaded pages. */
    suspend fun thread(messageId: String): ApiResult<MessageThread> = call { api.getMessageThread(messageId) }

    /** Empty [optionIds] removes the vote. */
    suspend fun vote(pollId: String, optionIds: List<String>): ApiResult<Poll> =
        call { api.vote(pollId, VoteRequest(optionIds)) }

    suspend fun closePoll(pollId: String): ApiResult<Poll> = call { api.closePoll(pollId) }

    suspend fun updatePoll(pollId: String, body: UpdatePollRequest): ApiResult<Poll> = call { api.updatePoll(pollId, body) }

    companion object {
        const val PAGE_SIZE = 50
    }
}
