package com.willclay.forgeide.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.URI
import java.nio.file.Path

/**
 * Keeps paths as portable JSON strings (`src/Main.java`) instead of file URIs.
 *
 * kotlinx.serialization only knows Kotlin's own types, so a [Path] property
 * needs to be told about this serializer. Put this line at the very top of a
 * file whose classes hold paths, above `package`:
 *
 * ```
 * @file:UseSerializers(PathSerializer::class)
 * ```
 */
object PathSerializer : KSerializer<Path> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.nio.file.Path", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.toString().replace('\\', '/'))
    }

    override fun deserialize(decoder: Decoder): Path {
        val value = decoder.decodeString()

        return try {
            // Read the file-URI representation emitted before Forge installed
            // its explicit portable-path codec.
            if (value.startsWith("file:")) Path.of(URI.create(value)) else Path.of(value)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("Invalid filesystem path: $value", e)
        }
    }
}
