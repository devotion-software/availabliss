package dev.joeherbert.availabliss.navigation

import kotlinx.serialization.Serializable

/*
 * Type-safe Navigation Compose destinations. Route properties are exposed to each
 * destination's ViewModel through SavedStateHandle under the same names
 * (e.g. `savedStateHandle.get<String>("groupId")` or `savedStateHandle.toRoute<GroupRoute>()`).
 */

// Top-level tabs
@Serializable data object HomeRoute
@Serializable data object MyTimeRoute
@Serializable data object ActivityRoute
@Serializable data object YouRoute

/** A group's calendar: its home screen, with the header's chat and "More" menu. */
@Serializable data class GroupRoute(val groupId: String)

// A group's sections, pushed over its calendar (see MainNavHost.openGroupSection).
@Serializable data class GroupChatRoute(val groupId: String)
/** Proposals and events ("Plans"). */
@Serializable data class GroupPlansRoute(val groupId: String)
@Serializable data class GroupTimingRoute(val groupId: String)

@Serializable data object NewGroupRoute
@Serializable data class MembersRoute(val groupId: String)
@Serializable data class ProposalRoute(val proposalId: String)

/** Propose a time at ISO [start] / [end]; [title] / [note] prefill the form (e.g. from the AI planner). */
@Serializable data class CreateProposalRoute(
    val groupId: String,
    val start: String,
    val end: String,
    val allDay: Boolean,
    val title: String? = null,
    val note: String? = null,
)

@Serializable data class EventsRoute(val groupId: String)

/** Adds an event (optionally prefilled with ISO [start] / [end]), or opens an existing one by [eventId]. */
@Serializable data class EventEditorRoute(val groupId: String, val start: String? = null, val end: String? = null, val eventId: String? = null)

@Serializable data class InviteRoute(val token: String)
@Serializable data object AppearanceRoute
@Serializable data object CalendarsRoute
@Serializable data object RegionRoute
