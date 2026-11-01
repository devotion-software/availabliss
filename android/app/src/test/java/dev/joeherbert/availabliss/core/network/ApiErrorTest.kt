package dev.joeherbert.availabliss.core.network

import dev.joeherbert.availabliss.core.network.model.GroupStatus
import dev.joeherbert.availabliss.core.network.model.GroupSummary
import dev.joeherbert.availabliss.core.network.model.Requirement
import dev.joeherbert.availabliss.core.network.model.RequirementLength
import dev.joeherbert.availabliss.core.network.model.DateRange
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun `parses validation error body with issues`() {
        val body = """{"message":"Invalid","code":"validation_error","issues":[{"path":"name","message":"Required"}]}"""
        val error = parseHttpError(json, 400, body)
        assertTrue(error is ApiError.Validation)
        assertEquals("Required", (error as ApiError.Validation).issueFor("name"))
    }

    @Test
    fun `falls back to status code when body is not json`() {
        assertTrue(parseHttpError(json, 401, "<html>") is ApiError.Unauthorized)
        assertTrue(parseHttpError(json, 503, null) is ApiError.Server)
        assertTrue(parseHttpError(json, 429, "") is ApiError.RateLimited)
    }

    @Test
    fun `group status decodes by kind with fallback`() {
        fun summary(status: String) = """{"id":"g","name":"G","color":"t2","monogram":"G","role":"admin","memberCount":3,"syncedCount":2,"requirementSummary":"x","status":$status,"unreadCount":0}"""
        assertEquals(GroupStatus.Matches(2), json.decodeFromString(GroupSummary.serializer(), summary("""{"kind":"matches","count":2}""")).status)
        assertEquals(GroupStatus.NeedsReply("p1"), json.decodeFromString(GroupSummary.serializer(), summary("""{"kind":"needs_reply","proposalId":"p1"}""")).status)
        assertEquals(GroupStatus.Searching, json.decodeFromString(GroupSummary.serializer(), summary("""{"kind":"something_new"}""")).status)
    }

    @Test
    fun `requirement encodes explicit nulls and span discriminator`() {
        val requirement = Requirement(
            length = RequirementLength.Span(4, "18:00", 6, "23:59", 0),
            window = null,
            days = emptyList(),
            bankHolidays = true,
            schoolHolidaysOnly = false,
            schoolAuthority = null,
            customSchoolHolidays = emptyList(),
            range = DateRange("2026-11-01", "2027-01-31"),
            dates = emptyList(),
            timezone = "Europe/London",
        )
        val encoded = json.encodeToString(Requirement.serializer(), requirement)
        assertTrue(encoded, encoded.contains("\"type\":\"span\""))
        assertTrue(encoded, encoded.contains("\"weeksLater\":0"))
        assertTrue(encoded, encoded.contains("\"window\":null"))
        assertTrue(encoded, encoded.contains("\"schoolAuthority\":null"))
        assertEquals(requirement, json.decodeFromString(Requirement.serializer(), encoded))
    }

    @Test
    fun `optional request fields are omitted`() {
        val encoded = Json(from = json) {}.encodeToString(
            dev.joeherbert.availabliss.core.network.model.CreateGroupRequest.serializer(),
            dev.joeherbert.availabliss.core.network.model.CreateGroupRequest(name = "Book club"),
        )
        assertEquals("""{"name":"Book club"}""", encoded)
    }
}
