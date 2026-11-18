package com.willclay.forgeide.lang.python

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.util.SystemFileChooser
import com.willclay.forgeide.lang.api.settings.LanguageSettings
import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage
import com.willclay.forgeide.ui.Utils
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Swing editor for [PythonSettings]: where the sources are, and which Python
 * runs them.
 */
internal class PythonSettingsPage(projectRoot: Path, initial: PythonSettings) : LanguageSettingsPage {
    private val projectRoot: Path = projectRoot.toAbsolutePath().normalize()

    private val sourcePath  = JTextField(20)
    private val interpreter = JTextField(20)
    private val useVirtualEnvironment: JCheckBox

    private val component: JComponent

    init {
        sourcePath.text = initial.sourcePath?.toString().orEmpty()
        sourcePath.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, PythonSettings.DEFAULT_SOURCE_FOLDER)

        interpreter.text = initial.interpreter
        interpreter.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Automatic")

        val paths = Utils.createSettingsSection("Project paths").apply {
            Utils.addSettingsFormRow(this, 0, "Sources:", browseRow(sourcePath) { chooseSources() })
            addHint(this, 1, "Leave blank to use src. Use . for a project that keeps its .py files at the top.")
        }

        val python = Utils.createSettingsSection("Interpreter").apply {
            Utils.addSettingsFormRow(this, 0, "Python:", browseRow(interpreter) { chooseInterpreter() })
            addHint(this, 1, "Leave blank to choose automatically: the virtual environment below, otherwise python on PATH.")
        }

        useVirtualEnvironment = Utils.addSettingsCheckBoxRow(
            python, 2, "Use the project's virtual environment (.venv or venv) when it has one", initial.useVirtualEnvironment
        )
        addHint(python, 3, "Create one by running python -m venv .venv in the project folder.")

        // A typed interpreter always wins, so the checkbox would do nothing.
        interpreter.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent)  = updateControls()
            override fun removeUpdate(event: DocumentEvent)  = updateControls()
            override fun changedUpdate(event: DocumentEvent) = Unit
        })
        updateControls()

        val page = Utils.createSettingsPage().apply {
            Utils.addSettingsSection(this, paths)
            Utils.addSettingsSection(this, python)
        }

        component = JScrollPane(page).apply {
            border = null
            verticalScrollBar.unitIncrement = 16
        }
    }

    override fun title(): String = "Python"

    override fun component(): JComponent = component

    override fun getValues(): LanguageSettings = PythonSettings(
        interpreter           = interpreter.text.trim(),
        sourcePath            = sourcePath.text.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) },
        useVirtualEnvironment = useVirtualEnvironment.isSelected
    )

    private fun updateControls() {
        useVirtualEnvironment.isEnabled = interpreter.text.isBlank()
    }

    // --- Choosers --- //

    private fun chooseSources() {
        val chosen      = choose("Select Source Directory", SystemFileChooser.DIRECTORIES_ONLY, sourcePath) ?: return
        sourcePath.text = displayPath(chosen)
    }

    /** Kept absolute: Python is installed somewhere on the machine, not inside the project. */
    private fun chooseInterpreter() {
        val chosen       = choose("Select Python Interpreter", SystemFileChooser.FILES_ONLY, interpreter) ?: return
        interpreter.text = chosen.toAbsolutePath().normalize().toString()
    }

    private fun choose(title: String, mode: Int, parent: JComponent): Path? {
        val chooser = SystemFileChooser().apply {
            dialogTitle       = title
            fileSelectionMode = mode
            currentDirectory  = projectRoot.toFile()
        }

        if (chooser.showOpenDialog(parent) != SystemFileChooser.APPROVE_OPTION) {
            return null
        }

        return chooser.selectedFile.toPath()
    }

    /**
     * Stores a path inside the project relative to it, so the project still
     * works when its folder is moved or opened on another computer.
     */
    private fun displayPath(selected: Path): String {
        val normalised = selected.toAbsolutePath().normalize()
        if (normalised == projectRoot) {
            return "."
        }

        return if (normalised.startsWith(projectRoot)) {
            projectRoot.relativize(normalised).toString()
        }
        else {
            normalised.toString()
        }
    }

    private companion object {
        fun browseRow(field: JTextField, onBrowse: () -> Unit): JPanel {
            val browse = JButton("Browse...").apply { addActionListener { onBrowse() } }

            return JPanel(BorderLayout(6, 0)).apply {
                add(field, BorderLayout.CENTER)
                add(browse, BorderLayout.EAST)
            }
        }

        /** A full-width note under a control, for a rule worth stating once rather than discovering. */
        fun addHint(panel: JPanel, row: Int, text: String) {
            val hint        = JLabel(text).apply { isEnabled = false }
            val constraints = Utils.createSettingsRowConstraints(row).apply { gridwidth = 2 }

            panel.add(hint, constraints)
        }
    }
}
