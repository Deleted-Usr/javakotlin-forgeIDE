package main.java.com.willclay.forgeide.files;

import main.java.com.willclay.forgeide.ui.DialogFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class LoadFile
{
    public void loadFile(JTextPane editor, Component component)
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Java File");

        FileNameExtensionFilter filter = new FileNameExtensionFilter("Java Source Files (*.java)", "java");
        chooser.setFileFilter(filter);

        int response = chooser.showOpenDialog(null); // Open the UI dialog

        if (response == JFileChooser.APPROVE_OPTION)
        {
            File selectedFile = chooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();
            System.out.println(selectedFile.getAbsolutePath());

            if (!filePath.toLowerCase().endsWith(".java"))
            {
                DialogFactory.showErrorMessage(component, "File is not a Java Source");
                return;
            }

            try (BufferedReader br = new BufferedReader(new FileReader(selectedFile)))
            {
                // JTextPane has no append(String). Build the contents first and
                // hand them over in a single setText call — this is also far
                // faster, since it triggers one highlight pass instead of one
                // per line. Do NOT use codeEditor.read(): it installs a brand
                // new Document, which would drop the DocumentListener and the
                // tab stops configured above.
                StringBuilder contents = new StringBuilder();

                String line;
                while ((line = br.readLine()) != null)
                {
                    contents.append(line).append("\n");
                }

                editor.setText(contents.toString());
                editor.setCaretPosition(0);
            }
            catch (IOException e)
            {
                DialogFactory.showErrorMessage(component, "Failed to Load File: " + e.getMessage());
            }
        }
        else
        {
            DialogFactory.showErrorMessage(component, "File Selection Cancelled by the User");
        }
    }
}
