package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.JsonPatchBuilder
import dev.joeherbert.availabliss.core.network.jsonPatch
import dev.joeherbert.availabliss.core.network.map
import dev.joeherbert.availabliss.core.network.model.BusyInterval
import dev.joeherbert.availabliss.core.network.model.CreateBlockRequest
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.common.DateTimes
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** "My time": own merged busy time and manual blocks (`/me/busy`, `/blocks`). */
@Singleton
class BlocksRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    /** Own busy time for the local days [from]..[to] inclusive, sent as ISO instants. */
    suspend fun busy(from: LocalDate, to: LocalDate): ApiResult<List<BusyInterval>> {
        val zone = DateTimes.zone
        val start = DateTimes.toIso(from.atStartOfDay(zone).toInstant())
        val end = DateTimes.toIso(to.plusDays(1).atStartOfDay(zone).toInstant())
        return call { api.getMyBusy(start, end) }.map { it.intervals }
    }

    suspend fun blocks(): ApiResult<List<ManualBlock>> = call { api.getBlocks() }.map { it.blocks }

    suspend fun create(request: CreateBlockRequest): ApiResult<ManualBlock> = call { api.createBlock(request) }

    suspend fun update(id: String, block: JsonPatchBuilder.() -> Unit): ApiResult<ManualBlock> =
        call { api.updateBlock(id, jsonPatch(block)) }

    suspend fun delete(id: String): ApiResult<Unit> = call { api.deleteBlock(id) }
}
