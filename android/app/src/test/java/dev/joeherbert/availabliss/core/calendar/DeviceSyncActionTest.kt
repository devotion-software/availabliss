package dev.joeherbert.availabliss.core.calendar

import org.junit.Assert.assertEquals
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
    fun `skips an upload identical to the last one within a day`() {
        val day = DeviceCalendarSync.MAX_UNCHANGED_AGE_MS
        assertEquals(true, isUnchanged("h", lastHash = "h", lastSyncAt = 1_000L, now = 1_000L + day - 1))
        assertEquals(false, isUnchanged("h", lastHash = "other", lastSyncAt = 1_000L, now = 2_000L))
        assertEquals(false, isUnchanged("h", lastHash = null, lastSyncAt = 1_000L, now = 2_000L))
        assertEquals(false, isUnchanged("h", lastHash = "h", lastSyncAt = null, now = 2_000L))
        // Forced roughly daily even when nothing changed, and after a clock change.
        assertEquals(false, isUnchanged("h", lastHash = "h", lastSyncAt = 1_000L, now = 1_000L + day))
        assertEquals(false, isUnchanged("h", lastHash = "h", lastSyncAt = 5_000L, now = 1_000L))
    }
}
