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

/** Group shell with its own tab bar. [tab] is one of [GroupTab] names. */
@Serializable data class GroupRoute(val groupId: String, val tab: String = GroupTab.Calendar.name)

enum class GroupTab { Calendar, Chat, Proposals, Timing }

@Serializable data object NewGroupRoute
@Serializable data class MembersRoute(val groupId: String)
@Serializable data class ProposalRoute(val proposalId: String)

/** Propose a slot: ISO [start] / [end]. */
@Serializable data class CreateProposalRoute(val groupId: String, val start: String, val end: String, val allDay: Boolean)

@Serializable data class EventsRoute(val groupId: String)

/** Add an event to a group, optionally prefilled with ISO [start] / [end]. */
@Serializable data class EventEditorRoute(val groupId: String, val start: String? = null, val end: String? = null)

@Serializable data class InviteRoute(val token: String)
@Serializable data object AppearanceRoute
@Serializable data object CalendarsRoute
@Serializable data object RegionRoute
