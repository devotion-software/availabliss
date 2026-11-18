package dev.joeherbert.availabliss.feature.myplans

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.MyPlan
import dev.joeherbert.availabliss.core.network.model.MyPlansResponse
import dev.joeherbert.availabliss.core.network.model.ResponseKind
import dev.joeherbert.availabliss.feature.common.AddAllResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** My plans: rows, pills, the in-your-calendar mark and "Add all to calendar". */
class MyPlansTest {
    private fun plan(
        id: String,
        start: LocalDateTime = LocalDateTime.of(2026, 11, 20, 18, 30),
        end: LocalDateTime = LocalDateTime.of(2026, 11, 20, 21, 0),
        allDay: Boolean = false,
        response: ResponseKind? = ResponseKind.Accepted,
        proposalId: String? = "p-$id",
    ) = MyPlan(
        id = id, groupId = "g", proposalId = proposalId, title = "Plan $id",
        start = DateTimes.toIso(start), end = DateTimes.toIso(end), allDay = allDay,
        groupName = "Book club", myResponse = response,
    )

    @Test
    fun timeLabels() {
        assertEquals("18:30 – 21:00", MyPlans.timeLabel(plan("a")))
        assertEquals("All day", MyPlans.timeLabel(plan("b", LocalDateTime.of(2026, 11, 20, 0, 0), LocalDateTime.of(2026, 11, 21, 0, 0), allDay = true)))
        assertEquals("Fri 20 – Sun 22 Nov", MyPlans.timeLabel(plan("c", LocalDateTime.of(2026, 11, 20, 18, 0), LocalDateTime.of(2026, 11, 22, 23, 0))))
    }

    @Test
    fun pillsOnlyForUpcomingMaybesAndUnanswered() {
        assertEquals("Maybe", MyPlans.pill(ResponseKind.Maybe))
        assertEquals("Not replied", MyPlans.pill(ResponseKind.Pending))
        assertNull(MyPlans.pill(ResponseKind.Accepted))
        // Added straight to the calendar: no reply to show.
        assertNull(MyPlans.pill(null))
        val sections = MyPlans.sections(
            MyPlansResponse(going = listOf(plan("a", response = ResponseKind.Maybe)), past = listOf(plan("b", response = ResponseKind.Pending))),
            emptySet(),
        )
        assertEquals("Maybe", sections.goingTo.single().pill)
        assertNull(sections.past.single().pill)
        assertEquals("NOV", sections.goingTo.single().month)
        assertEquals("20", sections.goingTo.single().day)
    }

    @Test
    fun inCalendarByEventOrProposalAndAddAllSkipsThose() {
        val plans = MyPlansResponse(
            going = listOf(plan("a"), plan("b"), plan("c", proposalId = null)),
            notGoing = listOf(plan("d", response = ResponseKind.Declined)),
        )
        // "a" was added from its event, "b" from its proposal before the event existed.
        val keys = setOf("a", "p-b")
        val sections = MyPlans.sections(plans, keys)
        assertEquals(listOf(true, true, false), sections.goingTo.map { it.inCalendar })
        // Only plans you're going to, not yet added.
        assertEquals(listOf("c"), MyPlans.toAdd(plans, keys).map { it.first })
        assertTrue(MyPlans.toAdd(plans, setOf("a", "p-b", "c")).isEmpty())
        assertFalse(sections.isEmpty)
    }

    @Test
    fun addAllMessages() {
        assertEquals("Added 3 plans to your calendar", AddAllResult(3).message)
        assertEquals("Added 1 plan to your calendar", AddAllResult(1).message)
    }

    @Test
    fun decodesWithoutAReply() {
        val json = NetworkModule.provideJson()
        val body = """{"going":[{"id":"e","groupId":"g","proposalId":null,"title":"T","location":null,"note":null,"start":"2026-11-20T18:00:00Z","end":"2026-11-20T21:00:00Z","allDay":false,"image":null,"createdBy":"u","createdAt":"2026-11-01T00:00:00Z","groupName":"Book club","myResponse":null}],"notGoing":[],"past":[{"id":"f","groupId":"g","title":"T","start":"2026-10-20T18:00:00Z","end":"2026-10-20T21:00:00Z","createdBy":"u","createdAt":"2026-10-01T00:00:00Z","groupName":"Book club","myResponse":"maybe"}]}"""
        val plans = json.decodeFromString(MyPlansResponse.serializer(), body)
        assertNull(plans.going.single().myResponse)
        assertEquals(ResponseKind.Maybe, plans.past.single().myResponse)
        assertEquals("Book club", plans.going.single().event.groupName)
    }
}
