package com.willclay.forgeide.lang.python

import com.willclay.forgeide.lang.api.run.RunOptionsPage
import com.willclay.forgeide.ui.Utils
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton

/**
 * The Python section of the run configuration dialog: whether the entry point
 * runs as a script or as a module. See [PythonRunOptions].
 *
 * Laid out like the Rust section's Debug / Release choice, so the two read
 * the same way.
 */
internal class PythonRunOptionsPage : RunOptionsPage {
    private val script = JToggleButton("Script")
    private val module = JToggleButton("Module (-m)")

    private val component = JPanel(GridBagLayout())

    init {
        ButtonGroup().apply {
            add(script)
            add(module)
        }
        script.isSelected = true

        // Filled with the accent colour when chosen, like the dialog's "Before it runs" buttons.
        listOf(script, module).forEach {
            it.putClientProperty(
                "FlatLaf.style",
                "selectedBackground: \$Component.accentColor; selectedForeground: \$Button.default.foreground"
            )
        }

        val runAs = JPanel(FlowLayout(FlowLayout.LEADING, 6, 0)).apply {
            add(script)
            add(module)
        }

        Utils.addCompactSettingsFormRow(component, 0, "Run as:", runAs)
        addHint(1, "Module runs python -m game.main, so relative imports (from . import x) work.")
    }

    /** A grey note under a control, lined up with the control, as in the rest of the dialog. */
    private fun addHint(row: Int, text: String) {
        val hint = JLabel(text).apply {
            putClientProperty("FlatLaf.styleClass", "small")
            putClientProperty("FlatLaf.style", "foreground: \$Label.disabledForeground")
        }

        val constraints = Utils.createSettingsRowConstraints(row).apply {
            gridx  = 1
            insets = Insets(0, 6, 4, 4)
        }

        component.add(hint, constraints)
    }

    override fun title(): String = "Python"

    override fun runtimeOptionsLabel(): String = "Interpreter options"

    override fun runtimeOptionsHint(): String =
        "Added after python and before your script, such as -O or -X dev."

    override fun component(): JComponent = component

    override fun addChangeListener(listener: Runnable) {
        script.addActionListener { listener.run() }
        module.addActionListener { listener.run() }
    }

    override fun load(options: Map<String, Any>) {
        (if (PythonRunOptions.from(options).module) {
            module
        }
        else {
            script
        }).isSelected = true
    }

    override fun getValues(): Map<String, Any> = PythonRunOptions(module = module.isSelected).toJson()
}
