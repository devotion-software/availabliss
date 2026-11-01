package dev.joeherbert.availabliss.core.network

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A field in a partial update: [Keep] leaves it unchanged (omitted from the body),
 * [Set] writes a value, including `null` to clear it.
 */
sealed interface Patch<out T> {
    data object Keep : Patch<Nothing>
    data class Set<T>(val value: T) : Patch<T>
}

fun <T> patch(value: T): Patch<T> = Patch.Set(value)

/** Builds a PATCH body containing only the fields that are [Patch.Set]. */
fun jsonPatch(block: JsonPatchBuilder.() -> Unit): JsonObject =
    buildJsonObject { JsonPatchBuilder(this).block() }

class JsonPatchBuilder internal constructor(private val builder: JsonObjectBuilder) {
    /** Sets [key] to [value]; `null` clears the field on the server. */
    fun set(key: String, value: String?) {
        builder.put(key, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    fun set(key: String, value: Boolean) {
        builder.put(key, JsonPrimitive(value))
    }

    fun string(key: String, value: Patch<String?>) {
        if (value is Patch.Set) builder.put(key, value.value?.let(::JsonPrimitive) ?: JsonNull)
    }

    fun boolean(key: String, value: Patch<Boolean?>) {
        if (value is Patch.Set) builder.put(key, value.value?.let(::JsonPrimitive) ?: JsonNull)
    }
}
