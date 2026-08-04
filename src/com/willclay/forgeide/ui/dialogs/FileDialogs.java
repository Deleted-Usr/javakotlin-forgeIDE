package com.willclay.forgeide.ui.dialogs;

import com.willclay.forgeide.files.SourceFileIO;
import com.willclay.forgeide.ui.Utils;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The file chooser, and only the file chooser.
 * <p>
 * This used to be {@code EditorFileActions}, which asked the user for a file
 * <em>and</em> read it <em>and</em> pushed it into the editor. Splitting those
 * apart is what makes the action layer work: the actions decide what happens,
 * {@link SourceFileIO} touches the disk, and this class does nothing but ask a
 * question and return the answer.
 * <p>
 * One chooser instance is reused so it remembers the last directory.
 */
public final class FileDialogs
{
    private final Component parent;
    private final JFileChooser chooser = new JFileChooser();

    public FileDialogs(Component parent)
    {
        this.parent = parent;

        chooser.setFileFilter(new FileNameExtensionFilter("Java Source Files (*.java)", "java"));
        chooser.setAcceptAllFileFilterUsed(false);
    }

    /** @return the chosen file, or null if the user cancelled or picked something that is not Java */
    public Path chooseFileToOpen()
    {
        chooser.setDialogTitle("Open Java File");

        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null;

        Path file = chooser.getSelectedFile().toPath();

        if (!SourceFileIO.isJavaFile(file))
        {
            Utils.showErrorMessage(parent, "Not a Java Source File: " + file.getFileName());
            return null;
        }

        return file;
    }

    /**
     * Asks where to save, appending .java to a bare name and confirming an
     * overwrite before it returns.
     *
     * @param suggested pre-selected in the dialog, or null for none
     * @return the chosen file, or null if the user cancelled or declined the overwrite
     */
    public Path chooseFileToSave(Path suggested)
    {
        chooser.setDialogTitle("Save Java File");

        if (suggested != null) chooser.setSelectedFile(suggested.toFile());

        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;

        Path file = SourceFileIO.withJavaExtension(chooser.getSelectedFile().toPath());

        if (Files.exists(file)
                && !Utils.confirm(parent, "Overwrite?", file.getFileName() + " already exists. Overwrite it?"))
        {
            return null;
        }

        return file;
    }
}
