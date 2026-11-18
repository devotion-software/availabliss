package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.model.GroupMute
import dev.joeherbert.availabliss.core.network.model.NotificationCategory
import dev.joeherbert.availabliss.core.network.model.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NotificationSettingsTest {
    private val now = Instant.parse("2026-11-18T21:15:42.123456Z")

    @Test
    fun muteDurations_countFromNow_toTheSecond() {
        assertEquals(Instant.parse("2026-11-19T05:15:42Z"), MuteDuration.EightHours.until(now))
        assertEquals(Instant.parse("2026-11-25T21:15:42Z"), MuteDuration.Week.until(now))
        assertNull(MuteDuration.UntilUnmuted.until(now))
    }

    @Test
    fun isMuted_forGood_orUntilAFutureInstant() {
        val settings = NotificationSettings(
            mutes = listOf(
                GroupMute("forever"),
                GroupMute("later", until = "2026-11-19T05:00:00.000Z"),
                GroupMute("ranOut", until = "2026-11-18T20:00:00.000Z"),
            ),
        )
        assertTrue(settings.isMuted("forever", now))
        assertTrue(settings.isMuted("later", now))
        assertFalse(settings.isMuted("ranOut", now))
        assertFalse(settings.isMuted("other", now))
    }

    @Test
    fun offWith_togglesOneCategory_andKeepsUnknownOnes() {
        val settings = NotificationSettings(off = listOf("nudges", "digest"))
        assertEquals(listOf("digest"), settings.offWith(NotificationCategory.Nudges, on = true))
        assertEquals(listOf("nudges", "digest", "chat"), settings.offWith(NotificationCategory.Chat, on = false))
        // Turning off something already off doesn't repeat it.
        assertEquals(listOf("nudges", "digest"), settings.offWith(NotificationCategory.Nudges, on = false))
        assertTrue(settings.isOff(NotificationCategory.Nudges))
        assertFalse(settings.isOff(NotificationCategory.Chat))
    }
}
