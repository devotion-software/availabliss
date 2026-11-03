package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** An enum with a wire value, for [LenientEnumSerializer]. */
interface WireEnum {
    val wire: String
}

/**
 * Decodes a string enum, mapping values this build doesn't know to [fallback] instead
 * of failing the whole response (`coerceInputValues` only helps properties that have a
 * default). Lets the server add new kinds without breaking older app versions.
 */
open class LenientEnumSerializer<T>(
    serialName: String,
    private val values: Array<T>,
    private val fallback: T,
) : KSerializer<T> where T : Enum<T>, T : WireEnum {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.wire)

    override fun deserialize(decoder: Decoder): T {
        val wire = decoder.decodeString()
        return values.firstOrNull { it != fallback && it.wire == wire } ?: fallback
    }
}
