package com.willclay.forgeide.lang.jvm;

import com.willclay.forgeide.lang.api.run.RunOptionsPage;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Map;

/// The run configuration page for languages that run on the JVM.
///
/// Java and Kotlin both use it, with their own title: the choices are the
/// JVM's, not the language's.
public final class JvmRunOptionsPage implements RunOptionsPage
{
    private final String title;
    private final JCheckBox enableAssertions = new JCheckBox("Enable assertions (-ea)");
    private final JPanel component           = new JPanel(new BorderLayout(0, 2));

    public JvmRunOptionsPage(String title)
    {
        this.title = title;

        JLabel hint = new JLabel("Without -ea, the JVM skips every assert statement.");
        hint.putClientProperty("FlatLaf.styleClass", "small");
        hint.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");

        component.add(enableAssertions, BorderLayout.NORTH);
        component.add(hint, BorderLayout.CENTER);
    }

    @Override
    public String title()
    {
        return title;
    }

    @Override
    public String runtimeOptionsLabel()
    {
        return "VM options";
    }

    @Override
    public String runtimeOptionsHint()
    {
        return "Given to the JVM before your class starts, such as -Xmx512m for more memory.";
    }

    @Override
    public JComponent component()
    {
        return component;
    }

    @Override
    public void addChangeListener(Runnable listener)
    {
        enableAssertions.addActionListener(event -> listener.run());
    }

    @Override
    public void load(Map<String, Object> options)
    {
        enableAssertions.setSelected(JvmRunOptions.from(options).enableAssertions());
    }

    @Override
    public Map<String, Object> getValues()
    {
        return new JvmRunOptions(enableAssertions.isSelected()).toJson();
    }
}
