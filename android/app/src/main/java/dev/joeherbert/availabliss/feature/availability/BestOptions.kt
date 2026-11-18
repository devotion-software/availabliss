package dev.joeherbert.availabliss.feature.availability

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.common.joinNamesSummary
import dev.joeherbert.availabliss.core.network.model.Slot
import java.time.Instant

/** One row of the "Best options" card on the group calendar. */
data class BestOptionUi(
    val slot: Slot,
    /** "Fri 13 – Sun 15 Nov". */
    val label: String,
    /** "Best for the group": the top option when there are Pro scores and nobody's missing. */
    val best: Boolean = false,
    /** "Ana can't make it", "3 people can't make it"; null when everyone's free. */
    val missing: String? = null,
    /** Pro: "Fair for Ana, who missed out last time". */
    val fair: String? = null,
    /** How many members' preferred times it fits. */
    val preferredCount: Int = 0,
)

/**
 * Ranking and wording for the group's best options (mirrors web `group/best.ts`).
 * Pure, so it's unit tested.
 */
object BestOptions {
    /** How many options the card shows. */
    const val MAX = 3

    /**
     * Fewer people missing first, then the Pro "best date" score (higher first, when there is
     * one), then how many people's preferences it fits, then soonest.
     */
    val ranking: Comparator<Slot> = compareBy<Slot> { it.missingMemberIds.size }
        .thenByDescending { it.score ?: Double.NEGATIVE_INFINITY }
        .thenByDescending { it.preferredBy.size }
        .thenBy { it.start }

    fun rank(slots: List<Slot>): List<Slot> = slots.sortedWith(ranking)

    /**
     * The top [max] slots that haven't ended by [now], ranked, as card rows. [nameOf] gives the
     * name to show for a user id ("You" for the viewer, else a first name).
     */
    fun options(slots: List<Slot>, now: Instant, nameOf: (String) -> String, max: Int = MAX): List<BestOptionUi> {
        val upcoming = rank(slots.filter { slot -> DateTimes.parseInstant(slot.end)?.isAfter(now) ?: true }).take(max)
        val scored = upcoming.any { it.score != null }
        return upcoming.mapIndexed { i, slot ->
            BestOptionUi(
                slot = slot,
                label = slot.label,
                best = scored && i == 0 && slot.missingMemberIds.isEmpty(),
                missing = missingLine(slot.missingMemberIds, nameOf),
                fair = fairLine(slot.fairFor, nameOf),
                preferredCount = slot.preferredBy.size,
            )
        }
    }

    /** "Ana can't make it", "Ana and Tom can't make it", "You can't make it", "3 people can't make it". */
    fun missingLine(ids: List<String>, nameOf: (String) -> String): String? = when {
        ids.isEmpty() -> null
        ids.size > 2 -> "${ids.size} people can't make it"
        else -> "${ids.map(nameOf).joinNamesSummary(2)} can't make it"
    }

    /** "Fair for Ana, who missed out last time", "Fair for you and Ana, who missed out last time". */
    fun fairLine(ids: List<String>, nameOf: (String) -> String): String? {
        if (ids.isEmpty()) return null
        val names = ids.map(nameOf).map { if (it == "You") "you" else it }
        return "Fair for ${names.joinNamesSummary(2)}, who missed out last time"
    }
}
