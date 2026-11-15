package dev.joeherbert.availabliss.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernamesTest {
    @Test
    fun `normalize trims, drops leading at signs and lowercases`() {
        assertEquals("samtaylor", Usernames.normalize("  @@SamTaylor "))
        assertEquals("sam.t_1", Usernames.normalize("sam.T_1"))
    }

    @Test
    fun `problems match the server's rules`() {
        assertNull(Usernames.problem("sam"))
        assertNull(Usernames.problem("sam.taylor_99"))
        assertNull(Usernames.problem("a".repeat(20)))
        assertEquals("At least 3 characters", Usernames.problem("sa"))
        assertEquals("At most 20 characters", Usernames.problem("a".repeat(21)))
        assertEquals("Only letters, numbers, _ and .", Usernames.problem("sam-taylor"))
        assertEquals("Only letters, numbers, _ and .", Usernames.problem("Sam"))
        assertEquals("A dot can't come first, last or twice in a row", Usernames.problem(".sam"))
        assertEquals("A dot can't come first, last or twice in a row", Usernames.problem("sam."))
        assertEquals("A dot can't come first, last or twice in a row", Usernames.problem("sam..t"))
        assertEquals("That one's reserved", Usernames.problem("admin"))
    }

    @Test
    fun `an at sign after the first character with a dotted domain is an email`() {
        assertEquals(InviteInput.Email("sam@example.com"), InviteInput.parse("Sam@Example.com"))
        assertEquals(InviteInput.Username("sam"), InviteInput.parse("@Sam"))
        assertEquals(InviteInput.Username("sam"), InviteInput.parse("sam"))
        assertEquals(InviteInput.Username("sam@localhost"), InviteInput.parse("sam@localhost"))
        assertEquals(InviteInput.Username("example.com"), InviteInput.parse("@example.com"))
    }

    @Test
    fun `tokens split on commas, semicolons, spaces and new lines`() {
        assertEquals(listOf("sam@example.com", "@priya", "leo"), InviteInput.tokens(" sam@example.com, @priya;\nleo "))
        assertTrue(InviteInput.endsEntry("sam,"))
        assertTrue(InviteInput.endsEntry("sam "))
        assertFalse(InviteInput.endsEntry("sam"))
        assertFalse(InviteInput.isValidEmail("sam@@example.com"))
    }
}
