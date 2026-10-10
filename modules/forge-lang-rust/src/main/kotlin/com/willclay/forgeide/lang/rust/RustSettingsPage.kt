package com.willclay.forgeide.lang.rust

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

/**
 * Swing editor for [RustSettings].
 *
 * Three sections: where the project's files are, which `cargo` to run, and
 * which features to build with. Each control is labelled with the Cargo flag
 * it adds, so what you tick here is what you will see in the console.
 */
internal class RustSettingsPage(projectRoot: Path, initial: RustSettings) : LanguageSettingsPage {
    private val projectRoot: Path = projectRoot.toAbsolutePath().normalize()

    private val manifestPath = JTextField(20)
    private val sourcePath = JTextField(20)

    private val cargoCommand = JTextField(20)
    private val offline: JCheckBox

    private val features = JTextField(20)
    private val noDefaultFeatures: JCheckBox
    private val allFeatures: JCheckBox

    private val component: JComponent

    init {
        manifestPath.text = initial.manifestPath.toString()
        sourcePath.text = initial.sourcePath?.toString().orEmpty()
        sourcePath.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "src next to Cargo.toml")
        cargoCommand.text = initial.cargoCommand
        features.text = initial.features.joinToString(" ")

        val paths = Utils.createSettingsSection("Project paths").apply {
            Utils.addSettingsFormRow(this, 0, "Cargo.toml:", browseRow(manifestPath) { chooseManifest() })
            Utils.addSettingsFormRow(this, 1, "Sources:", browseRow(sourcePath) { chooseSources() })
            addHint(this, 2, "Cargo always builds the src folder next to Cargo.toml. Leave Sources blank to match it.")
        }

        val cargo = Utils.createSettingsSection("Cargo").apply {
            Utils.addSettingsFormRow(this, 0, "Cargo command:", browseRow(cargoCommand) { chooseCargo() })
            addHint(this, 1, "Leave as cargo to use the one on PATH. rustup installs it in ~/.cargo/bin.")
        }
        offline = Utils.addSettingsCheckBoxRow(
            cargo, 2, "Work offline (--offline): only use crates already downloaded", initial.offline
        )

        val featureSection = Utils.createSettingsSection("Features").apply {
            Utils.addSettingsFormRow(this, 0, "Features (--features):", features)
            addHint(this, 1, "Names from the [features] table in Cargo.toml, separated by spaces or commas.")
        }
        noDefaultFeatures = Utils.addSettingsCheckBoxRow(
            featureSection, 2, "Disable default features (--no-default-features)", initial.noDefaultFeatures
        )
        allFeatures = Utils.addSettingsCheckBoxRow(
            featureSection, 3, "Enable all features (--all-features)", initial.allFeatures
        )

        // --all-features overrides the other two, so they are switched off
        // rather than left looking as if they still did something.
        allFeatures.addActionListener { updateFeatureControls() }
        updateFeatureControls()

        val page = Utils.createSettingsPage().apply {
            Utils.addSettingsSection(this, paths)
            Utils.addSettingsSection(this, cargo)
            Utils.addSettingsSection(this, featureSection)
        }

        component = JScrollPane(page).apply {
            border = null
            verticalScrollBar.unitIncrement = 16
        }
    }

    override fun title(): String = "Rust"

    override fun component(): JComponent = component

    /** RustSettings checks the values themselves; this only turns text into them. */
    override fun getValues(): LanguageSettings {
        val manifest = manifestPath.text.trim()
        require(manifest.isNotEmpty()) { "The Cargo.toml path must not be blank." }

        return RustSettings(
            cargoCommand = cargoCommand.text.trim(),
            manifestPath = Path.of(manifest),
            sourcePath = sourcePath.text.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) },
            features = RustSettings.parseFeatures(features.text),
            noDefaultFeatures = noDefaultFeatures.isSelected,
            allFeatures = allFeatures.isSelected,
            offline = offline.isSelected
        )
    }

    private fun updateFeatureControls() {
        features.isEnabled = !allFeatures.isSelected
        noDefaultFeatures.isEnabled = !allFeatures.isSelected
    }

    // --- Choosers --- //

    private fun chooseManifest() {
        val chosen = choose("Select Cargo.toml", SystemFileChooser.FILES_ONLY, manifestPath) ?: return
        manifestPath.text = displayPath(chosen)
    }

    private fun chooseSources() {
        val chosen = choose("Select Source Directory", SystemFileChooser.DIRECTORIES_ONLY, sourcePath) ?: return
        sourcePath.text = displayPath(chosen)
    }

    /** Kept absolute: cargo is installed somewhere on the machine, not inside the project. */
    private fun chooseCargo() {
        val chosen = choose("Select Cargo Executable", SystemFileChooser.FILES_ONLY, cargoCommand) ?: return
        cargoCommand.text = chosen.toAbsolutePath().normalize().toString()
    }

    private fun choose(title: String, mode: Int, parent: JComponent): Path? {
        val chooser = SystemFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = mode
            currentDirectory = projectRoot.toFile()
        }

        if (chooser.showOpenDialog(parent) != SystemFileChooser.APPROVE_OPTION) return null

        return chooser.selectedFile.toPath()
    }

    /**
     * Stores a path inside the project relative to it, so the project still
     * builds when its folder is moved or opened on another computer.
     */
    private fun displayPath(selected: Path): String {
        val normalised = selected.toAbsolutePath().normalize()

        return if (normalised.startsWith(projectRoot)) projectRoot.relativize(normalised).toString() else normalised.toString()
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
            val hint = JLabel(text).apply { isEnabled = false }
            val constraints = Utils.createSettingsRowConstraints(row).apply { gridwidth = 2 }

            panel.add(hint, constraints)
        }
    }
}
