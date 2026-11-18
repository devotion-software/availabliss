package dev.joeherbert.availabliss.feature.availability

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.NextSuggestion
import dev.joeherbert.availabliss.core.network.model.Rhythm

/** Wording for the group calendar's "Next one" card (a group with a rhythm). Pure, so it's unit tested. */
object NextOne {
    /** Null when the server's target date can't be read. [nameOf] gives "You" or a first name for a user id. */
    fun ui(next: NextSuggestion, rhythm: Rhythm, nameOf: (String) -> String): NextOneUi? {
        val target = DateTimes.parseDate(next.target) ?: return null
        val slot = next.slot
        return NextOneUi(
            target = target,
            targetLabel = DateTimes.shortDay(target),
            rhythmLabel = rhythm.frequency?.label ?: "Regularly",
            slot = slot,
            label = slot?.let { DateTimes.rangeLabel(it.start, it.end, AvailabilityUiMapper.isAllDay(it)) },
            note = slot?.let { BestOptions.missingLine(it.missingMemberIds, nameOf) ?: "Everyone's free" },
        )
    }
}
