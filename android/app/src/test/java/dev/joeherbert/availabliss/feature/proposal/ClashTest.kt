package dev.joeherbert.availabliss.feature.proposal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ported from web `proposals/clash.test.ts`. */
class ClashTest {
    private val names = mapOf("me" to "Sam Taylor", "p" to "Priya Shah", "a" to "Alex", "j" to "Jo", "t" to "Tom", "l" to "Lee")
    private val nameOf: (String) -> String? = names::get

    @Test
    fun text() {
        assertNull(Clash.text(emptyList(), nameOf, "me"))
        assertEquals("Priya is busy then", Clash.text(listOf("p"), nameOf, "me"))
        assertEquals("You're busy then", Clash.text(listOf("me"), nameOf, "me"))
        assertEquals("You and Priya are busy then", Clash.text(listOf("me", "p"), nameOf, "me"))
        assertEquals("Priya, Alex, Jo and 2 others are busy then", Clash.text(listOf("p", "a", "j", "t", "l"), nameOf, "me"))
        assertEquals("Someone is busy then", Clash.text(listOf("gone"), nameOf, "me"))
    }

    @Test
    fun short() {
        assertNull(Clash.short(emptyList(), nameOf))
        assertEquals("Priya busy then", Clash.short(listOf("p"), nameOf))
        assertEquals("2 busy then", Clash.short(listOf("p", "a"), nameOf))
    }

    @Test
    fun detail() {
        assertEquals("Your calendar or blocked time overlaps. You can still accept if you can move things.", Clash.detail(listOf("me"), "me"))
        assertEquals("Their calendars or blocked time overlap. They can still accept if they can move things.", Clash.detail(listOf("me", "p"), "me"))
    }
}
