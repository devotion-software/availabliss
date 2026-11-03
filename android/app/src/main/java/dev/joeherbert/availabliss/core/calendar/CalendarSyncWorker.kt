package dev.joeherbert.availabliss.core.calendar

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.joeherbert.availabliss.core.network.ApiError
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Runs [DeviceCalendarSync] in the background. */
@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: DeviceCalendarSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val result = sync.sync()) {
        is DeviceSyncResult.Synced, DeviceSyncResult.Skipped, DeviceSyncResult.Cleared -> Result.success()
        is DeviceSyncResult.Failed -> when (result.error) {
            is ApiError.Network, is ApiError.Server, is ApiError.RateLimited -> Result.retry()
            else -> Result.failure()
        }
    }
}

/** Schedules device calendar uploads: periodic, and on demand (launch, calendar change). */
@Singleton
class CalendarSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Coalesces bursts of calendar changes into one upload. */
    fun syncSoon(delaySeconds: Long = 0) {
        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(ONE_OFF_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(ONE_OFF_WORK)
    }

    private companion object {
        const val PERIODIC_WORK = "device-calendar-sync-periodic"
        const val ONE_OFF_WORK = "device-calendar-sync-now"
        const val PERIODIC_HOURS = 6L
    }
}
