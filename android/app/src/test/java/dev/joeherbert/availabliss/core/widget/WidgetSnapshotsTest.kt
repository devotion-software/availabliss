package dev.joeherbert.availabliss.core.widget

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.network.model.GroupStatus
import dev.joeherbert.availabliss.core.network.model.GroupSummary
import dev.joeherbert.availabliss.core.network.model.MyPlan
import dev.joeherbert.availabliss.core.network.model.MyPlansResponse
import dev.joeherbert.availabliss.core.network.model.Role
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** The widgets' snapshot: which plan is next, which proposals are waiting, and their labels. */
class WidgetSnapshotsTest {
    private val zone = ZoneId.of("Europe/London")
    private lateinit var defaultLocale: Locale

    /** Wednesday 18 November 2026, 10:00 in London. */
    private val now: Instant = at(2026, 11, 18, 10, 0)

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
    }

    @After
    fun tearDown() = Locale.setDefault(defaultLocale)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    private fun myPlan(id: String, start: Instant, end: Instant, allDay: Boolean = false, proposalId: String? = "p-$id") = MyPlan(
        id = id, groupId = "g-$id", proposalId = proposalId, title = "Plan $id",
        start = DateTimes.toIso(start), end = DateTimes.toIso(end), allDay = allDay, groupName = "Group $id",
    )

    private fun widgetPlan(start: Instant, end: Instant, allDay: Boolean = false) = WidgetPlan(
        title = "Dinner", groupName = "Book club", groupId = "g1",
        start = DateTimes.toIso(start), end = DateTimes.toIso(end), allDay = allDay,
    )

    private fun group(id: String, status: GroupStatus, archived: Boolean = false) = GroupSummary(
        id = id, name = "Group $id", monogram = "G", role = Role.Member, archived = archived,
        memberCount = 3, syncedCount = 3, requirementSummary = "", status = status,
    )

    @Test
    fun `snapshot plans drop ended ones, sort soonest first and keep a few`() {
        val response = MyPlansResponse(
            going = listOf(
                myPlan("later", at(2026, 11, 25, 19), at(2026, 11, 25, 22)),
                myPlan("ended", at(2026, 11, 17, 19), at(2026, 11, 17, 22)),
                myPlan("now", at(2026, 11, 18, 9), at(2026, 11, 18, 11)),
                myPlan("soon", at(2026, 11, 19, 19), at(2026, 11, 19, 22)),
            ) + (1..6).map { myPlan("x$it", at(2026, 12, it, 19), at(2026, 12, it, 22)) },
        )
        val plans = WidgetSnapshots.plans(response, now)
        assertEquals(WidgetSnapshots.MAX_PLANS, plans.size)
        assertEquals(listOf("Plan now", "Plan soon", "Plan later", "Plan x1", "Plan x2"), plans.map { it.title })
        assertEquals("Group soon", plans[1].groupName)
        assertEquals("g-soon", plans[1].groupId)
    }

    @Test
    fun `next plan is the first that hasn't ended`() {
        val ended = widgetPlan(at(2026, 11, 18, 8), at(2026, 11, 18, 9))
        val next = widgetPlan(at(2026, 11, 18, 19), at(2026, 11, 18, 21))
        assertEquals(next, WidgetSnapshots.nextPlan(listOf(ended, next), now))
        assertNull(WidgetSnapshots.nextPlan(listOf(ended), now))
        assertNull(WidgetSnapshots.nextPlan(emptyList(), now))
    }

    @Test
    fun `when labels say today, tomorrow or the day`() {
        fun label(start: Instant, end: Instant, allDay: Boolean = false) =
            WidgetSnapshots.whenLabel(widgetPlan(start, end, allDay), now, zone)

        assertEquals("Now", label(at(2026, 11, 18, 9), at(2026, 11, 18, 11)))
        assertEquals("Today 19:00", label(at(2026, 11, 18, 19), at(2026, 11, 18, 21)))
        assertEquals("Tomorrow 19:00", label(at(2026, 11, 19, 19), at(2026, 11, 19, 21)))
        assertEquals("Fri 4 Dec · 19:30", label(at(2026, 12, 4, 19, 30), at(2026, 12, 4, 22)))
        // All day: local midnights, the end exclusive.
        assertEquals("Today", label(at(2026, 11, 18, 0), at(2026, 11, 19, 0), allDay = true))
        assertEquals("Today", label(at(2026, 11, 17, 0), at(2026, 11, 20, 0), allDay = true))
        assertEquals("Tomorrow", label(at(2026, 11, 19, 0), at(2026, 11, 20, 0), allDay = true))
        assertEquals("Fri 4 Dec", label(at(2026, 12, 4, 0), at(2026, 12, 5, 0), allDay = true))
    }

    @Test
    fun `waiting lists groups with a proposal needing your reply, not archived ones`() {
        val waiting = WidgetSnapshots.waiting(
            listOf(
                group("a", GroupStatus.NeedsReply("p1")),
                group("b", GroupStatus.Searching),
                group("c", GroupStatus.Matches(2, "evening")),
                group("d", GroupStatus.NeedsReply("p2"), archived = true),
                group("e", GroupStatus.NeedsReply("p3")),
            ),
        )
        assertEquals(listOf(WidgetWaiting("Group a", "p1", "a"), WidgetWaiting("Group e", "p3", "e")), waiting)
    }

    @Test
    fun `taps open the proposal, or the group for a plan without one`() {
        assertEquals(DeepLink.Proposal("p9"), WidgetWaiting("Book club", "p9").link)
        val plan = widgetPlan(at(2026, 11, 19, 19), at(2026, 11, 19, 21))
        assertEquals(DeepLink.GroupCalendar("g1"), plan.link)
        assertEquals(DeepLink.Proposal("p1"), plan.copy(proposalId = "p1").link)
    }

    @Test
    fun `waiting labels`() {
        assertEquals("1 proposal waiting for you", WidgetSnapshots.waitingTitle(1))
        assertEquals("4 proposals waiting for you", WidgetSnapshots.waitingTitle(4))
        assertEquals("+2 more", WidgetSnapshots.moreLabel(5, 3))
        assertNull(WidgetSnapshots.moreLabel(3, 3))
    }

    @Test
    fun `pushes about proposals, events and reminders refresh the widgets`() {
        assertTrue(Widgets.refreshesOn("proposal.created"))
        assertTrue(Widgets.refreshesOn("proposal.confirmed"))
        assertTrue(Widgets.refreshesOn("event.created"))
        assertTrue(Widgets.refreshesOn("reminder"))
        assertFalse(Widgets.refreshesOn("message"))
        assertFalse(Widgets.refreshesOn(null))
    }

    @Test
    fun `snapshot survives a round trip through JSON`() {
        val snapshot = WidgetSnapshot(
            signedIn = true,
            plans = listOf(widgetPlan(at(2026, 11, 19, 19), at(2026, 11, 19, 21)).copy(proposalId = "p1")),
            waiting = listOf(WidgetWaiting("Book club", "p2", "g1")),
            waitingCount = 1,
        )
        val json = Json.encodeToString(WidgetSnapshot.serializer(), snapshot)
        assertEquals(snapshot, Json.decodeFromString(WidgetSnapshot.serializer(), json))
        // Never written: no plans or groups loaded yet, as opposed to none at all.
        assertNull(WidgetSnapshot.SignedOut.plans)
        assertFalse(WidgetSnapshot.SignedOut.signedIn)
    }
}
