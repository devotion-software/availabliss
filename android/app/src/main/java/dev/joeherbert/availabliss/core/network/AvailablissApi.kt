package dev.joeherbert.availabliss.core.network

import dev.joeherbert.availabliss.core.network.model.Availability
import dev.joeherbert.availabliss.core.network.model.LinkAccountRequest
import dev.joeherbert.availabliss.core.network.model.LinkAccountResponse
import dev.joeherbert.availabliss.core.network.model.LinkedAccounts
import dev.joeherbert.availabliss.core.network.model.Plan
import dev.joeherbert.availabliss.core.network.model.PlannerRequest
import dev.joeherbert.availabliss.core.network.model.PlannerResult
import dev.joeherbert.availabliss.core.network.model.CreateTemplateRequest
import dev.joeherbert.availabliss.core.network.model.RenameTemplateRequest
import dev.joeherbert.availabliss.core.network.model.RequirementTemplate
import dev.joeherbert.availabliss.core.network.model.TemplatesResponse
import dev.joeherbert.availabliss.core.network.model.BlocksResponse
import dev.joeherbert.availabliss.core.network.model.BusyResponse
import dev.joeherbert.availabliss.core.network.model.CalDavRequest
import dev.joeherbert.availabliss.core.network.model.CalendarBusyRequest
import dev.joeherbert.availabliss.core.network.model.CalendarConnection
import dev.joeherbert.availabliss.core.network.model.ConnectionsResponse
import dev.joeherbert.availabliss.core.network.model.CountriesResponse
import dev.joeherbert.availabliss.core.network.model.CreateBlockRequest
import dev.joeherbert.availabliss.core.network.model.CreateEventRequest
import dev.joeherbert.availabliss.core.network.model.CreateGroupRequest
import dev.joeherbert.availabliss.core.network.model.CreateInviteRequest
import dev.joeherbert.availabliss.core.network.model.ConflictsResponse
import dev.joeherbert.availabliss.core.network.model.CreateProposalRequest
import dev.joeherbert.availabliss.core.network.model.DayAvailability
import dev.joeherbert.availabliss.core.network.model.DetectedRegion
import dev.joeherbert.availabliss.core.network.model.DeviceSync
import dev.joeherbert.availabliss.core.network.model.EventsResponse
import dev.joeherbert.availabliss.core.network.model.Group
import dev.joeherbert.availabliss.core.network.model.GroupEvent
import dev.joeherbert.availabliss.core.network.model.GroupsResponse
import dev.joeherbert.availabliss.core.network.model.HolidaysResponse
import dev.joeherbert.availabliss.core.network.model.InviteLink
import dev.joeherbert.availabliss.core.network.model.InvitePreview
import dev.joeherbert.availabliss.core.network.model.ManualBlock
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.MemberRoleRequest
import dev.joeherbert.availabliss.core.network.model.Message
import dev.joeherbert.availabliss.core.network.model.MessageInfo
import dev.joeherbert.availabliss.core.network.model.MessagesResponse
import dev.joeherbert.availabliss.core.network.model.NotificationsResponse
import dev.joeherbert.availabliss.core.network.model.NudgeResponse
import dev.joeherbert.availabliss.core.network.model.OAuthCompleteRequest
import dev.joeherbert.availabliss.core.network.model.OAuthUrlRequest
import dev.joeherbert.availabliss.core.network.model.OAuthUrlResponse
import dev.joeherbert.availabliss.core.network.model.RequirementPreview
import dev.joeherbert.availabliss.core.network.model.RequirementPreviewRequest
import dev.joeherbert.availabliss.core.network.model.OverrideRequest
import dev.joeherbert.availabliss.core.network.model.Poll
import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.core.network.model.ProposalsResponse
import dev.joeherbert.availabliss.core.network.model.ReadNotificationsRequest
import dev.joeherbert.availabliss.core.network.model.ReceiptsRequest
import dev.joeherbert.availabliss.core.network.model.RegisterDeviceRequest
import dev.joeherbert.availabliss.core.network.model.RespondRequest
import dev.joeherbert.availabliss.core.network.model.SchoolAuthoritiesResponse
import dev.joeherbert.availabliss.core.network.model.SendPollRequest
import dev.joeherbert.availabliss.core.network.model.SendTextRequest
import dev.joeherbert.availabliss.core.network.model.SubdivisionsResponse
import dev.joeherbert.availabliss.core.network.model.UpdateGroupRequest
import dev.joeherbert.availabliss.core.network.model.User
import dev.joeherbert.availabliss.core.network.model.VoteRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The `/api/v1` JSON API, one method per row of docs/API.md "Endpoints".
 * Paths are relative to the Retrofit base URL (`<host>/`).
 *
 * Partial-update bodies (`PATCH /me`, `PATCH /blocks/:id`, `PATCH /events/:id`) are
 * [JsonObject]s so callers can distinguish "leave unchanged" (absent) from "clear" (null);
 * see [JsonPatch].
 */
interface AvailablissApi {

    // region Me
    @GET("api/v1/me")
    suspend fun getMe(): User

    @PATCH("api/v1/me")
    suspend fun updateMe(@Body patch: JsonObject): User

    @GET("api/v1/me/busy")
    suspend fun getMyBusy(@Query("from") from: String, @Query("to") to: String): BusyResponse

    @DELETE("api/v1/me")
    suspend fun deleteAccount()

    @GET("api/v1/me/upcoming")
    suspend fun getUpcoming(): EventsResponse
    // endregion

    // region Regions & holidays
    @GET("api/v1/regions/countries")
    suspend fun getCountries(): CountriesResponse

    @GET("api/v1/regions/subdivisions")
    suspend fun getSubdivisions(@Query("country") country: String): SubdivisionsResponse

    @GET("api/v1/regions/school-authorities")
    suspend fun getSchoolAuthorities(@Query("country") country: String): SchoolAuthoritiesResponse

    @GET("api/v1/regions/detect")
    suspend fun detectRegion(): DetectedRegion

    @GET("api/v1/holidays")
    suspend fun getHolidays(
        @Query("country") country: String,
        @Query("subdivision") subdivision: String?,
        @Query("from") from: String,
        @Query("to") to: String,
    ): HolidaysResponse
    // endregion

    // region Calendars
    @GET("api/v1/calendars")
    suspend fun getConnections(): ConnectionsResponse

    @POST("api/v1/calendars/caldav")
    suspend fun connectCalDav(@Body body: CalDavRequest): CalendarConnection

    /** Provider consent URL with signed state; open it in a Custom Tab (it can't carry the bearer token). */
    @POST("api/v1/calendars/oauth/{provider}/url")
    suspend fun calendarOAuthUrl(@Path("provider") provider: String, @Body body: OAuthUrlRequest): OAuthUrlResponse

    /** Finishes a connection with the one-time code from `availabliss://calendars?complete=`. */
    @POST("api/v1/calendars/oauth/{provider}/complete")
    suspend fun completeCalendarOAuth(@Path("provider") provider: String, @Body body: OAuthCompleteRequest): CalendarConnection

    @PUT("api/v1/calendars/device")
    suspend fun syncDevice(@Body body: DeviceSync): CalendarConnection

    @PATCH("api/v1/calendars/{connectionId}/calendars/{calendarId}")
    suspend fun setCalendarBusy(
        @Path("connectionId") connectionId: String,
        @Path("calendarId") calendarId: String,
        @Body body: CalendarBusyRequest,
    ): CalendarConnection

    @POST("api/v1/calendars/{connectionId}/sync")
    suspend fun syncConnection(@Path("connectionId") connectionId: String): CalendarConnection

    @DELETE("api/v1/calendars/{connectionId}")
    suspend fun deleteConnection(@Path("connectionId") connectionId: String)
    // endregion

    // region Manual blocks
    @GET("api/v1/blocks")
    suspend fun getBlocks(): BlocksResponse

    @POST("api/v1/blocks")
    suspend fun createBlock(@Body body: CreateBlockRequest): ManualBlock

    @PATCH("api/v1/blocks/{id}")
    suspend fun updateBlock(@Path("id") id: String, @Body patch: JsonObject): ManualBlock

    @DELETE("api/v1/blocks/{id}")
    suspend fun deleteBlock(@Path("id") id: String)
    // endregion

    // region Groups
    @GET("api/v1/groups")
    suspend fun getGroups(): GroupsResponse

    @POST("api/v1/groups")
    suspend fun createGroup(@Body body: CreateGroupRequest): Group

    @GET("api/v1/groups/{id}")
    suspend fun getGroup(@Path("id") id: String): Group

    @PATCH("api/v1/groups/{id}")
    suspend fun updateGroup(@Path("id") id: String, @Body body: UpdateGroupRequest): Group

    @DELETE("api/v1/groups/{id}")
    suspend fun deleteGroup(@Path("id") id: String)

    @PATCH("api/v1/groups/{id}/members/{userId}")
    suspend fun setMemberRole(
        @Path("id") groupId: String,
        @Path("userId") userId: String,
        @Body body: MemberRoleRequest,
    ): Member

    /** Removes a member, or leaves the group when [userId] is the caller. */
    @DELETE("api/v1/groups/{id}/members/{userId}")
    suspend fun removeMember(@Path("id") groupId: String, @Path("userId") userId: String)

    @POST("api/v1/groups/{id}/nudge")
    suspend fun nudge(@Path("id") groupId: String): NudgeResponse

    @POST("api/v1/groups/{id}/invites")
    suspend fun createInvite(@Path("id") groupId: String, @Body body: CreateInviteRequest): InviteLink

    @GET("api/v1/invites/{token}")
    suspend fun getInvite(@Path("token") token: String): InvitePreview

    @POST("api/v1/invites/{token}/accept")
    suspend fun acceptInvite(@Path("token") token: String): Group
    // endregion

    // region Availability
    @POST("api/v1/requirements/preview")
    suspend fun previewRequirement(@Body body: RequirementPreviewRequest): RequirementPreview

    @GET("api/v1/groups/{id}/availability")
    suspend fun getAvailability(
        @Path("id") groupId: String,
        @Query("from") from: String,
        @Query("to") to: String,
    ): Availability

    @GET("api/v1/groups/{id}/availability/day")
    suspend fun getDayAvailability(@Path("id") groupId: String, @Query("date") date: String): DayAvailability
    // endregion

    // region Proposals
    @GET("api/v1/groups/{id}/proposals")
    suspend fun getProposals(@Path("id") groupId: String, @Query("status") status: String? = null): ProposalsResponse

    /** The optional AI planner (Pro). Slow: NetworkModule gives this call a longer read timeout. */
    @POST("api/v1/groups/{id}/planner")
    suspend fun planWithAi(@Path("id") groupId: String, @Body body: PlannerRequest): PlannerResult

    // region Plan (Pro)
    @GET("api/v1/me/plan")
    suspend fun getPlan(): Plan

    /** Re-reads RevenueCat right after a purchase or restore (the webhook may lag). */
    @POST("api/v1/me/plan/refresh")
    suspend fun refreshPlan(): Plan
    // endregion

    // region Linked sign-ins (Apple / Google)
    @GET("api/v1/me/accounts")
    suspend fun getLinkedAccounts(): LinkedAccounts

    /**
     * Raw [Response] because a 409 carries the other account (`account`) that the
     * normal error parsing would drop; see AccountsRepository.
     */
    @POST("api/v1/me/accounts/link")
    suspend fun linkAccount(@Body body: LinkAccountRequest): Response<LinkAccountResponse>
    // endregion

    // region Timing templates
    @GET("api/v1/me/templates")
    suspend fun getTemplates(): TemplatesResponse

    @POST("api/v1/me/templates")
    suspend fun createTemplate(@Body body: CreateTemplateRequest): RequirementTemplate

    @PATCH("api/v1/me/templates/{id}")
    suspend fun renameTemplate(@Path("id") id: String, @Body body: RenameTemplateRequest): RequirementTemplate

    @DELETE("api/v1/me/templates/{id}")
    suspend fun deleteTemplate(@Path("id") id: String)
    // endregion

    @POST("api/v1/groups/{id}/proposals")
    suspend fun createProposal(@Path("id") groupId: String, @Body body: CreateProposalRequest): Proposal

    @GET("api/v1/groups/{id}/conflicts")
    suspend fun getConflicts(@Path("id") groupId: String, @Query("start") start: String, @Query("end") end: String): ConflictsResponse

    @GET("api/v1/proposals/{id}")
    suspend fun getProposal(@Path("id") id: String): Proposal

    @POST("api/v1/proposals/{id}/respond")
    suspend fun respond(@Path("id") id: String, @Body body: RespondRequest): Proposal

    @POST("api/v1/proposals/{id}/override")
    suspend fun overrideResponse(@Path("id") id: String, @Body body: OverrideRequest): Proposal

    @DELETE("api/v1/proposals/{id}")
    suspend fun cancelProposal(@Path("id") id: String)
    // endregion

    // region Events
    @GET("api/v1/groups/{id}/events")
    suspend fun getEvents(
        @Path("id") groupId: String,
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): EventsResponse

    @POST("api/v1/groups/{id}/events")
    suspend fun createEvent(@Path("id") groupId: String, @Body body: CreateEventRequest): GroupEvent

    @GET("api/v1/events/{id}")
    suspend fun getEvent(@Path("id") id: String): GroupEvent

    @PATCH("api/v1/events/{id}")
    suspend fun updateEvent(@Path("id") id: String, @Body patch: JsonObject): GroupEvent

    @DELETE("api/v1/events/{id}")
    suspend fun deleteEvent(@Path("id") id: String)
    // endregion

    // region Chat
    @GET("api/v1/groups/{id}/messages")
    suspend fun getMessages(
        @Path("id") groupId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int = 50,
    ): MessagesResponse

    @POST("api/v1/groups/{id}/messages")
    suspend fun sendText(@Path("id") groupId: String, @Body body: SendTextRequest): Message

    @POST("api/v1/groups/{id}/messages")
    suspend fun sendPoll(@Path("id") groupId: String, @Body body: SendPollRequest): Message

    @POST("api/v1/groups/{id}/messages/receipts")
    suspend fun sendReceipts(@Path("id") groupId: String, @Body body: ReceiptsRequest)

    @GET("api/v1/messages/{id}/info")
    suspend fun getMessageInfo(@Path("id") messageId: String): MessageInfo

    @POST("api/v1/polls/{id}/vote")
    suspend fun vote(@Path("id") pollId: String, @Body body: VoteRequest): Poll

    @POST("api/v1/polls/{id}/close")
    suspend fun closePoll(@Path("id") pollId: String): Poll
    // endregion

    // region Notifications & devices
    @GET("api/v1/notifications")
    suspend fun getNotifications(): NotificationsResponse

    @POST("api/v1/notifications/read")
    suspend fun markNotificationsRead(@Body body: ReadNotificationsRequest)

    @POST("api/v1/devices")
    suspend fun registerDevice(@Body body: RegisterDeviceRequest)

    @DELETE("api/v1/devices/{token}")
    suspend fun unregisterDevice(@Path("token") token: String)
    // endregion

    companion object {
        const val LIVE_PATH = "api/v1/live"
    }
}
