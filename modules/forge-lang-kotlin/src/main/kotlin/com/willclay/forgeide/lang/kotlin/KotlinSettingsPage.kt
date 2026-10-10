package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.lang.api.settings.LanguageSettings
import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage
import com.willclay.forgeide.ui.Utils
import com.willclay.forgeide.ui.settings.project.JvmSettingsForm
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextField
import kotlin.io.path.name

/**
 * Swing editor for [KotlinSettings].
 */
internal class KotlinSettingsPage(projectRoot: Path, initial: KotlinSettings) : LanguageSettingsPage {

    private val jvm             = JvmSettingsForm(projectRoot, initial.jvm)
    private val compilerCommand = JTextField(20)

    private val jvmTarget       = JComboBox(arrayOf("1.8", "9", "11", "17", "21", "23", "24", "25"))
    private val languageVersion = JComboBox(arrayOf("1.9", "2.0", "2.1", "2.2", "2.3", "2.4"))

    private val progressiveMode: JCheckBox
    private val component: JComponent

    init {
        compilerCommand.text = initial.compilerCommand

        jvmTarget.isEditable   = true
        jvmTarget.selectedItem = initial.jvmTarget

        languageVersion.isEditable   = true
        languageVersion.selectedItem = initial.languageVersion

        val compiler = Utils.createSettingsSection("Kotlin compiler").apply {
            Utils.addSettingsFormRow(this, 0, "Compiler command:", compilerCommand)
            Utils.addCompactSettingsFormRow(this, 1, "JVM target:", jvmTarget)
            Utils.addCompactSettingsFormRow(this, 2, "Language version:", languageVersion)
        }

        progressiveMode = Utils.addSettingsCheckBoxRow(
            compiler, 3, "Enable progressive compiler mode", initial.progressiveMode
        )

        val page = Utils.createSettingsPage().apply {
            Utils.addSettingsSection(this, jvm.createPathsSection())
            Utils.addSettingsSection(this, jvm.createLibrariesSection())
            Utils.addSettingsSection(this, jvm.createJdkSection())
            Utils.addSettingsSection(this, compiler)
        }

        component = JScrollPane(page).apply {
            border = null
            verticalScrollBar.unitIncrement = 16
        }
    }

    override fun title(): String = "Kotlin"

    override fun component(): JComponent = component

    override fun getValues(): LanguageSettings {
        val values = jvm.values
        val java = values.jdkExecutable("java")

        require(Files.isRegularFile(java)) {
            "The selected JDK does not contain ${java.name}."
        }

        return KotlinSettings(
            jvm             = values,
            compilerCommand = compilerCommand.text,
            jvmTarget       = selectedText(jvmTarget),
            languageVersion = selectedText(languageVersion),
            progressiveMode = progressiveMode.isSelected
        )
    }

    companion object {
        private fun selectedText(comboBox: JComboBox<String>): String {
            return comboBox.editor.item?.toString().orEmpty()
        }
    }
}
