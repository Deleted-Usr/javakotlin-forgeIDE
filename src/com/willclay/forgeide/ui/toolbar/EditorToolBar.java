package com.willclay.forgeide.ui.toolbar;

import javax.swing.*;

/**
 * The strip of buttons across the top.
 *
 * Takes callbacks rather than reaching back into Window, so it has no idea what
 * running or saving actually involves.
 *
 * TODO - move these onto a JMenuBar as well, and turn the compiler options into
 *        JToggleButtons once there are any.
 */
public final class EditorToolBar extends JToolBar
{
    private final JButton runButton = new JButton("\u25B6 Run Code");

    public EditorToolBar(Runnable onRun, Runnable onSave, Runnable onOpen)
    {
        setFloatable(false);

        runButton.addActionListener(e -> onRun.run());
        add(runButton);

        add(Box.createHorizontalGlue()); // pushes the file buttons to the right

        JButton saveButton = new JButton("Save File");
        saveButton.addActionListener(e -> onSave.run());
        add(saveButton);

        JButton loadButton = new JButton("Load File");
        loadButton.addActionListener(e -> onOpen.run());
        add(loadButton);
    }

    /** Disabled while a build is in flight so two cannot overlap. */
    public void setRunEnabled(boolean enabled)
    {
        runButton.setEnabled(enabled);
    }
}
