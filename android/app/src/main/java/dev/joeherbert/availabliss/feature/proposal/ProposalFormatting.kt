package dev.joeherbert.availabliss.feature.proposal

import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.Proposal
import dev.joeherbert.availabliss.core.network.model.ProposalStatus
import dev.joeherbert.availabliss.core.network.model.ResponseKind
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardTone
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardUi
import dev.joeherbert.availabliss.feature.common.MemberDirectory

/** Shared presentation rules for proposals (chat cards, list, detail). */
object ProposalFormatting {

    /** Firm yeses. */
    fun acceptedCount(proposal: Proposal): Int = proposal.responses.count { it.response == ResponseKind.Accepted }

    /** Maybes (soft yeses), counted apart from [acceptedCount]. */
    fun maybeCount(proposal: Proposal): Int = proposal.responses.count { it.response == ResponseKind.Maybe }

    /** "3 of 6 in" → "3 of 6 in · 1 maybe" when anyone said maybe. */
    fun withMaybes(text: String, maybe: Int): String = if (maybe > 0) "$text · $maybe maybe" else text

    fun totalCount(proposal: Proposal, memberCount: Int): Int = proposal.responses.size.takeIf { it > 0 } ?: memberCount

    /** "Fri 13 – Sun 15 Nov" */
    fun rangeLabel(proposal: Proposal): String = DateTimes.rangeLabel(proposal.start, proposal.end, proposal.allDay)

    /** "From Fri 18:00 · Keswick" */
    fun detailLine(proposal: Proposal): String {
        val start = DateTimes.localDateTime(proposal.start)
        val from = if (!proposal.allDay && start != null) {
            "From ${DateTimes.shortWeekday(DateTimes.apiWeekday(start.toLocalDate()))} ${DateTimes.time(start)}"
        } else {
            null
        }
        return listOfNotNull(from, proposal.location?.takeIf { it.isNotBlank() }).joinToString(" · ")
    }

    /** Members busy at an open proposal's time (none once it's settled). */
    fun busyIds(proposal: Proposal): List<String> =
        if (proposal.status == ProposalStatus.Open) proposal.busyMemberIds else emptyList()

    /** "Priya busy then" for compact rows, or null. */
    fun clashShort(proposal: Proposal, directory: MemberDirectory?): String? =
        Clash.short(busyIds(proposal)) { directory?.get(it)?.name }

    /** "Everyone's in", or "It's on · 3 of 4 coming" (" · 1 maybe") when confirmed without everyone. */
    fun confirmedStatus(proposal: Proposal): String =
        if (proposal.everyoneIn) "Everyone's in"
        else withMaybes("It's on · ${acceptedCount(proposal)} of ${proposal.responses.size} coming", maybeCount(proposal))

    fun card(proposal: Proposal, directory: MemberDirectory?, memberCount: Int, caption: String? = null): ProposalCardUi {
        val date = DateTimes.localDate(proposal.start)
        val accepted = acceptedCount(proposal)
        val total = totalCount(proposal, memberCount)
        val proposer = directory?.displayName(proposal.createdBy, youSuffix = false) ?: "Someone"
        val (status, tone) = when (proposal.status) {
            ProposalStatus.Confirmed -> confirmedStatus(proposal) to ProposalCardTone.Confirmed
            ProposalStatus.Cancelled -> "Cancelled" to ProposalCardTone.Muted
            ProposalStatus.Open -> withMaybes("$accepted of $total accepted", maybeCount(proposal)) to ProposalCardTone.Open
        }
        return ProposalCardUi(
            month = date?.let(DateTimes::monthAbbrev).orEmpty(),
            day = date?.dayOfMonth?.toString().orEmpty(),
            caption = caption ?: (proposal.title?.takeIf { it.isNotBlank() }?.let { "$it · $proposer proposed" } ?: "$proposer proposed a time"),
            title = rangeLabel(proposal),
            status = status,
            tone = tone,
            clash = clashShort(proposal, directory),
        )
    }
}
