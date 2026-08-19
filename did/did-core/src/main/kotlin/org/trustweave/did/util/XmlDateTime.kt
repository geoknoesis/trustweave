package org.trustweave.did.util

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Formats this instant per DID Resolution 1.0 §3.1: a valid XML datetime, adjusted to UTC,
 * without sub-second decimal precision (e.g. `2020-12-20T19:17:47Z`).
 */
fun Instant.toXmlDateTime(): String = Instant.fromEpochSeconds(this.epochSeconds).toString()

/**
 * Serializes [Instant] in the DID Resolution 1.0 §3.1 datetime format.
 *
 * Apply to every timestamp that crosses a resolution wire boundary so conformance is enforced
 * once, at the serialization boundary, rather than at each call site.
 */
object XmlDateTimeSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("XmlDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toXmlDateTime())
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
