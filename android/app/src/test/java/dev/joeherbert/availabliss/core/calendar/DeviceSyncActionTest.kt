package dev.joeherbert.availabliss.core.calendar

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSyncActionTest {
    @Test
    fun `uploads when signed in with permission`() {
        assertEquals(DeviceSyncAction.Upload, deviceSyncAction(signedIn = true, hasPermission = true, lastSyncAt = null))
        assertEquals(DeviceSyncAction.Upload, deviceSyncAction(signedIn = true, hasPermission = true, lastSyncAt = 1L))
    }

    @Test
    fun `clears the last upload once permission is revoked`() {
        assertEquals(DeviceSyncAction.Clear, deviceSyncAction(signedIn = true, hasPermission = false, lastSyncAt = 1L))
    }

    @Test
    fun `skips without permission when nothing was uploaded`() {
        assertEquals(DeviceSyncAction.Skip, deviceSyncAction(signedIn = true, hasPermission = false, lastSyncAt = null))
    }

    @Test
    fun `skips when signed out`() {
        assertEquals(DeviceSyncAction.Skip, deviceSyncAction(signedIn = false, hasPermission = true, lastSyncAt = 1L))
        assertEquals(DeviceSyncAction.Skip, deviceSyncAction(signedIn = false, hasPermission = false, lastSyncAt = 1L))
    }

    @Test
    fun `skips a range identical to the last upload of it within its max age`() {
        val day = DeviceCalendarSync.MAX_UNCHANGED_AGE_MS
        val last = UploadedRange("h", at = 1_000L)
        assertEquals(true, isUnchanged("h", last, now = 1_000L + day - 1, maxAge = day))
        assertEquals(false, isUnchanged("h", UploadedRange("other", 1_000L), now = 2_000L, maxAge = day))
        assertEquals(false, isUnchanged("h", null, now = 2_000L, maxAge = day))
        // Re-sent once it's old even when nothing changed, and after a clock change.
        assertEquals(false, isUnchanged("h", last, now = 1_000L + day, maxAge = day))
        assertEquals(false, isUnchanged("h", UploadedRange("h", 5_000L), now = 1_000L, maxAge = day))
    }

    @Test
    fun `splits the window at fixed boundaries until one reaches the horizon`() {
        val step = Duration.ofDays(DeviceCalendarSync.RANGE_DAYS)
        val start = Instant.parse("2026-11-03T00:00:00Z")
        val ranges = syncRanges(start, start.plus(Duration.ofDays(3 * 365)))
        assertEquals(start, ranges.first().first)
        assertTrue(ranges.last().second >= start.plus(Duration.ofDays(3 * 365)))
        // Contiguous, and every range after the first is a whole step on a fixed boundary.
        ranges.zipWithNext().forEach { (a, b) -> assertEquals(a.second, b.first) }
        ranges.drop(1).forEach { (from, to) ->
            assertEquals(step, Duration.between(from, to))
            assertEquals(0L, from.epochSecond % step.seconds)
        }
        // A day later the later ranges are exactly the same.
        val tomorrow = syncRanges(start.plus(Duration.ofDays(1)), start.plus(Duration.ofDays(3 * 365 + 1)))
        assertEquals(ranges.drop(1).map { it.first }, tomorrow.drop(1).map { it.first }.take(ranges.size - 1))
    }
}
