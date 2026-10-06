package com.willclay.forgeide.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializerOrNull
import java.io.IOException
import java.io.Reader
import java.io.Writer

/**
 * Reads and writes JSON with kotlinx.serialization.
 *
 * Unlike Jackson, nothing here inspects classes by reflection: the Kotlin
 * compiler plugin writes a serializer for every `@Serializable` class, and
 * this codec only looks that serializer up. A class that is not
 * `@Serializable` (for example a Java record) cannot be read here at all.
 */
class KotlinxJsonCodec : JsonCodec {
    override fun <T> read(reader: Reader, type: Class<T>): T {
        try {
            return FORMAT.decodeFromString(serializerFor(type), reader.readText())
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException, and so is a
            // failed require() in a document's init block; both mean a bad file.
            throw IOException("Could not decode JSON: ${e.message}", e)
        }
    }

    override fun write(writer: Writer, value: Any) {
        try {
            writer.write(FORMAT.encodeToString(serializerFor(value.javaClass), value))
        } catch (e: IllegalArgumentException) {
            throw IOException("Could not encode JSON: ${e.message}", e)
        }
    }

    private companion object {
        val FORMAT = Json {
            prettyPrint = true

            // Write every property, even ones still at their default value, so a
            // file on disk shows all the settings it holds, as Jackson's did.
            encodeDefaults = true

            // An explicit `null` for a property that cannot be null falls back to
            // that property's default, as the old record constructors did.
            coerceInputValues = true

            // Jackson skipped properties it did not recognise, so a file written by
            // a newer Forge still opens in an older one. Keep doing the same.
            ignoreUnknownKeys = true
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> serializerFor(type: Class<T>): KSerializer<T> =
            serializerOrNull(type) as KSerializer<T>?
                ?: throw IllegalStateException("${type.name} is not @Serializable, so KotlinxJsonCodec cannot handle it.")
    }
}
