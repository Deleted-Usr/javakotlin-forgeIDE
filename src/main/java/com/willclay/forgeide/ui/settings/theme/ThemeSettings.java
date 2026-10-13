package com.willclay.forgeide.ui.settings.theme;

import com.formdev.flatlaf.extras.FlatUIDefaultsInspector;
import com.willclay.forgeide.services.settings.theme.CustomTheme;
import com.willclay.forgeide.services.settings.theme.Theme;
import com.willclay.forgeide.services.settings.theme.ThemeService;
import com.willclay.forgeide.ui.Utils;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Chooses the IDE theme, and looks after the custom theme documents that
/// themes can also come from. Unlike the other pages, a choice here applies
/// (and is saved) straight away rather than waiting for Apply.
///
/// The page only asks [ThemeService] to do things: reading and writing the
/// documents happens there, off the Event Dispatch Thread. Whenever the service
/// reports a change (a reload, a new theme) the page rebuilds itself from it.
public final class ThemeSettings extends JPanel
{
    private final ThemeService service;

    private final JComboBox<Theme> themes = new JComboBox<>();
    private final JButton editButton      = new JButton("Edit Document");
    private final JTextArea problems      = new JTextArea(4, 40);
    private final JPanel problemsSection  = Utils.createSettingsSection("Problems in theme documents");
    private final Runnable refresher      = this::refresh;

    /// What the list was last built from, so it is only rebuilt when that changes.
    private List<Theme> listed = List.of();

    /// Set while the page changes the list itself, so that the change is not
    /// mistaken for the user choosing a theme.
    private boolean refreshing;

    public ThemeSettings(ThemeService service)
    {
        super(new BorderLayout());

        this.service = Objects.requireNonNull(service, "themeService");

        themes.setRenderer(new ThemeRenderer());
        themes.addActionListener(event -> themeChosen());

        JPanel colourSection = Utils.createSettingsSection("Colour theme");
        Utils.addCompactSettingsFormRow(colourSection, 0, "Theme:", themes);

        JPanel page = Utils.createSettingsPage();
        Utils.addSettingsSection(page, colourSection);
        Utils.addSettingsSection(page, createCustomSection());
        Utils.addSettingsSection(page, createProblemsSection());

        add(page, BorderLayout.NORTH);
        refresh();
    }

    private JPanel createCustomSection()
    {
        JButton newButton = new JButton("New Theme…");
        newButton.setToolTipText("Start a new theme document as a copy of the current theme");
        newButton.addActionListener(event -> createTheme());

        editButton.setToolTipText("Open the current theme's document in the editor");
        editButton.addActionListener(event -> editTheme());

        JButton reloadButton = new JButton("Reload");
        reloadButton.setToolTipText("Read the theme documents again, after changing them outside Forge");
        reloadButton.addActionListener(event -> service.reloadCustomThemes());

        JButton keysButton = new JButton("Browse UI Keys…");
        keysButton.setToolTipText("Every UI default of the current look and feel: the keys a theme's \"swing\" section can change");
        keysButton.addActionListener(event -> FlatUIDefaultsInspector.show());

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        for (JButton button : List.of(newButton, editButton, reloadButton, keysButton))
        {
            if (buttons.getComponentCount() > 0) buttons.add(Box.createHorizontalStrut(6));
            buttons.add(button);
        }
        buttons.add(Box.createHorizontalGlue());

        JLabel hint = new JLabel("Saving a theme document in Forge's editor applies it straight away.");
        hint.putClientProperty("FlatLaf.styleClass", "small");
        hint.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");

        JPanel section = Utils.createSettingsSection("Custom themes");
        Utils.addSettingsFormRow(section, 0, "Folder:", Utils.readOnlyField(service.getCatalog().directory().toString()));
        addFullWidthRow(section, 1, buttons);
        addFullWidthRow(section, 2, hint);

        return section;
    }

    /// Hidden while every document reads cleanly.
    private JPanel createProblemsSection()
    {
        problems.setEditable(false);
        problems.setLineWrap(true);
        problems.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(problems);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        addFullWidthRow(problemsSection, 0, scrollPane);

        return problemsSection;
    }

    private static void addFullWidthRow(JPanel section, int row, Component component)
    {
        GridBagConstraints constraints = Utils.createSettingsRowConstraints(row);
        constraints.gridwidth = 2;

        section.add(component, constraints);
    }

    /// Rebuilds the page from the service: the list of themes, which one is
    /// selected, and any problems with the documents.
    private void refresh()
    {
        Theme current = service.getTheme();

        // A custom theme whose document has stopped loading stays on screen
        // until another theme is chosen, so it stays in the list as well.
        List<Theme> available = new ArrayList<>(service.getCatalog().themes());
        if (!available.contains(current)) available.add(current);

        refreshing = true;
        try
        {
            if (!available.equals(listed))
            {
                themes.setModel(new DefaultComboBoxModel<>(available.toArray(Theme[]::new)));
                listed = available;
            }
            themes.setSelectedItem(current);
        }
        finally
        {
            refreshing = false;
        }

        editButton.setEnabled(current instanceof CustomTheme);

        // Bullets show where one problem ends once a long one has wrapped.
        List<String> found = service.getCatalog().problems();
        problems.setText(String.join("\n", found.stream().map(problem -> "• " + problem).toList()));
        problems.setCaretPosition(0);
        problemsSection.setVisible(!found.isEmpty());
        revalidate();
    }

    private void themeChosen()
    {
        if (refreshing) return;

        if (themes.getSelectedItem() instanceof Theme theme && theme != service.getTheme())
        {
            if (!service.setTheme(theme)) themes.setSelectedItem(service.getTheme());
        }
    }

    private void createTheme()
    {
        String name = Utils.prompt(this, "New Theme",
                "Name of the new theme. It starts as a copy of " + service.getTheme().getDisplayName() + ".",
                "My Theme");

        if (name == null) return;
        if (name.isBlank())
        {
            Utils.showErrorMessage(this, "A theme needs a name.");
            return;
        }

        service.createCustomTheme(name);
    }

    private void editTheme()
    {
        if (service.getTheme() instanceof CustomTheme custom) service.editDocument(custom.getDocument());
    }

    /// Listens to the service only while the page is on screen. The settings
    /// window is built afresh each time it opens, and a page that stayed
    /// subscribed after its window had closed could never be garbage collected.
    @Override
    public void addNotify()
    {
        super.addNotify();
        service.addChangeListener(refresher);
        refresh();
    }

    @Override
    public void removeNotify()
    {
        service.removeChangeListener(refresher);
        super.removeNotify();
    }

    /// Marks custom themes in the list, so one called "Forge Dark" can still be
    /// told apart from the built-in theme of that name.
    private static final class ThemeRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused)
        {
            Object text = value instanceof CustomTheme custom ? custom.getDisplayName() + " (custom)" : value;

            return super.getListCellRendererComponent(list, text, index, selected, focused);
        }
    }
}
