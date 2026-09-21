package com.willclay.forgeide.ui.dialogs;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemFileChooser.FileNameExtensionFilter;
import com.willclay.forgeide.files.SourceFileIO;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.LanguageRegistry;
import com.willclay.forgeide.ui.Utils;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/// Owns Forge's file and project-selection dialogs.
public final class FileDialogs
{
    private final Component parent;
    private final LanguageRegistry languages;
    private final SystemFileChooser fileChooser = new SystemFileChooser();
    private final SystemFileChooser directoryChooser = new SystemFileChooser();

    public FileDialogs(Component parent, LanguageRegistry languages)
    {
        this.parent = parent;
        this.languages = languages;

        fileChooser.setAcceptAllFileFilterUsed(false);
        directoryChooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
    }

    /// Values collected by the New Project dialog.
    public record NewProjectDetails(String name, Language language) { }

    /// @return the chosen directory, or `null` if cancelled
    public Path chooseDirectory(String title)
    {
        return chooseDirectory(title, null);
    }

    /// @param start the directory the chooser opens in, or `null` to open
    ///              wherever it was last
    /// @return the chosen directory, or `null` if cancelled
    public Path chooseDirectory(String title, Path start)
    {
        directoryChooser.setDialogTitle(title);
        if (start != null) directoryChooser.setCurrentDirectory(start.toFile());

        if (directoryChooser.showOpenDialog(parent) != SystemFileChooser.APPROVE_OPTION) return null;

        return directoryChooser.getSelectedFile().toPath();
    }

    /// Prompts for both the new project's name and its permanent language.
    public NewProjectDetails chooseNewProjectDetails()
    {
        JTextField name = new JTextField("MyProject", 24);
        JComboBox<String> language = new JComboBox<>(languageNames());

        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 4));
        fields.add(new JLabel("Project name:"));
        fields.add(name);
        fields.add(new JLabel("Language:"));
        fields.add(language);

        while (true)
        {
            int choice = JOptionPane.showConfirmDialog(parent, fields, "New Project", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return null;

            String projectName = name.getText().trim();
            if (isSimpleName(projectName))
            {
                return new NewProjectDetails(projectName, languages.languages().get(language.getSelectedIndex()));
            }

            Utils.showErrorMessage(parent, "Enter a single valid folder name for the project.");
        }
    }

    /// Asks which language should be assigned to an unconfigured directory.
    public Language chooseLanguage(String title, String message)
    {
        List<Language> available = languages.languages();
        String[] names = languageNames();

        Object selected = JOptionPane.showInputDialog(parent, message, title, JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
        if (selected == null) return null;

        for (int i = 0; i < names.length; i++)
        {
            if (names[i].equals(selected)) return available.get(i);
        }

        return null;
    }

    /// @return a source file recognised by `language`, or `null`
    public Path chooseFileToOpen(Language language)
    {
        configureFileChooser(language);
        fileChooser.setDialogTitle("Open " + language.displayName() + " File");

        if (fileChooser.showOpenDialog(parent) != SystemFileChooser.APPROVE_OPTION) return null;

        Path file = fileChooser.getSelectedFile().toPath();
        if (!language.recognises(file))
        {
            Utils.showErrorMessage(parent, "Not a " + language.displayName() + " source file: " + file.getFileName());
            return null;
        }

        return file;
    }

    /// Asks where to save, appends the language's default extension to a bare
    /// name and confirms before overwriting an existing file.
    public Path chooseFileToSave(Language language, Path suggested)
    {
        configureFileChooser(language);
        fileChooser.setDialogTitle("Save " + language.displayName() + " File");

        if (suggested != null) fileChooser.setSelectedFile(suggested.toFile());
        if (fileChooser.showSaveDialog(parent) != SystemFileChooser.APPROVE_OPTION) return null;

        Path file = SourceFileIO.withExtension(fileChooser.getSelectedFile().toPath(), language.defaultExtension());
        if (Files.exists(file) && !Utils.confirm(parent, "Overwrite?", file.getFileName() + " already exists. Overwrite it?"))
        {
            return null;
        }

        return file;
    }

    private void configureFileChooser(Language language)
    {
        fileChooser.resetChoosableFileFilters();
        fileChooser.setFileFilter(createFilter(language));
        fileChooser.setAcceptAllFileFilterUsed(false);
    }

    private String[] languageNames()
    {
        return languages.languages().stream().map(Language::displayName).toArray(String[]::new);
    }

    private static FileNameExtensionFilter createFilter(Language language)
    {
        String[] extensions = language.extensions().stream()
                .map(FileDialogs::withoutLeadingDot)
                .toArray(String[]::new);
        String patterns = language.extensions().stream()
                .map(extension -> "*" + extension)
                .collect(Collectors.joining(", "));

        return new FileNameExtensionFilter(
                language.displayName() + " Source Files (" + patterns + ")", extensions);
    }

    private static String withoutLeadingDot(String extension)
    {
        return extension.startsWith(".") ? extension.substring(1) : extension;
    }

    private static boolean isSimpleName(String name)
    {
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return false;

        try
        {
            Path path = Path.of(name);
            return !path.isAbsolute() && path.getNameCount() == 1;
        }
        catch (InvalidPathException e)
        {
            return false;
        }
    }
}
