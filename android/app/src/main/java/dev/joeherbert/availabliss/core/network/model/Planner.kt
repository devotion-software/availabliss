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

/** `POST /groups/:id/planner/draft` body: the time picked (ISO instants). A null note is left out. */
@Serializable
data class ProposalDraftRequest(val start: String, val end: String, val notes: String? = null)

/** An AI-drafted proposal title and message (Pro). */
@Serializable
data class ProposalDraft(val title: String = "", val note: String = "")
