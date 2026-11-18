package dev.joeherbert.availabliss.core.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.joeherbert.availabliss.core.auth.TokenStore
import dev.joeherbert.availabliss.core.common.ApplicationScope
import dev.joeherbert.availabliss.core.network.model.GroupSummary
import dev.joeherbert.availabliss.core.network.model.MyPlansResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the home-screen widgets in step with the app: writes the [WidgetSnapshot] when
 * plans or groups load (the repositories call [onPlans] / [onGroups]), on sign out, and
 * from [WidgetRefreshWorker] after a push; then redraws the widgets.
 *
 * Writes are applied one at a time, in the order they were made, so a sign-out can't be
 * overwritten by a load that was queued before it.
 */
@Singleton
class Widgets @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val store = WidgetSnapshotStore(context)
    private val writes = Channel<(WidgetSnapshot) -> WidgetSnapshot>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (transform in writes) {
                try {
                    if (store.update(transform)) redraw()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't update the widgets", e)
                }
            }
        }
    }

    /** `GET /me/plans` loaded: the "Next plan" widget's plans. */
    fun onPlans(plans: MyPlansResponse) {
        if (tokenStore.currentToken() == null) return
        val upcoming = WidgetSnapshots.plans(plans, Instant.now())
        writes.trySend { it.copy(signedIn = true, plans = upcoming) }
    }

    /** The groups list loaded or changed: the proposals waiting for your reply. */
    fun onGroups(groups: List<GroupSummary>) {
        if (tokenStore.currentToken() == null) return
        val waiting = WidgetSnapshots.waiting(groups)
        writes.trySend { it.copy(signedIn = true, waiting = waiting.take(WidgetSnapshots.MAX_WAITING), waitingCount = waiting.size) }
    }

    /** Signed out (or the session was rejected): the widgets ask you to sign in. */
    fun onSignedOut() {
        writes.trySend { WidgetSnapshot.SignedOut }
    }

    /**
     * Refetches plans and groups in the background (after a push, or when a widget is first
     * added), coalescing bursts into one run.
     */
    fun refreshSoon() = WidgetRefreshWorker.enqueue(context)

    private suspend fun redraw() {
        NextPlanWidget().updateAll(context)
        WaitingWidget().updateAll(context)
    }

    companion object {
        private const val TAG = "Widgets"

        /**
         * Whether a push of [kind] can change what the widgets show: proposals (new, confirmed,
         * changed), reminders and events. Chat messages and the rest don't.
         */
        fun refreshesOn(kind: String?): Boolean = kind != null &&
            (kind.startsWith("proposal.") || kind.startsWith("event.") || kind == "reminder")
    }
}

/** Schedules [WidgetRefreshWorker]. */
internal fun WidgetRefreshWorker.Companion.enqueue(context: Context, delaySeconds: Long = 2) {
    val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
}
