package dev.joeherbert.availabliss.feature.common

import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.UserSummary
import dev.joeherbert.availabliss.core.network.model.UsernameCheck
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsernameInputTest {
    private val people = mapOf(
        "priya" to UserSummary("u2", "priya", "Priya Shah"),
        "samtaylor" to UserSummary("u1", "samtaylor", "Sam Taylor"),
    )
    private var lookups = 0

    private fun lookup(username: String): ApiResult<UsernameCheck> {
        lookups++
        val user = people[username]
        return ApiResult.Success(UsernameCheck(username, available = user == null || user.id == "u1", user = user))
    }

    @Test
    fun `editor checks locally, then looks up once typing pauses`() = runTest {
        val editor = UsernameEditor(backgroundScope, { lookup(it) })
        editor.prefill("samtaylor")
        assertEquals(UsernameStatus.Unchanged, editor.state.value.status)

        editor.onChange("p")
        assertEquals(UsernameStatus.Invalid("At least 3 characters"), editor.state.value.status)
        editor.onChange("pri")
        editor.onChange("@Priya")
        assertEquals("priya", editor.state.value.text)
        assertEquals(UsernameStatus.Checking, editor.state.value.status)
        advanceTimeBy(401)
        runCurrent()
        assertEquals(1, lookups)
        assertEquals(UsernameStatus.Taken, editor.state.value.status)
        assertFalse(editor.state.value.canSave)

        editor.onChange("priya2")
        advanceTimeBy(401)
        runCurrent()
        assertEquals(UsernameStatus.Available, editor.state.value.status)
        assertTrue(editor.state.value.canSave && editor.state.value.changed)

        assertTrue(editor.onSaveError(ApiError.Conflict()))
        assertEquals(UsernameStatus.Taken, editor.state.value.status)
        assertFalse(editor.onSaveError(ApiError.Network()))
    }

    @Test
    fun `recipients add emails as typed and people found by username`() = runTest {
        val recipients = InviteRecipients(backgroundScope, { lookup(it) }, selfId = { "u1" })
        recipients.onDraftChange("Leo@Example.com @priya nobody")
        assertFalse(recipients.addDraft())
        val s = recipients.state.value
        assertEquals(listOf("leo@example.com"), s.emails)
        assertEquals(listOf("priya"), s.usernames)
        assertEquals("nobody", s.draft)
        assertEquals(InviteRecipients.NO_ONE, s.error)

        recipients.onDraftChange("samtaylor")
        assertFalse(recipients.addDraft())
        assertEquals("That's you", recipients.state.value.error)

        recipients.onDraftChange("")
        assertTrue(recipients.addDraft())
        assertEquals(listOf("leo@example.com", "@priya"), recipients.state.value.labels)
    }
}
