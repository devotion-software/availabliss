package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.Serializable

/** One emoji on a message and who reacted with it, in the order they did. */
@Serializable
data class MessageReaction(val emoji: String, val userIds: List<String> = emptyList())

/** A message's reactions after one changed (`PUT`/`DELETE /messages/:id/reaction`, live `message.reactions`). */
@Serializable
data class MessageReactions(val messageId: String, val reactions: List<MessageReaction> = emptyList())

@Serializable
data class ReactionRequest(val emoji: String)

/** Anything but group updates and deleted messages can be reacted to. */
val Message.canReact: Boolean get() = kind != MessageKind.System && !deleted && authorId != null

/** Reactions: one per person per message, picked from the menu (or any emoji), ❤️ on a double tap. */
object Reactions {
    const val HEART = "❤️"

    /** The row at the top of a message's menu. */
    val quick = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")

    /** More to pick from, besides any emoji from the keyboard. */
    val more = listOf(
        "🔥", "🎉", "👏", "🙌", "💯", "✅", "👀", "🤔",
        "😍", "🥰", "😘", "😊", "😁", "🤣", "😅", "😎",
        "🥳", "🤩", "😴", "🙃", "😬", "😱", "😭", "😡",
        "👎", "👌", "🤞", "💪", "🫶", "🤝", "🍻", "🥂",
        "☕", "🍕", "🌮", "🎂", "⚽", "🏃", "✈️", "🏖️",
        "🌞", "🌧️", "❄️", "⭐", "💔", "💀", "🙈", "🤷",
    )

    /** Your reaction, or null. */
    fun mine(reactions: List<MessageReaction>, me: String?): String? =
        me?.let { id -> reactions.firstOrNull { id in it.userIds }?.emoji }

    /** What picking [emoji] does: reacts with it, or takes yours back if it's already yours. */
    fun toggled(reactions: List<MessageReaction>, me: String?, emoji: String): String? =
        if (mine(reactions, me) == emoji) null else emoji

    /** The reactions with your one reaction set to [emoji] (removed for null), as the server orders them. */
    fun setting(reactions: List<MessageReaction>, me: String, emoji: String?): List<MessageReaction> {
        val next = reactions.map { it.copy(userIds = it.userIds - me) }.filter { it.userIds.isNotEmpty() }.toMutableList()
        if (emoji == null) return next
        val index = next.indexOfFirst { it.emoji == emoji }
        if (index >= 0) next[index] = next[index].copy(userIds = next[index].userIds + me)
        else next += MessageReaction(emoji, listOf(me))
        return next
    }

    /** One emoji (a single character: flags, skin tones and joined sequences included), else null. */
    fun single(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val breaks = java.text.BreakIterator.getCharacterInstance()
        breaks.setText(t)
        breaks.first()
        if (breaks.next() != t.length) return null
        val first = t.codePointAt(0)
        val codePoints = t.codePoints().toArray()
        val emoji = when {
            // Pictographs, flags and the rest of the supplementary emoji planes.
            first >= 0x1F000 -> true
            // Arrows, symbols and dingbats (☕, ⭐, ✈️ …).
            first in 0x2190..0x2BFF -> true
            // Keycaps (1️⃣) and text symbols shown as emoji (©️).
            0x20E3 in codePoints -> true
            codePoints.size > 1 && 0xFE0F in codePoints -> true
            else -> false
        }
        return if (emoji) t else null
    }

    /** "You, Ana and Sam" / "Ana, Sam and 2 others" for who reacted with one emoji. */
    fun names(userIds: List<String>, me: String?, name: (String) -> String): String {
        val ordered = userIds.filter { it == me } + userIds.filter { it != me }
        val names = ordered.map { if (it == me) "You" else name(it) }
        return when (names.size) {
            0 -> ""
            1 -> names[0]
            2, 3 -> names.dropLast(1).joinToString(", ") + " and " + names.last()
            else -> "${names[0]}, ${names[1]} and ${names.size - 2} others"
        }
    }
}
