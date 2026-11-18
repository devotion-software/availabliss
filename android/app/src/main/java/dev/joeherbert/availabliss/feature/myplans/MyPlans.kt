package dev.joeherbert.availabliss.feature.myplans

import dev.joeherbert.availabliss.core.calendar.CalendarEventDraft
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.MyPlan
import dev.joeherbert.availabliss.core.network.model.MyPlansResponse
import dev.joeherbert.availabliss.core.network.model.ResponseKind

/** One row of My plans: date tile, title, time, group, your reply and whether it's in your calendar. */
data class MyPlanRowUi(
    val id: String,
    val groupId: String,
    /** Tapping opens this proposal, or the group when the plan was added straight to the calendar. */
    val proposalId: String?,
    /** "NOV". */
    val month: String,
    /** "13". */
    val day: String,
    val title: String,
    /** "18:30 – 21:00", "from 18:00", "All day", or the days of a longer plan ("Fri 13 – Sun 15 Nov"). */
    val time: String,
    val groupName: String,
    /** "Maybe" or "Not replied" for an upcoming plan; null otherwise. */
    val pill: String? = null,
    /** In one of your calendars (added from this phone). */
    val inCalendar: Boolean = false,
)

/** My plans' three sections, as rows. */
data class MyPlanSections(
    val goingTo: List<MyPlanRowUi> = emptyList(),
    val notGoing: List<MyPlanRowUi> = emptyList(),
    val past: List<MyPlanRowUi> = emptyList(),
) {
    val isEmpty: Boolean get() = goingTo.isEmpty() && notGoing.isEmpty() && past.isEmpty()
}

/** Rows and "Add all to calendar" for the My plans screen. Pure, so it's unit tested. */
object MyPlans {
    /**
     * Keys a plan may be remembered under as added to a calendar: its event id, or (added from
     * the proposal before its event existed) its proposal id.
     */
    fun calendarKeys(plan: MyPlan): List<String> = listOfNotNull(plan.id, plan.proposalId)

    /** In a calendar, given the keys known to be in one ([AddToCalendarState.inCalendar]'s keys). */
    fun inCalendar(plan: MyPlan, keys: Set<String>): Boolean = calendarKeys(plan).any { it in keys }

    fun sections(plans: MyPlansResponse, inCalendar: Set<String>): MyPlanSections = MyPlanSections(
        goingTo = plans.going.map { row(it, inCalendar, upcoming = true) },
        notGoing = plans.notGoing.map { row(it, inCalendar, upcoming = false) },
        past = plans.past.map { row(it, inCalendar, upcoming = false) },
    )

    fun row(plan: MyPlan, inCalendar: Set<String>, upcoming: Boolean): MyPlanRowUi {
        val date = DateTimes.localDate(plan.start)
        return MyPlanRowUi(
            id = plan.id,
            groupId = plan.groupId,
            proposalId = plan.proposalId,
            month = date?.let(DateTimes::monthAbbrev).orEmpty(),
            day = date?.dayOfMonth?.toString().orEmpty(),
            title = plan.title,
            time = timeLabel(plan),
            groupName = plan.groupName,
            pill = if (upcoming) pill(plan.myResponse) else null,
            inCalendar = inCalendar(plan, inCalendar),
        )
    }

    /** "18:30 – 21:00", "from 18:00", "All day", or "Fri 13 – Sun 15 Nov" for a plan over several days. */
    fun timeLabel(plan: MyPlan): String {
        val (days, time) = DateTimes.rangeParts(plan.start, plan.end, plan.allDay)
        return when {
            time != null -> time
            // A single day: the tile already says which.
            !days.contains("–") -> "All day"
            else -> days
        }
    }

    /** "Maybe", "Not replied", or nothing for a yes (or a plan added straight to the calendar). */
    fun pill(response: ResponseKind?): String? = when (response) {
        ResponseKind.Maybe -> "Maybe"
        ResponseKind.Pending -> "Not replied"
        else -> null
    }

    /** The plans you're going to that aren't in a calendar yet, for "Add all to calendar", by key. */
    fun toAdd(plans: MyPlansResponse, inCalendar: Set<String>): List<Pair<String, CalendarEventDraft>> =
        plans.going.filterNot { inCalendar(it, inCalendar) }.mapNotNull { plan ->
            CalendarEventDraft.from(plan.event)?.let { plan.id to it }
        }
}
