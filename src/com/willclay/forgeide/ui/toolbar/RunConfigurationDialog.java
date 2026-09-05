package com.willclay.forgeide.ui.toolbar;

import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.runconfig.BeforeLaunch;
import com.willclay.forgeide.workspace.runconfig.RunConfiguration;
import com.willclay.forgeide.workspace.runconfig.RunConfigurationManager;

import javax.swing.*;
import java.awt.*;
import java.util.function.Function;

/// The dialog [RunConfigDialogController] opens.
///
/// The left half picks a configuration, the right half edits the selected one,
/// and the bar along the bottom applies or abandons the edit — the same shape
/// the settings window uses. Only the layout lives here; nothing is loaded or
/// saved yet, so the controls that need [RunConfigurationManager] stay disabled.
public class RunConfigurationDialog extends JDialog
{
    private final JTextField name        = new JTextField(24);
    private final JTextField entryPoint  = new JTextField(24);
    private final JTextField vmOptions   = new JTextField(24);
    private final JTextField programArgs = new JTextField(24);
    private final JTextField workingDir  = new JTextField(24);
    private final JTextField environment = new JTextField(24);
    private final JComboBox<BeforeLaunch> beforeLaunchOptions = new JComboBox<>(BeforeLaunch.values());

    private final DefaultListModel<RunConfiguration> configs = new DefaultListModel<>();
    private final JList<RunConfiguration> configList         = new JList<>(configs);

    private final JButton addConfig = new JButton("+ Add");
    private final JButton remove    = new JButton("- Remove");
    private final JButton apply     = new JButton("Apply");
    private final JButton cancel    = new JButton("Cancel");

    public RunConfigurationDialog(Window parent, RunConfigurationManager manager)
    {
        super(parent, "Edit Run Configurations", ModalityType.MODELESS);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        setSize(760, 540);
        setMinimumSize(new Dimension(620, 440));

        JSplitPane listAndEditor = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, configurationList(), editor());
        listAndEditor.setResizeWeight(0); // the editor, not the list, takes new width
        listAndEditor.setBorder(BorderFactory.createEmptyBorder());

        add(listAndEditor, BorderLayout.CENTER);
        add(buttonBar(), BorderLayout.SOUTH);

        setLocationRelativeTo(parent);
    }

    private JPanel configurationList()
    {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Configurations"));

        // Height is the dialog's to decide; only the width is worth pinning.
        panel.setPreferredSize(new Dimension(200, 0));
        panel.setMinimumSize(new Dimension(160, 0));

        configList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configList.setCellRenderer(labelledBy(value -> ((RunConfiguration) value).name()));
        configList.addListSelectionListener(event ->
        {
            if (!event.getValueIsAdjusting()) remove.setEnabled(!configList.isSelectionEmpty());
        });

        addConfig.setEnabled(false); // waiting on the manager to create one
        remove.setEnabled(false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        buttons.add(addConfig);
        buttons.add(remove);

        panel.add(new JScrollPane(configList), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private JComponent editor()
    {
        JPanel sections = Utils.createSettingsPage();
        Utils.addSettingsSection(sections, createConfigurationSection());
        Utils.addSettingsSection(sections, createLaunchSection());

        // A page of sections has no use for spare height, so hold it at the top.
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(sections, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapper,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        return scrollPane;
    }

    private JPanel createConfigurationSection()
    {
        JPanel panel = Utils.createSettingsSection("Configuration");

        Utils.addSettingsFormRow(panel, 0, "Name:", name);
        Utils.addSettingsFormRow(panel, 1, "Entry point:", entryPoint);

        return panel;
    }

    private JPanel createLaunchSection()
    {
        JPanel panel = Utils.createSettingsSection("Launch");

        Utils.addSettingsFormRow(panel, 0, "VM options:", vmOptions);
        Utils.addSettingsFormRow(panel, 1, "Program arguments:", programArgs);
        Utils.addSettingsFormRow(panel, 2, "Working directory:", workingDir);
        Utils.addSettingsFormRow(panel, 3, "Environment variables:", environment);

        beforeLaunchOptions.setRenderer(labelledBy(value -> displayName((BeforeLaunch) value)));
        Utils.addCompactSettingsFormRow(panel, 4, "Before launch:", beforeLaunchOptions);

        return panel;
    }

    private JPanel buttonBar()
    {
        apply.setFocusable(false); cancel.setFocusable(false);
        apply.setEnabled(false); // waiting on the manager to save one
        cancel.addActionListener(event -> dispose());

        JPanel panel = new JPanel();
        panel.add(apply); panel.add(cancel);

        return panel;
    }

    /// Enum constants and records should not reach the screen in their code form.
    private static DefaultListCellRenderer labelledBy(Function<Object, String> text)
    {
        return new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (component instanceof JLabel label && value != null) label.setText(text.apply(value));

                return component;
            }
        };
    }

    private static String displayName(BeforeLaunch beforeLaunch)
    {
        return switch (beforeLaunch)
        {
            case COMPILE_TARGET -> "Compile target";
            case BUILD_TARGET   -> "Build target";
            case NONE           -> "Nothing";
        };
    }
}
