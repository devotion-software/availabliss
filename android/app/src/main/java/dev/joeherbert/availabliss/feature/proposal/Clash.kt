package dev.joeherbert.availabliss.feature.proposal

import dev.joeherbert.availabliss.core.common.firstName
import dev.joeherbert.availabliss.core.common.joinNamesSummary

/** Wording for proposals that clash with someone's busy time (mirrors web `proposals/clash.ts`). */
object Clash {

    /**
     * "Priya is busy then", "You're busy then", "You and Priya are busy then",
     * "Priya, Alex, Jo and 2 others are busy then". Null when nobody is.
     * [nameOf] gives a member's full name by user id (null for ex-members).
     */
    fun text(busyIds: List<String>, nameOf: (String) -> String?, meId: String?): String? {
        if (busyIds.isEmpty()) return null
        val mine = meId != null && meId in busyIds
        val others = busyIds.filter { it != meId }.map { firstNameOf(it, nameOf) }
        val names = if (mine) listOf("You") + others else others
        if (mine && names.size == 1) return "You're busy then"
        return "${names.joinNamesSummary(3)} ${if (names.size > 1) "are" else "is"} busy then"
    }

    /** Short form for compact rows: "Priya busy then" / "3 busy then". Null when nobody is. */
    fun short(busyIds: List<String>, nameOf: (String) -> String?): String? = when {
        busyIds.isEmpty() -> null
        busyIds.size > 1 -> "${busyIds.size} busy then"
        else -> "${firstNameOf(busyIds.first(), nameOf)} busy then"
    }

    /** The sentence after [text] on a proposal: who it overlaps for, and that they can still accept. */
    fun detail(busyIds: List<String>, meId: String?): String =
        if (busyIds.size == 1 && busyIds.first() == meId) {
            "Your calendar or blocked time overlaps. You can still accept if you can move things."
        } else {
            "Their calendars or blocked time overlap. They can still accept if they can move things."
        }

    private fun firstNameOf(id: String, nameOf: (String) -> String?) = nameOf(id)?.firstName() ?: "Someone"
}
