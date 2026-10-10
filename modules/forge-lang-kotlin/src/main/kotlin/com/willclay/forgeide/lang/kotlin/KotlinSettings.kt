package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.workspace.Project
import com.willclay.forgeide.lang.api.settings.LanguageSettings
import com.willclay.forgeide.lang.jvm.JvmSettings
import java.util.Locale

/**
 * The complete set of Kotlin/JVM settings owned by the Kotlin language.
 */
data class KotlinSettings(
    val jvm: JvmSettings,
    val compilerCommand: String,
    val jvmTarget: String,
    val languageVersion: String,
    val progressiveMode: Boolean
) : LanguageSettings {

    init {
        require(compilerCommand.isNotBlank()) { "Kotlin compiler command must not be blank." }
        require(jvmTarget.isNotBlank())       { "Kotlin JVM target must not be blank." }
        require(languageVersion.isNotBlank()) { "Kotlin language version must not be blank." }
    }

    override fun languageId(): String = ID

    override fun toJson(): Map<String, Any> {
        return (jvm.toJson() + mapOf(
            "compilerCommand" to compilerCommand,
            "jvmTarget"       to jvmTarget,
            "languageVersion" to languageVersion,
            "progressiveMode" to progressiveMode
        ))
    }

    companion object {
        const val ID = "kotlin"

        @JvmStatic
        fun defaults(): KotlinSettings {
            val isWindows = System.getProperty("os.name")
                .lowercase(Locale.ROOT)
                .contains("windows")
            val compiler = if (isWindows) "kotlinc.bat" else "kotlinc"

            return KotlinSettings(
                jvm             = JvmSettings.defaults(),
                compilerCommand = compiler,
                jvmTarget       = Runtime.version().feature().toString(),
                languageVersion = "2.4",
                progressiveMode = false
            )
        }

        @JvmStatic
        fun from(project: Project): KotlinSettings {
            val json = project.configuration().languageSettings[ID] ?: return defaults()

            val defaults = defaults()
            return KotlinSettings(
                jvm             = JvmSettings.fromJson(json),
                compilerCommand = text(json["compilerCommand"], defaults.compilerCommand),
                jvmTarget       = text(json["jvmTarget"], defaults.jvmTarget),
                languageVersion = text(json["languageVersion"], defaults.languageVersion),
                progressiveMode = json["progressiveMode"] as? Boolean ?: false
            )
        }

        private fun text(value: Any?, fallback: String): String {
            return (value as? String)?.takeIf { it.isNotBlank() } ?: fallback
        }
    }
}