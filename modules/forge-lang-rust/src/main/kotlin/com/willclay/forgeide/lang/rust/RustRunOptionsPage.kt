package com.willclay.forgeide.lang.rust

import com.formdev.flatlaf.FlatClientProperties
import com.willclay.forgeide.lang.api.run.RunOptionsPage
import com.willclay.forgeide.ui.Utils
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.ButtonGroup
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

/**
 * The Rust section of the run configuration dialog: which build profile, and
 * which toolchain. See [RustRunOptions].
 *
 * As on [RustSettingsPage], each control is labelled with the Cargo flag it
 * adds, so what you choose here is what you will see in the console.
 */
internal class RustRunOptionsPage : RunOptionsPage {
    private val debug = JToggleButton("Debug")
    private val release = JToggleButton("Release")

    /** Editable, so any toolchain rustup knows can be typed; the list is just the common ones. */
    private val toolchain = JComboBox(arrayOf("", "stable", "beta", "nightly"))

    private val component = JPanel(GridBagLayout())

    init {
        ButtonGroup().apply {
            add(debug)
            add(release)
        }
        debug.isSelected = true

        // Filled with the accent colour when chosen, like the dialog's "Before it runs" buttons.
        listOf(debug, release).forEach {
            it.putClientProperty(
                "FlatLaf.style",
                "selectedBackground: \$Component.accentColor; selectedForeground: \$Button.default.foreground"
            )
        }

        val profile = JPanel(FlowLayout(FlowLayout.LEADING, 6, 0)).apply {
            add(debug)
            add(release)
        }

        toolchain.isEditable = true
        toolchain.prototypeDisplayValue = "rustup's default    " // wide enough for the placeholder
        toolchain.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "rustup's default")

        Utils.addCompactSettingsFormRow(component, 0, "Profile:", profile)
        addHint(1, "Release adds --release: slower to build, much faster to run.")
        Utils.addCompactSettingsFormRow(component, 2, "Toolchain:", toolchain)
        addHint(3, "Runs cargo +nightly, for example. Needs Cargo installed through rustup.")
    }

    /** A grey note under a control, lined up with the control, as in the rest of the dialog. */
    private fun addHint(row: Int, text: String) {
        val hint = JLabel(text).apply {
            putClientProperty("FlatLaf.styleClass", "small")
            putClientProperty("FlatLaf.style", "foreground: \$Label.disabledForeground")
        }
        val constraints = Utils.createSettingsRowConstraints(row).apply {
            gridx = 1
            insets = Insets(0, 6, 4, 4)
        }

        component.add(hint, constraints)
    }

    override fun title(): String = "Rust"

    override fun runtimeOptionsLabel(): String = "Cargo options"

    override fun runtimeOptionsHint(): String =
        "Added to cargo run before your program's arguments, such as --verbose."

    override fun component(): JComponent = component

    override fun addChangeListener(listener: Runnable) {
        debug.addActionListener { listener.run() }
        release.addActionListener { listener.run() }

        // The combo box only reports a typed name once Enter is pressed, so the
        // text field inside it is watched too, for a preview that keeps up.
        toolchain.addActionListener { listener.run() }
        (toolchain.editor.editorComponent as? JTextComponent)?.document?.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = listener.run()
            override fun removeUpdate(event: DocumentEvent) = listener.run()
            override fun changedUpdate(event: DocumentEvent) = Unit
        })
    }

    override fun load(options: Map<String, Any>) {
        val values = RustRunOptions.from(options)

        (if (values.release) release else debug).isSelected = true
        toolchain.selectedItem = values.toolchain
    }

    override fun getValues(): Map<String, Any> {
        val name = (toolchain.editor.item ?: toolchain.selectedItem)?.toString().orEmpty().trim().removePrefix("+")

        require(name.none(Char::isWhitespace)) {
            "A toolchain name has no spaces, such as nightly or 1.80."
        }

        return RustRunOptions(release = release.isSelected, toolchain = name).toJson()
    }
}
