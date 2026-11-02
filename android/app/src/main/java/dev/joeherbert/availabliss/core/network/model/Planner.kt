package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.Serializable

/** `POST /groups/:id/planner` body. A null note is left out. */
@Serializable
data class PlannerRequest(val notes: String? = null)

/** The optional AI planner's suggestion (Pro). Slots are always ones that already work for everyone. */
@Serializable
data class PlannerResult(
    val best: PlannerPick,
    val alternatives: List<PlannerPick> = emptyList(),
    val ideas: List<PlannerIdea> = emptyList(),
    val checklist: List<String> = emptyList(),
    /** Prefill for "Propose this". */
    val proposalTitle: String = "",
    val proposalNote: String = "",
)

@Serializable
data class PlannerPick(val slot: Slot, val why: String)

@Serializable
data class PlannerIdea(val title: String, val description: String)
