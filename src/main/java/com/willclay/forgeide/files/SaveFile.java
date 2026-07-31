package main.java.com.willclay.forgeide.files;

import main.java.com.willclay.forgeide.ui.DialogFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SaveFile
{
    public void saveFile(JTextPane editor, Component component)
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Java File");

        FileNameExtensionFilter filter = new FileNameExtensionFilter("Java Source Files (*.java)", "java");
        chooser.setFileFilter(filter);

        int response = chooser.showSaveDialog(null);

        if (response == JFileChooser.APPROVE_OPTION)
        {
            File selectedFile = chooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();

            if (!filePath.toLowerCase().endsWith(".java"))
            {
                selectedFile = new File(filePath + ".java");
            }

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(selectedFile)))
            {
                // write(Writer) is inherited from JTextComponent, so this still
                // works on a JTextPane and still emits plain text.
                editor.write(bw);
                DialogFactory.showInfoMessage(component, "File Saved Successfully!");
            }
            catch (IOException e)
            {
                DialogFactory.showErrorMessage(component, "Failed to Save File: " + e.getMessage());
            }
        }
        else
        {
            DialogFactory.showErrorMessage(component, "File Selection Cancelled by the User");
        }
    }
}
