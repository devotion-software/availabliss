package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageInfo
import dev.joeherbert.availabliss.core.network.model.MessagesResponse
import dev.joeherbert.availabliss.core.network.model.NewPoll
import dev.joeherbert.availabliss.core.network.model.Poll
import dev.joeherbert.availabliss.core.network.model.ReceiptState
import dev.joeherbert.availabliss.core.network.model.ReceiptsRequest
import dev.joeherbert.availabliss.core.network.model.SendPollRequest
import dev.joeherbert.availabliss.core.network.model.SendTextRequest
import dev.joeherbert.availabliss.core.network.model.VoteRequest
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

    suspend fun sendText(groupId: String, body: String): ApiResult<Message> =
        call { api.sendText(groupId, SendTextRequest(body.trim())) }

    suspend fun sendPoll(groupId: String, question: String, options: List<String>, multi: Boolean): ApiResult<Message> =
        call { api.sendPoll(groupId, SendPollRequest(NewPoll(question.trim(), options.map { it.trim() }, multi))) }

    suspend fun markReceipts(groupId: String, upTo: String, state: ReceiptState): ApiResult<Unit> =
        call { api.sendReceipts(groupId, ReceiptsRequest(upTo, state)) }

    suspend fun info(messageId: String): ApiResult<MessageInfo> = call { api.getMessageInfo(messageId) }

    /** Empty [optionIds] removes the vote. */
    suspend fun vote(pollId: String, optionIds: List<String>): ApiResult<Poll> =
        call { api.vote(pollId, VoteRequest(optionIds)) }

    suspend fun closePoll(pollId: String): ApiResult<Poll> = call { api.closePoll(pollId) }

    companion object {
        const val PAGE_SIZE = 50
    }
}
