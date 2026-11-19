package dev.joeherbert.availabliss.feature.members

import dev.joeherbert.availabliss.core.network.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameEditorUiTest {
    private fun member(name: String, nameInGroup: String?, me: Boolean = false) =
        MemberUi("u", name, null, Role.Member, true, me, canPromote = false, canDemote = false, canRemove = false, nameInGroup = nameInGroup, canRename = true)

    @Test
    fun `unchanged, or blank with no name set, can't be saved`() {
        val m = member("Sam", null)
        assertFalse(NameEditorUi(m, draft = "Sam").canSave)
        assertFalse(NameEditorUi(m, draft = " Sam ").canSave)
        assertFalse(NameEditorUi(m, draft = "  ").canSave)
    }

    @Test
    fun `a new name is saved trimmed`() {
        val e = NameEditorUi(member("Sam", null), draft = "  Sammy ")
        assertTrue(e.canSave)
        assertEquals("Sammy", e.toSave)
    }

    @Test
    fun `blank, or your own account name, goes back to the account name`() {
        val set = member("Sammy", "Sammy", me = true)
        assertNull(NameEditorUi(set, draft = "").toSave)
        assertTrue(NameEditorUi(set, draft = "").canSave)
        val typedAccount = NameEditorUi(set, draft = "Sam", accountName = "Sam")
        assertNull(typedAccount.toSave)
        assertTrue(typedAccount.canSave)
    }

    @Test
    fun `nothing can be saved while saving`() {
        assertFalse(NameEditorUi(member("Sam", null), draft = "Sammy", saving = true).canSave)
    }
}
