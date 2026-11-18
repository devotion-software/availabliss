package dev.joeherbert.availabliss.core.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.joeherbert.availabliss.core.auth.TokenStore
import dev.joeherbert.availabliss.core.data.EventsRepository
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Refetches what the widgets show (`GET /me/plans` and the groups list) in the background.
 * The repositories write the snapshot themselves (through [Widgets]), so this only loads.
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tokenStore: TokenStore,
    private val groups: GroupsRepository,
    private val events: EventsRepository,
    private val widgets: Widgets,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (tokenStore.token() == null) {
            widgets.onSignedOut()
            return Result.success()
        }
        val (groupsResult, plansResult) = coroutineScope {
            val g = async { groups.refreshGroups() }
            val p = async { events.myPlans() }
            g.await() to p.await()
        }
        val failures = listOf(groupsResult, plansResult).filterIsInstance<ApiResult.Failure>()
        return when {
            failures.isEmpty() -> Result.success()
            failures.any { it.error is ApiError.Network || it.error is ApiError.Server || it.error is ApiError.RateLimited } &&
                runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        internal const val WORK_NAME = "widget-refresh"
        private const val MAX_ATTEMPTS = 3
    }
}
