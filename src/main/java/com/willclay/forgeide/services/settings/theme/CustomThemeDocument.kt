package com.willclay.forgeide.services.settings.theme

import com.willclay.forgeide.application.IDESettingsConfiguration
import com.willclay.forgeide.json.VersionedJsonDocument
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * The JSON file a user writes to make their own theme, kept in
 * `~/.forge/themes/<name>.json`:
 *
 * ```json
 * {
 *     "schemaVersion": 1,
 *     "name": "Copper Night",
 *     "base": "tokyo-night",
 *     "swing": {
 *         "@accentColor": "#D99A70",
 *         "TextPane.background": "#16161E"
 *     },
 *     "tokens": {
 *         "keyword": { "color": "#D99A70", "bold": true },
 *         "string": { "color": "#9ECE6A" }
 *     }
 * }
 * ```
 *
 * Every property is optional, and anything left out comes from the `base`
 * theme, so a document only has to say what it changes.
 *
 * - `base` is the id of a built-in theme ([AppTheme]).
 * - `swing` holds FlatLaf UI defaults, written exactly as in a FlatLaf
 *   `.properties` file. Values may be JSON numbers or booleans as well as
 *   strings, since `"Component.arc": 8` is what people naturally type.
 * - `tokens` restyles token types by name (`keyword`, `method-call`, ...).
 *
 * This class is only the file's *shape*. Checking the values and turning them
 * into a [Theme] is [CustomTheme.fromDocument]'s job.
 *
 * Unlike Forge's other documents, an unknown property is an error here rather
 * than being skipped: a person wrote this file, and a misspelt `"token"`
 * section that silently did nothing would be much harder to track down.
 */
@JvmRecord
@Serializable
data class CustomThemeDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,

    val name: String                      = "",
    val base: String                      = IDESettingsConfiguration.DEFAULT_THEME,
    val swing: Map<String, JsonPrimitive> = emptyMap(),
    val tokens: Map<String, TokenStyle>   = emptyMap()
) : VersionedJsonDocument {
    init {
        VersionedJsonDocument.requireSupportedVersion("custom theme", schemaVersion, CURRENT_SCHEMA_VERSION)
    }

    /** The `swing` values as FlatLaf reads them: `8` and `"8"` both become `8`. */
    fun swingProperties(): Map<String, String> = swing.mapValues { (_, value) -> value.content }

    /**
     * One token type's style. Either half may be left out (null), in which
     * case the base theme's value is kept. `"colour"` is read as well as
     * `"color"`, whichever the author spells.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @JvmRecord
    @Serializable
    data class TokenStyle(
        @JsonNames("colour") val color: String? = null,
                             val bold: Boolean? = null
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        private val FORMAT = Json {
            prettyPrint    = true
            encodeDefaults = true
        }

        private val OFFSET = Regex("""\s*at offset (\d+)""")

        /** Builds a document from plain Java values. */
        @JvmStatic
        fun create(
            name: String,
            base: String,
            swing: Map<String, String>,
            tokens: Map<String, TokenStyle>
        ) = CustomThemeDocument(CURRENT_SCHEMA_VERSION, name, base, swing.mapValues { JsonPrimitive(it.value) }, tokens)

        /**
         * Reads a document, describing any mistake in plain terms with the line
         * and column it is on.
         *
         * @throws IOException if the file cannot be read or is not a valid theme document
         */
        @JvmStatic
        @Throws(IOException::class)
        fun read(file: Path): CustomThemeDocument {
            val text = Files.readString(file)

            try {
                return FORMAT.decodeFromString(serializer(), text)
            }
            catch (e: IllegalArgumentException) {
                // A SerializationException is an IllegalArgumentException, and so
                // is the schema version check in init.
                throw IOException(describe(e, text), e)
            }
        }

        /**
         * Writes a new document, refusing to replace a file that already exists.
         *
         * @throws java.nio.file.FileAlreadyExistsException if `file` exists
         */
        @JvmStatic
        @Throws(IOException::class)
        fun write(file: Path, document: CustomThemeDocument) {
            Files.writeString(file, FORMAT.encodeToString(serializer(), document) + "\n", StandardOpenOption.CREATE_NEW)
        }

        /**
         * kotlinx reports a mistake as several lines that end with a dump of the
         * input, and points at it with a character offset. Only the first line
         * is useful to a person, and a line and column are easier to find in
         * an editor than an offset.
         */
        private fun describe(e: IllegalArgumentException, text: String): String {
            val firstLine = (e.message ?: "The file is not a valid theme document.").lineSequence().first()
            val offset    = OFFSET.find(firstLine) ?: return firstLine

            val index  = Math.clamp(offset.groupValues[1].toLong(), 0, text.length)
            val before = text.substring(0, index)
            val line   = before.count { it == '\n' } + 1
            val column = index - before.lastIndexOf('\n')

            return "line $line, column $column: " + firstLine.replace(OFFSET, "").removePrefix("Unexpected JSON token: ")
        }
    }
}
