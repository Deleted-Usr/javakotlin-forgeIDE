package com.willclay.forgeide.ui.toolbar.runconfigurations;

import com.willclay.forgeide.workspace.runconfig.BeforeLaunch;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/// Chooses what happens before a run configuration starts: nothing, compile
/// the entry file, or build the whole project.
///
/// The choices are toggle buttons in a [ButtonGroup] rather than a drop-down,
/// so all three are visible at once, and a line underneath says in plain words
/// what the selected one does. They are alternatives, not a sequence, so they
/// sit side by side without arrows between them.
final class BeforeLaunchPicker extends JPanel
{
    /// Ordered from least work to most, which is also how they read left to right.
    private static final List<BeforeLaunch> ORDER = List.of(
            BeforeLaunch.NONE,
            BeforeLaunch.COMPILE_TARGET,
            BeforeLaunch.BUILD_TARGET
    );

    private final Map<BeforeLaunch, JToggleButton> buttons = new EnumMap<>(BeforeLaunch.class);
    private final JLabel explanation = new JLabel();

    BeforeLaunchPicker()
    {
        super(new BorderLayout(0, 6));

        // GridLayout gives every option the same width, like a segmented control.
        JPanel options = new JPanel(new GridLayout(1, 0, 6, 0));
        ButtonGroup group = new ButtonGroup();

        for (BeforeLaunch option : ORDER)
        {
            JToggleButton button = new JToggleButton(label(option));
            button.putClientProperty("FlatLaf.style", "arc: 8; margin: 5,12,5,12;"
                    + " selectedBackground: $Component.accentColor; selectedForeground: $Button.default.foreground");
            button.addActionListener(event -> explanation.setText(explain(option)));

            group.add(button);
            buttons.put(option, button);
            options.add(button);
        }

        explanation.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");

        // Keep the buttons at their natural width rather than stretching across the dialog.
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        line.add(options);

        add(line, BorderLayout.NORTH);
        add(explanation, BorderLayout.CENTER);

        select(BeforeLaunch.COMPILE_TARGET);
    }

    BeforeLaunch selected()
    {
        for (Map.Entry<BeforeLaunch, JToggleButton> entry : buttons.entrySet())
        {
            if (entry.getValue().isSelected()) return entry.getKey();
        }

        return BeforeLaunch.COMPILE_TARGET;
    }

    void select(BeforeLaunch option)
    {
        buttons.get(option).setSelected(true);
        explanation.setText(explain(option));
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);
        buttons.values().forEach(button -> button.setEnabled(enabled));
    }

    private static String label(BeforeLaunch option)
    {
        return switch (option)
        {
            case NONE           -> "Nothing";
            case COMPILE_TARGET -> "Compile file";
            case BUILD_TARGET   -> "Build project";
        };
    }

    private static String explain(BeforeLaunch option)
    {
        return switch (option)
        {
            case NONE           -> "Runs straight away, using whatever was compiled last.";
            case COMPILE_TARGET -> "Compiles only the entry file first. Quick, and enough for most programs.";
            case BUILD_TARGET   -> "Builds the whole project first, for entry files that need other sources compiled too.";
        };
    }
}
