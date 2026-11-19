package dev.joeherbert.availabliss.feature.members

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InviteExpiryTest {
    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun `today, tomorrow, then the date`() {
        assertEquals("Expires today", expiryLabel(today, today))
        assertEquals("Expires today", expiryLabel(today.minusDays(1), today))
        assertEquals("Expires tomorrow", expiryLabel(today.plusDays(1), today))
        assertEquals("Expires 26 Nov", expiryLabel(today.plusDays(7), today))
    }
}
