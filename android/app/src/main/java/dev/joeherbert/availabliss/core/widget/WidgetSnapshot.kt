package dev.joeherbert.availabliss.core.widget

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.network.model.GroupStatus
import dev.joeherbert.availabliss.core.network.model.GroupSummary
import dev.joeherbert.availabliss.core.network.model.MyPlansResponse
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the home-screen widgets show, cached by [WidgetSnapshotStore] so they can draw without
 * the network. The app writes it whenever it loads plans or groups, and after a push.
 */
@Serializable
data class WidgetSnapshot(
    val signedIn: Boolean = false,
    /**
     * Upcoming plans you're going to, soonest first (a few, so the widget can move on to the
     * next one when a plan ends before the app writes again). Null until plans have loaded.
     */
    val plans: List<WidgetPlan>? = null,
    /** Proposals waiting for your reply, a few of them. Null until groups have loaded. */
    val waiting: List<WidgetWaiting>? = null,
    /** How many proposals are waiting in all (can be more than [waiting] holds). */
    val waitingCount: Int = 0,
) {
    companion object {
        val SignedOut = WidgetSnapshot(signedIn = false)
    }
}

/** A plan for the "Next plan" widget. Times are the API's ISO 8601 UTC timestamps. */
@Serializable
data class WidgetPlan(
    val title: String,
    val groupName: String,
    val groupId: String,
    val start: String,
    val end: String,
    val allDay: Boolean = false,
    val proposalId: String? = null,
) {
    /** Opens the proposal the plan came from, or the group when it was added straight to the calendar. */
    val link: DeepLink get() = proposalId?.let(DeepLink::Proposal) ?: DeepLink.GroupCalendar(groupId)
}

/** A group with a proposal waiting for your reply, for the "Waiting for you" widget. */
@Serializable
data class WidgetWaiting(
    val groupName: String,
    val proposalId: String,
    val groupId: String = "",
) {
    val link: DeepLink get() = DeepLink.Proposal(proposalId)
}

/** Choosing and labelling what the widgets show. Pure, so it's unit tested. */
object WidgetSnapshots {
    /** Plans kept in the snapshot. */
    const val MAX_PLANS = 5

    /** Waiting proposals kept in the snapshot (the widget lists up to [SHOWN_WAITING]). */
    const val MAX_WAITING = 5

    /** Rows the "Waiting for you" widget lists. */
    const val SHOWN_WAITING = 3

    /** The plans you're going to that haven't ended at [now], soonest first, for the snapshot. */
    fun plans(response: MyPlansResponse, now: Instant): List<WidgetPlan> =
        response.going
            .filter { plan -> DateTimes.parseInstant(plan.end)?.isAfter(now) ?: false }
            .sortedBy { DateTimes.parseInstant(it.start) }
            .take(MAX_PLANS)
            .map { plan ->
                WidgetPlan(
                    title = plan.title,
                    groupName = plan.groupName,
                    groupId = plan.groupId,
                    start = plan.start,
                    end = plan.end,
                    allDay = plan.allDay,
                    proposalId = plan.proposalId,
                )
            }

    /** Groups with a proposal waiting for your reply, in the groups list's order (archived ones left out). */
    fun waiting(groups: List<GroupSummary>): List<WidgetWaiting> = groups
        .filterNot { it.archived }
        .mapNotNull { group ->
            (group.status as? GroupStatus.NeedsReply)?.let { WidgetWaiting(group.name, it.proposalId, group.id) }
        }

    /** The plan to show at [now]: the first that hasn't ended (one that's on now counts). */
    fun nextPlan(plans: List<WidgetPlan>, now: Instant): WidgetPlan? =
        plans.firstOrNull { plan -> DateTimes.parseInstant(plan.end)?.isAfter(now) ?: false }

    /**
     * When [plan] is, relative to [now]: "Now", "Today 19:00", "Tomorrow 19:00",
     * "Fri 4 Dec · 19:00", and for an all-day plan "Today", "Tomorrow" or "Fri 4 Dec".
     */
    fun whenLabel(plan: WidgetPlan, now: Instant, zone: ZoneId = DateTimes.zone): String {
        val start = DateTimes.parseInstant(plan.start) ?: return ""
        val today = now.atZone(zone).toLocalDate()
        if (plan.allDay) {
            // Local midnights; the end is the exclusive midnight after the last day.
            val startDay = start.atZone(zone).toLocalDate()
            val endDay = DateTimes.parseInstant(plan.end)?.atZone(zone)?.toLocalDate()
            val onNow = !startDay.isAfter(today) && (endDay == null || endDay.isAfter(today))
            return if (onNow) "Today" else dayLabel(startDay, today)
        }
        if (!start.isAfter(now)) return "Now"
        val local = start.atZone(zone).toLocalDateTime()
        val day = local.toLocalDate()
        val time = DateTimes.time(local)
        return when (day) {
            today -> "Today $time"
            today.plusDays(1) -> "Tomorrow $time"
            else -> "${DateTimes.shortDay(day)} · $time"
        }
    }

    /** "Today", "Tomorrow" or "Fri 4 Dec". */
    private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> DateTimes.shortDay(day)
    }

    /** "1 proposal waiting for you", "3 proposals waiting for you". */
    fun waitingTitle(count: Int): String = if (count == 1) "1 proposal waiting for you" else "$count proposals waiting for you"

    /** "+2 more" under the listed rows, or null when every waiting proposal is listed. */
    fun moreLabel(count: Int, shown: Int): String? = (count - shown).takeIf { it > 0 }?.let { "+$it more" }
}
