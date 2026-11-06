package com.willclay.forgeide.ui.settings.filetypes;

import com.willclay.forgeide.application.IDESettingsConfiguration;
import com.willclay.forgeide.highlighting.filetypes.FileType;
import com.willclay.forgeide.highlighting.filetypes.FileTypes;
import com.willclay.forgeide.ui.Utils;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Which extensions each of core's file types claims: GLSL, JSON, TOML and
/// Markdown, whatever project is open.
///
/// GLSL is the reason this page exists. Shader extensions are a convention of
/// whichever engine or binding a project uses (`.vert`, `.vs`, `.vsh`, ...), so
/// no fixed list suits everyone, and since the highlighter is in core the
/// choice belongs with the IDE rather than with any one language.
///
/// Each row is a comma-separated field. Only rows that differ from Forge's
/// defaults are saved, so an untouched type keeps getting whatever defaults a
/// later version ships.
public final class FileTypeSettings extends JPanel
{
    private final Map<FileType, JTextField> fields = new LinkedHashMap<>();

    public FileTypeSettings()
    {
        super(new BorderLayout());

        JPanel section = Utils.createSettingsSection("Extensions");

        int row = 0;
        for (FileType type : FileTypes.ALL)
        {
            JTextField field = new JTextField(24);
            field.setToolTipText("Separate extensions with commas. Leave empty to stop "
                    + type.displayName() + " claiming any extension.");

            JButton reset = new JButton("Reset");
            reset.setFocusable(false);
            reset.setToolTipText("Restore Forge's default extensions for " + type.displayName());
            reset.addActionListener(event -> field.setText(format(type.extensions())));

            JPanel control = new JPanel(new BorderLayout(6, 0));
            control.add(field, BorderLayout.CENTER);
            control.add(reset, BorderLayout.EAST);

            Utils.addSettingsFormRow(section, row++, type.displayName() + ":", control);
            fields.put(type, field);
        }

        JLabel note = new JLabel("<html>A language plugin's own files always use its highlighter, "
                + "so an extension it claims is not affected by these settings.</html>");
        note.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");

        GridBagConstraints constraints = Utils.createSettingsRowConstraints(row);
        constraints.gridwidth = 2;
        section.add(note, constraints);

        JPanel page = Utils.createSettingsPage();
        Utils.addSettingsSection(page, section);

        add(page, BorderLayout.NORTH);
    }

    public void load(IDESettingsConfiguration settings)
    {
        Map<String, List<String>> chosen = settings.fileTypes().extensions();

        for (Map.Entry<FileType, JTextField> entry : fields.entrySet())
        {
            FileType type = entry.getKey();
            List<String> extensions = chosen.get(type.id());

            entry.getValue().setText(extensions == null ? format(type.extensions()) : String.join(", ", extensions));
        }
    }

    /// The extensions as edited, keeping only the types that differ from their defaults.
    ///
    /// @throws IllegalArgumentException if a field holds something that is not
    ///         an extension, or two types claim the same one
    public IDESettingsConfiguration.FileTypeAssociations getValues()
    {
        Map<String, List<String>> changed = new LinkedHashMap<>();

        for (Map.Entry<FileType, JTextField> entry : fields.entrySet())
        {
            FileType type = entry.getKey();
            List<String> extensions;

            try
            {
                extensions = FileTypes.parseExtensions(entry.getValue().getText());
            }
            catch (IllegalArgumentException exception)
            {
                throw new IllegalArgumentException(type.displayName() + ": " + exception.getMessage(), exception);
            }

            if (!Set.copyOf(extensions).equals(type.extensions())) changed.put(type.id(), extensions);
        }

        FileTypes.requireDistinctExtensions(FileTypes.configured(changed));

        return new IDESettingsConfiguration.FileTypeAssociations(changed);
    }

    /// Defaults are a set, so they are shown sorted to read the same every time.
    private static String format(Collection<String> extensions)
    {
        return String.join(", ", extensions.stream().sorted().toList());
    }
}