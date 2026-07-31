package com.willclay.forgeide.ui.toolbar;

import com.willclay.forgeide.files.SourceFileIO;
import com.willclay.forgeide.ui.DialogFactory;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Open and Save, i.e. the file chooser plus the dialogs that go with it.
 * <p>
 * The disk work itself lives in {@link SourceFileIO}; this class is only the
 * conversation with the user. One chooser instance is reused so it remembers
 * the last directory.
 */
public class EditorFileActions
{
    private final Component parent;
    private final CodeEditorPanel editor;
    private final JFileChooser chooser = new JFileChooser();

    public EditorFileActions(Component parent, CodeEditorPanel editor)
    {
        this.parent = parent;
        this.editor = editor;

        chooser.setFileFilter(new FileNameExtensionFilter("Java Source Files (*.java)", "java"));
        chooser.setAcceptAllFileFilterUsed(false);
    }

    public void open()
    {
        chooser.setDialogTitle("Open Java File");

        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        Path file = chooser.getSelectedFile().toPath();

        if (!SourceFileIO.isJavaFile(file))
        {
            DialogFactory.showErrorMessage(parent, "Not a Java Source File: " + file.getFileName());
            return;
        }

        try
        {
            editor.setText(SourceFileIO.read(file));
        }
        catch (IOException e)
        {
            DialogFactory.showErrorMessage(parent, "Failed to load file: " + e.getMessage());
        }
    }

    public void save()
    {
        chooser.setDialogTitle("Save Java File");

        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        Path file = SourceFileIO.withJavaExtension(chooser.getSelectedFile().toPath());

        if (Files.exists(file) && !DialogFactory.confirm(parent, "Overwrite?", file.getFileName() + " already exists. Overwrite it?"))
        {
            return;
        }

        try
        {
            SourceFileIO.write(file, editor.getText());
            DialogFactory.showInfoMessage(parent, "File saved successfully!");
        }
        catch (IOException e)
        {
            DialogFactory.showErrorMessage(parent, "Failed to save file: " + e.getMessage());
        }
    }
}
