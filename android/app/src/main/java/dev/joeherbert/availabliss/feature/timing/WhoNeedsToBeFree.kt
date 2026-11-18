package dev.joeherbert.availabliss.feature.timing

/**
 * "Who needs to be free" on the timing editor (API.md "Requirements"): everyone, or at
 * least N of the members who aren't optional, plus members marked optional, who never
 * stop a time from working. Pure, so the clamping is unit tested.
 */
object WhoNeedsToBeFree {
    /** How many of [memberIds] count towards the quorum: everyone not marked optional on [draft]. */
    fun requiredCount(draft: TimingDraft, memberIds: List<String>): Int =
        memberIds.count { it !in draft.optionalMemberIds }

    /**
     * Switches between "Everyone" ([everyone] true, no quorum) and "At least N of M",
     * which starts at one fewer than everyone (at least 1).
     */
    fun setEveryone(draft: TimingDraft, everyone: Boolean, memberIds: List<String>): TimingDraft {
        if (everyone) return draft.copy(quorum = null)
        if (draft.quorum != null) return draft
        val required = requiredCount(draft, memberIds)
        return draft.copy(quorum = (required - 1).coerceAtLeast(1))
    }

    /** Steps "At least N" by [delta], kept between 1 and the number of required members. */
    fun changeQuorum(draft: TimingDraft, delta: Int, memberIds: List<String>): TimingDraft {
        val quorum = draft.quorum ?: return draft
        val max = requiredCount(draft, memberIds).coerceAtLeast(1)
        return draft.copy(quorum = (quorum + delta).coerceIn(1, max))
    }

    /** Marks [userId] optional (or not), keeping any quorum within the members still required. */
    fun setOptional(draft: TimingDraft, userId: String, optional: Boolean, memberIds: List<String>): TimingDraft {
        val ids = if (optional) draft.optionalMemberIds + userId else draft.optionalMemberIds - userId
        val updated = draft.copy(optionalMemberIds = ids)
        val quorum = updated.quorum ?: return updated
        val max = requiredCount(updated, memberIds).coerceAtLeast(1)
        return updated.copy(quorum = quorum.coerceIn(1, max))
    }

    /** "At least 3 of 5". */
    fun quorumLabel(quorum: Int, required: Int): String = "At least $quorum of $required"
}
