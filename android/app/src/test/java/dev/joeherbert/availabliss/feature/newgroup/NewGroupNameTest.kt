package dev.joeherbert.availabliss.feature.newgroup

import org.junit.Assert.assertEquals
import org.junit.Test

class NewGroupNameTest {
    @Test
    fun nextName_addsATwo() = assertEquals("Book club 2", NewGroupViewModel.nextName("Book club"))

    @Test
    fun nextName_countsOnFromANumber() = assertEquals("Book club 3", NewGroupViewModel.nextName(" Book club 2 "))

    @Test
    fun nextName_onlyCountsOnFromAPlainNumber() {
        assertEquals("Mum's 60th 2", NewGroupViewModel.nextName("Mum's 60th"))
        assertEquals("Christmas 2026 2", NewGroupViewModel.nextName("Christmas 2026"))
    }
}
