package dev.joeherbert.availabliss.core.common

/**
 * Usernames: a unique handle people can be invited by instead of their email. Stored
 * lowercase, typed with or without a leading "@". Mirrors web/src/lib/usernames.ts for
 * instant feedback; the server is the authority.
 */
object Usernames {
    const val MIN = 3
    const val MAX = 20

    private val ALLOWED = Regex("^[a-z0-9_.]+$")
    private val RESERVED = setOf(
        "admin", "administrator", "availabliss", "help", "me", "moderator",
        "null", "root", "support", "system", "undefined", "you",
    )

    /** What was typed, as stored: trimmed, no leading "@", lowercase. */
    fun normalize(raw: String): String = raw.trim().trimStart('@').lowercase()

    /** Why a (normalized) username can't be used, or null when it's fine. */
    fun problem(username: String): String? = when {
        username.length < MIN -> "At least $MIN characters"
        username.length > MAX -> "At most $MAX characters"
        !ALLOWED.matches(username) -> "Only letters, numbers, _ and ."
        username.startsWith('.') || username.endsWith('.') || username.contains("..") ->
            "A dot can't come first, last or twice in a row"
        username in RESERVED -> "That one's reserved"
        else -> null
    }
}

/** One thing typed into an "Email or username" field. */
sealed interface InviteInput {
    data class Email(val address: String) : InviteInput
    data class Username(val username: String) : InviteInput

    companion object {
        private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
        private val SEPARATORS = charArrayOf(',', ';', ' ', '\n', '\t')

        /** Splits typed or pasted text into entries (commas, semicolons, spaces or new lines between them). */
        fun tokens(text: String): List<String> = text.split(*SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }

        /** True when [text] ends with a separator, i.e. the entry before it is finished. */
        fun endsEntry(text: String): Boolean = text.isNotEmpty() && text.last() in SEPARATORS

        /**
         * An "@" after the first character with a dot in the part after it is an email
         * (lowercased); anything else is a username (normalized).
         */
        fun parse(token: String): InviteInput {
            val trimmed = token.trim()
            val at = trimmed.lastIndexOf('@')
            return if (at >= 1 && trimmed.substring(at + 1).contains('.')) Email(trimmed.lowercase())
            else Username(Usernames.normalize(trimmed))
        }

        fun isValidEmail(address: String): Boolean = EMAIL.matches(address)
    }
}
