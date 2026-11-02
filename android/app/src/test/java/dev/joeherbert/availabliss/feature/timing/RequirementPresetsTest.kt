package dev.joeherbert.availabliss.feature.timing

import dev.joeherbert.availabliss.core.network.model.RequirementLength
import dev.joeherbert.availabliss.core.network.model.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RequirementPresetsTest {
    private val base = RequirementPresets.defaultDraft(LocalDate.of(2026, 11, 1), "Europe/London")

    private fun requirementFor(preset: TimingPreset) = RequirementPresets.applyPreset(base, preset).toRequirement()

    @Test
    fun twoHours_isDurationWithWindow() {
        val r = requirementFor(TimingPreset.TwoHours)
        assertEquals(RequirementLength.Duration(120), r.length)
        assertEquals(TimeWindow("08:00", "23:00"), r.window)
    }

    @Test
    fun evening_isOneDayFromSix() {
        val r = requirementFor(TimingPreset.Evening)
        assertEquals(RequirementLength.Days(1), r.length)
        assertEquals(TimeWindow("18:00", "23:59"), r.window)
    }

    @Test
    fun oneDay_hasNoWindow() {
        val r = requirementFor(TimingPreset.OneDay)
        assertEquals(RequirementLength.Days(1), r.length)
        assertNull(r.window)
    }

    @Test
    fun weekend_isFridaySixToSunday() {
        val r = requirementFor(TimingPreset.Weekend)
        assertEquals(RequirementLength.Span(4, "18:00", 6, "23:59", 0), r.length)
        assertNull(r.window)
        assertEquals(listOf(4, 5, 6), r.days)
    }

    @Test
    fun week_isSevenDays() {
        val r = requirementFor(TimingPreset.Week)
        assertEquals(RequirementLength.Days(7), r.length)
        assertNull(r.window)
    }

    @Test
    fun customSpan_tueToFollowingThu() {
        val span = RequirementLength.Span(1, "00:00", 3, "23:59", 1)
        val draft = RequirementPresets.withLength(RequirementPresets.applyCustomKind(base, CustomKind.Span), span, null)
        assertEquals(TimingPreset.Custom, draft.preset)
        assertEquals(span, draft.toRequirement().length)
    }

    @Test
    fun detect_roundTripsEveryPreset() {
        TimingPreset.entries.filter { it != TimingPreset.Custom }.forEach { preset ->
            val r = requirementFor(preset)
            assertEquals(preset, RequirementPresets.fromRequirement(r).preset)
        }
    }

    @Test
    fun editingEveningStart_becomesCustom() {
        val evening = RequirementPresets.applyPreset(base, TimingPreset.Evening)
        val edited = RequirementPresets.withLength(evening, evening.length, TimeWindow("19:00", "23:59"))
        assertEquals(TimingPreset.Custom, edited.preset)
        assertEquals(CustomKind.Days, edited.customKind)
    }

    @Test
    fun spanDays_wrapsPastSunday() {
        assertEquals(setOf(5, 6, 0), RequirementPresets.spanDays(RequirementLength.Span(5, "09:00", 0, "18:00", 0)))
    }

    @Test
    fun slotNoun_matchesPreset() {
        assertEquals("weekends", requirementFor(TimingPreset.Weekend).slotNoun(plural = true))
        assertEquals("evening", requirementFor(TimingPreset.Evening).slotNoun())
    }

    @Test
    fun week_opensUpEveryDay_shorterPresetsKeepTheChosenDays() {
        // A weekend group as stored by the server: Fri–Sun.
        val weekend = RequirementPresets.fromRequirement(requirementFor(TimingPreset.Weekend))
        assertEquals(setOf(4, 5, 6), weekend.days)
        assertEquals(setOf(4, 5, 6), RequirementPresets.applyPreset(weekend, TimingPreset.Evening).days)
        val week = RequirementPresets.applyPreset(weekend, TimingPreset.Week)
        assertEquals((0..6).toSet(), week.days)
        assertEquals(TimingPreset.Week, RequirementPresets.fromRequirement(week.toRequirement()).preset)
        // "Any day" (empty) already allows a week.
        assertEquals(emptySet<Int>(), RequirementPresets.applyPreset(base, TimingPreset.Week).days)
    }

    @Test
    fun summary_describesTheTiming() {
        val weekend = RequirementPresets.applyPreset(base, TimingPreset.Weekend)
        assertEquals("A weekend · Fri from 18:00 → Sun · Nov – Feb", TimingLabels.summary(weekend))
        val evening = RequirementPresets.applyPreset(base, TimingPreset.Evening).copy(days = setOf(0, 1, 2, 3, 4))
        assertEquals("An evening from 18:00 · weekdays · Nov – Feb", TimingLabels.summary(evening))
        assertEquals("2 hours · 08:00–23:00 · any day · Nov – Feb", TimingLabels.summary(base))
    }
}
