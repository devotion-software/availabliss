package dev.joeherbert.availabliss.core.common

/** "Sam Taylor" → "S" (single initial, as in the design's avatars). */
fun String.initial(): String = trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

/** First name for greetings and lists: "Sam Taylor" → "Sam". */
fun String.firstName(): String = trim().substringBefore(' ').ifEmpty { this }

/** "Ana, Priya, Tom and 3 others" */
fun List<String>.joinNamesSummary(max: Int = 3): String = when {
    isEmpty() -> ""
    size == 1 -> first()
    size <= max -> dropLast(1).joinToString(", ") + " and " + last()
    else -> take(max).joinToString(", ") + " and ${size - max} " + if (size - max == 1) "other" else "others"
}

/** "Leo and Jade", "Leo, Jade and Ana" */
fun List<String>.joinNames(): String = when (size) {
    0 -> ""
    1 -> first()
    else -> dropLast(1).joinToString(", ") + " and " + last()
}
