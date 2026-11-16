package dev.joeherbert.availabliss.core.network

import dev.joeherbert.availabliss.core.network.model.EventsResponse
import dev.joeherbert.availabliss.core.network.model.GroupsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Group photos and event covers: set, null, or absent (older servers). */
class ImageFieldsTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun `group photo decodes when set, null or absent`() {
        val group = """"name":"Crew","color":"t2","monogram":"CR","role":"admin","memberCount":3,"syncedCount":1,
            "requirementSummary":"A weekend","status":{"kind":"searching"}"""
        val body = """{"groups":[
            {"id":"a",$group,"image":"https://cdn.example/a.jpg"},
            {"id":"b",$group,"image":null},
            {"id":"c",$group}]}"""
        val images = json.decodeFromString(GroupsResponse.serializer(), body).groups.map { it.image }
        assertEquals(listOf("https://cdn.example/a.jpg", null, null), images)
    }

    @Test
    fun `event cover decodes when set or absent`() {
        val event = """"groupId":"g","title":"Dinner","start":"2026-11-20T18:00:00Z","end":"2026-11-20T21:00:00Z",
            "createdBy":"u","createdAt":"2026-11-01T10:00:00Z""""
        val body = """{"events":[{"id":"1",$event,"image":"https://cdn.example/1.jpg"},{"id":"2",$event}]}"""
        val events = json.decodeFromString(EventsResponse.serializer(), body).events
        assertEquals("https://cdn.example/1.jpg", events[0].image)
        assertNull(events[1].image)
    }
}
