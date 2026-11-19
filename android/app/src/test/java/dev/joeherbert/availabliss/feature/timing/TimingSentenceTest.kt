package dev.joeherbert.availabliss.feature.timing

import dev.joeherbert.availabliss.core.network.model.RequirementLength
import dev.joeherbert.availabliss.core.network.model.TimeWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The timing sentence's wording. Same cases as web's `requirements.test.ts` and iOS's `TimingSentenceTests`. */
class TimingSentenceTest {
    private fun draft(preset: TimingPreset) = RequirementPresets.applyPreset(RequirementPresets.defaultDraft(LocalDate.of(2026, 11, 20), "UTC"), preset)

    @Test
    fun kinds() {
        assertEquals("a weekend", TimingSentence.kind(draft(TimingPreset.Weekend)))
        assertEquals("2 hours", TimingSentence.kind(draft(TimingPreset.TwoHours)))
        val hours = draft(TimingPreset.TwoHours)
        assertEquals("2½ hours", TimingSentence.kind(hours.copy(length = RequirementLength.Duration(150))))
        assertEquals("45 minutes", TimingSentence.kind(hours.copy(length = RequirementLength.Duration(45))))
        assertEquals("3 days in a row", TimingSentence.kind(hours.copy(length = RequirementLength.Days(3), window = null)))
    }

    @Test
    fun aSpan() {
        val span = draft(TimingPreset.Weekend).length as RequirementLength.Span
        assertEquals("Friday 18:00", TimingSentence.start(span))
        assertEquals("Sunday night", TimingSentence.end(span))
        assertEquals("the following Sunday 17:00", TimingSentence.end(span.copy(endTime = "17:00", weeksLater = 1)))
    }

    @Test
    fun timesAndDays() {
        assertEquals("any time of day", TimingSentence.window(null))
        assertEquals("between 18:00 and midnight", TimingSentence.window(TimeWindow("18:00", "23:59")))
        assertEquals("any day", TimingSentence.days(emptySet()))
        assertEquals("Fridays to Sundays", TimingSentence.days(setOf(4, 5, 6)))
        assertEquals("Mondays, Wednesdays and Fridays", TimingSentence.days(setOf(0, 2, 4)))
        assertEquals("weekends", TimingSentence.days(setOf(5, 6)))
    }
}
