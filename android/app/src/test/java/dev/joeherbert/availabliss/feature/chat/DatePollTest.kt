package dev.joeherbert.availabliss.feature.chat

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.NetworkModule
import dev.joeherbert.availabliss.core.network.model.DateRange
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageKind
import dev.joeherbert.availabliss.core.network.model.NewDatePoll
import dev.joeherbert.availabliss.core.network.model.Poll
import dev.joeherbert.availabliss.core.network.model.PollOption
import dev.joeherbert.availabliss.core.network.model.PollSlot
import dev.joeherbert.availabliss.core.network.model.Requirement
import dev.joeherbert.availabliss.core.network.model.RequirementLength
import dev.joeherbert.availabliss.core.network.model.Rhythm
import dev.joeherbert.availabliss.core.network.model.RhythmEvery
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.core.network.model.SendDatePollRequest
import dev.joeherbert.availabliss.core.network.model.UpdateGroupRequest
import dev.joeherbert.availabliss.core.network.model.isDatePoll
import dev.joeherbert.availabliss.feature.common.MemberDirectory
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Date polls in the chat (times, who's busy, "Propose this") and the wire shapes for them and rhythms. */
class DatePollTest {
    private val json = NetworkModule.provideJson()
    private val directory = MemberDirectory(
        listOf(
            Member("me", "Sam Taylor", role = Role.Member, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
            Member("ana", "Ana Lopez", role = Role.Owner, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
            Member("tom", "Tom Reed", role = Role.Member, synced = true, joinedAt = "2026-01-01T00:00:00.000Z"),
        ),
        meId = "me",
    )
    private val friday = PollSlot(DateTimes.toIso(LocalDateTime.of(2026, 11, 20, 18, 0)), DateTimes.toIso(LocalDateTime.of(2026, 11, 20, 22, 0)))
    private val saturday = PollSlot(DateTimes.toIso(LocalDateTime.of(2026, 11, 21, 0, 0)), DateTimes.toIso(LocalDateTime.of(2026, 11, 22, 0, 0)), allDay = true)

    private fun pollMessage(author: String) = Message(
        id = "m1",
        groupId = "g",
        authorId = author,
        kind = MessageKind.Poll,
        body = "Which of these works?",
        createdAt = "2026-11-16T12:00:00.000Z",
        poll = Poll(
            "p1",
            "Which of these works?",
            multi = true,
            options = listOf(
                PollOption("o1", "Fri 20 Nov, 18:00", listOf("me", "ana"), friday),
                PollOption("o2", "Sat 21 Nov", listOf("tom"), saturday),
            ),
        ),
    )

    private fun pollUi(author: String, isAdmin: Boolean = false, conflicts: Map<String, List<String>> = emptyMap()) =
        buildChatItems(
            listOf(pollMessage(author)),
            directory,
            memberCount = 3,
            proposals = emptyMap(),
            events = emptyMap(),
            today = LocalDate.of(2026, 11, 16),
            conflicts = conflicts,
            isAdmin = isAdmin,
        ).filterIsInstance<ChatItem.PollItem>().single().poll

    @Test
    fun optionsShowTheirTimeAndWhoIsBusy() {
        val poll = pollUi("ana", conflicts = mapOf(pollSlotKey(friday) to listOf("tom"), pollSlotKey(saturday) to emptyList()))
        assertTrue(poll.isDatePoll)
        assertEquals("Fri 20 Nov · 18:00 – 22:00", poll.options[0].label)
        assertEquals("Sat 21 Nov", poll.options[1].label)
        assertEquals("Tom is busy", poll.options[0].detail)
        assertTrue(poll.options[0].busy)
        assertEquals("Everyone's free", poll.options[1].detail)
        assertFalse(poll.options[1].busy)
        // The most votes leads.
        assertEquals(listOf(true, false), poll.options.map { it.leading })
        // Not loaded yet: nothing said.
        assertNull(pollUi("ana").options[0].detail)
    }

    @Test
    fun itsCreatorAndAdminsCanProposeTheWinner() {
        assertTrue(pollUi("me").canPropose)
        assertFalse(pollUi("ana").canPropose)
        assertTrue(pollUi("ana", isAdmin = true).canPropose)
        // A plain poll has nothing to propose.
        val plain = pollMessage("me").let { m -> m.copy(poll = m.poll!!.copy(options = m.poll!!.options.map { it.copy(slot = null) })) }
        val ui = buildChatItems(listOf(plain), directory, 3, emptyMap(), emptyMap(), today = LocalDate.of(2026, 11, 16))
            .filterIsInstance<ChatItem.PollItem>().single().poll
        assertFalse(ui.isDatePoll)
        assertFalse(ui.canPropose)
        assertEquals("Fri 20 Nov, 18:00", ui.options[0].label)
    }

    @Test
    fun busyLineNamesYouFirst() {
        val nameOf = { id: String -> mapOf("me" to "You", "ana" to "Ana", "tom" to "Tom", "sam" to "Sam").getValue(id) }
        assertEquals("Everyone's free", busyLine(emptyList(), nameOf))
        assertEquals("You're busy", busyLine(listOf("me"), nameOf))
        assertEquals("Ana is busy", busyLine(listOf("ana"), nameOf))
        assertEquals("You and Ana are busy", busyLine(listOf("ana", "me"), nameOf))
        assertEquals("3 people are busy", busyLine(listOf("ana", "tom", "sam"), nameOf))
    }

    @Test
    fun datePollWireShapes() {
        val decoded = json.decodeFromString(
            Poll.serializer(),
            """{"id":"p","question":"q","multi":true,"closed":false,"options":[{"id":"a","label":"Fri","voterIds":[],"slot":{"start":"2026-11-20T18:00:00.000Z","end":"2026-11-20T22:00:00.000Z","allDay":false}},{"id":"b","label":"x","voterIds":[],"slot":null}]}""",
        )
        assertTrue(decoded.isDatePoll)
        assertEquals("2026-11-20T18:00:00.000Z", decoded.options[0].slot?.start)
        assertNull(decoded.options[1].slot)

        val body = json.encodeToString(SendDatePollRequest.serializer(), SendDatePollRequest(NewDatePoll("Which?", listOf(PollSlot("a", "b"), PollSlot("c", "d", true)), multi = true)))
        assertEquals("""{"poll":{"question":"Which?","slots":[{"start":"a","end":"b"},{"start":"c","end":"d","allDay":true}],"multi":true}}""", body)
    }

    @Test
    fun rhythmDecodesWithDefaultsAndPatchesExplicitly() {
        val group = Group(
            id = "g", name = "N", monogram = "N", role = Role.Admin, memberCount = 1, syncedCount = 1, requirementSummary = "s",
            requirement = Requirement(
                RequirementLength.Duration(120), null, emptyList(), false, false, null, emptyList(),
                DateRange("2026-11-01", "2026-12-31"), emptyList(), "Europe/London",
            ),
            members = emptyList(), createdAt = "2026-01-01T00:00:00Z",
        )
        val encoded = json.encodeToJsonElement(Group.serializer(), group).jsonObject
        // Older servers leave it out.
        assertFalse("rhythm" in encoded)
        assertNull(json.decodeFromJsonElement(Group.serializer(), encoded).rhythm)
        val withRhythm = JsonObject(encoded + ("rhythm" to json.parseToJsonElement("""{"every":"two-weeks","prompt":true}""")))
        val rhythm = json.decodeFromJsonElement(Group.serializer(), withRhythm).rhythm
        assertEquals(RhythmEvery.TwoWeeks, rhythm?.frequency)
        assertTrue(rhythm!!.prompt)

        // Stopping it sends an explicit null; setting it sends both fields; other fields stay out.
        assertEquals("""{"rhythm":null}""", json.encodeToString(UpdateGroupRequest.serializer(), UpdateGroupRequest(rhythm = JsonNull)))
        assertEquals(
            """{"rhythm":{"every":"month","prompt":false}}""",
            json.encodeToString(UpdateGroupRequest.serializer(), UpdateGroupRequest(rhythm = Rhythm("month", prompt = false).toJson())),
        )
    }
}
