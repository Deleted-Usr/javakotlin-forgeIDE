package com.willclay.forgeide.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Converts between kotlinx's [JsonElement] tree and plain Java values: strings,
 * numbers, booleans, lists and maps.
 *
 * Language plugins describe their settings with those plain values (see
 * `LanguageSettings.toJson()`), so they never depend on Forge's JSON library.
 */
object JsonValues {
    @JvmStatic
    fun toElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to toElement(item) })
        is Iterable<*> -> JsonArray(value.map(::toElement))
        else -> throw IllegalArgumentException("Not a JSON value: ${value.javaClass.name}")
    }

    /** Numbers come back as Integer, Long or Double, the same types Jackson chose. */
    @JvmStatic
    fun fromElement(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonObject -> element.mapValues { (_, item) -> fromElement(item) }
        is JsonArray -> element.map(::fromElement)
        is JsonPrimitive ->
            if (element.isString) {
                element.content
            }
            else {
                element.booleanOrNull ?: element.intOrNull ?: element.longOrNull ?: element.double
            }
    }
}

/**
 * Lets a `Map<String, Any?>` of plain values be a `@Serializable` property:
 *
 * ```
 * val settings: @Serializable(with = JsonValueMapSerializer::class) Map<String, Any?>
 * ```
 */
object JsonValueMapSerializer : KSerializer<Map<String, Any?>> {
    private val objectSerializer = JsonObject.serializer()

    override val descriptor: SerialDescriptor = SerialDescriptor("com.willclay.forgeide.json.JsonValueMap", objectSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        encoder.encodeSerializableValue(objectSerializer, JsonValues.toElement(value) as JsonObject)
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): Map<String, Any?> =
        JsonValues.fromElement(decoder.decodeSerializableValue(objectSerializer)) as Map<String, Any?>
}
