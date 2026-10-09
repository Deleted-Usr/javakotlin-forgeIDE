package com.willclay.forgeide.ui.settings;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage;
import com.willclay.forgeide.services.settings.SettingsService;
import com.willclay.forgeide.services.settings.theme.ThemeService;
import com.willclay.forgeide.services.settings.project.ProjectSettingsService;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.menu.SettingsButton;
import com.willclay.forgeide.ui.settings.general.GeneralSettings;
import com.willclay.forgeide.ui.settings.project.ProjectSettings;
import com.willclay.forgeide.ui.settings.theme.ThemeSettings;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/// The settings window [SettingsButton] opens
///
/// A sidebar on the left lists the pages and a [CardLayout] on the right shows
/// the one that is selected. Each page is its own class that talks to a
/// settings layer, keeping the UI separate from the IDE backend; this window
/// only arranges the pages and runs Apply.
///
/// The sidebar is a column of toggle buttons in a [ButtonGroup], which is
/// what makes exactly one stay selected. Pages saved with the open project
/// (including any its language contributes) are grouped under "Project".
public final class SettingsWindow extends JDialog
{
    private static final int SIDEBAR_WIDTH = 190;

    private final SettingsService settingsService;
    private final ProjectSettingsService projectService;
    private final ThemeService themeService;
    private final GeneralSettings generalSettings;
    private final ProjectSettings projectSettings;

    private final JPanel sidebar             = new JPanel();
    private final ButtonGroup sidebarButtons = new ButtonGroup();
    private final CardLayout pageLayout      = new CardLayout();
    private final JPanel pages               = new JPanel(pageLayout);

    public SettingsWindow(
            Window owner,
            SettingsService settingsService,
            ProjectSettingsService projectService,
            ThemeService themeService
    )
    {
        super(owner, "IDE Settings", ModalityType.MODELESS);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        setSize(780, 620);
        setLocationRelativeTo(owner);

        this.settingsService = settingsService;
        this.projectService  = projectService;
        this.themeService    = themeService;

        this.generalSettings = new GeneralSettings();
        this.projectSettings = new ProjectSettings(projectService);

        this.generalSettings.load(settingsService.get());

        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));

        addPages();

        add(createSidebarPanel(), BorderLayout.WEST);
        add(pages, BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);
    }

    private void addPages()
    {
        addPage(
                "General",
                "Startup, editor defaults, saving, and build behaviour for every project.",
                generalSettings,
                false
        );
        addPage(
                "Theme",
                "How Forge looks. A new theme applies straight away.",
                new ThemeSettings(themeService),
                false
        );

        addGroupHeading("Project");

        addPage(
                "General",
                "Settings for the open project, saved with it in .forge/project.json.",
                projectSettings,
                true
        );

        for (LanguageSettingsPage page : projectSettings.languagePages())
        {
            addPage(
                    page.title(),
                    page.title() + " settings for this project, saved with its metadata.",
                    page.component(),
                    true
            );
        }
    }

    /// Adds a page to the card layout and a button for it to the sidebar. The
    /// first page added is the one the window opens on.
    private void addPage(String title, String description, JComponent content, boolean nested)
    {
        // Card names only need to be unique, and titles can repeat ("General").
        String card = String.valueOf(pages.getComponentCount());
        pages.add(createPage(title, description, content), card);

        JToggleButton button = new JToggleButton(title);

        button.setFocusable(false);

        button.setHorizontalAlignment(SwingConstants.LEADING);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);

        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.putClientProperty("FlatLaf.style", nested
                ? "arc: 8; toolbar.margin: 6,22,6,10"
                : "arc: 8; toolbar.margin: 6,10,6,10");

        button.addActionListener(event -> pageLayout.show(pages, card));

        // Stretch to the sidebar's width so the selected background is a full row.
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));

        sidebarButtons.add(button);
        sidebar.add(button);

        if (sidebarButtons.getButtonCount() == 1) button.setSelected(true);
    }

    private void addGroupHeading(String text)
    {
        JLabel heading = new JLabel(text.toUpperCase());

        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.setBorder(BorderFactory.createEmptyBorder(14, 10, 4, 10));

        heading.putClientProperty("FlatLaf.styleClass", "small");
        heading.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");

        sidebar.add(heading);
    }

    /// One page: a title and description above the page's own content, which
    /// scrolls when it is taller than the window.
    private static JComponent createPage(String title, String description, JComponent content)
    {
        JLabel titleLabel = new JLabel(title);
        titleLabel.putClientProperty("FlatLaf.styleClass", "h2");

        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");

        // Left padding matches Utils.createSettingsPage, so the title lines up
        // with the section headings below it.
        JPanel header = new JPanel(new BorderLayout(0, 2));
        header.setBorder(BorderFactory.createEmptyBorder(14, 12, 2, 12));
        header.add(titleLabel, BorderLayout.NORTH);
        header.add(descriptionLabel, BorderLayout.CENTER);

        JScrollPane scrollPane = new JScrollPane(new PageBody(content));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel page = new JPanel(new BorderLayout());
        page.add(header, BorderLayout.NORTH);
        page.add(scrollPane, BorderLayout.CENTER);

        return page;
    }

    private JComponent createSidebarPanel()
    {
        // The buttons sit at the top of their own column. Putting them at NORTH
        // of the outer panel instead would span the full width and push the
        // separator down below them.
        JPanel column = new JPanel(new BorderLayout());
        column.add(sidebar, BorderLayout.NORTH);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(UIScale.scale(SIDEBAR_WIDTH), 0));
        panel.add(column, BorderLayout.CENTER);
        panel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.EAST);

        return panel;
    }

    private JComponent createFooter()
    {
        JButton apply  = new JButton("Apply");
        JButton cancel = new JButton("Cancel");

        apply.setFocusable(false); cancel.setFocusable(false);
        apply.addActionListener(event -> applySettings());
        cancel.addActionListener(event -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 10));
        buttons.add(apply); buttons.add(cancel);

        JPanel footer = new JPanel(new BorderLayout());
        footer.add(new JSeparator(), BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.CENTER);

        return footer;
    }

    private void applySettings()
    {
        try
        {
            settingsService.save(generalSettings.getValues(settingsService.get().appearance().theme()));
        }
        catch (IOException | IllegalArgumentException exception)
        {
            Utils.showErrorMessage(this, "Could not apply IDE settings: " + exception.getMessage());
            return;
        }

        if (!projectSettings.isAvailable()) return;

        try
        {
            projectService.apply(projectSettings.getProjectRoot(), projectSettings.getValues());
        }
        catch (IOException | IllegalArgumentException | IllegalStateException exception)
        {
            Utils.showErrorMessage(this, "Could not apply project settings: " + exception.getMessage());
        }
    }

    /// Holds a page's content inside its scroll pane.
    ///
    /// A plain panel in a [JScrollPane] keeps its own preferred width, so a
    /// page could end up wider than the window and be cut off. Implementing
    /// [Scrollable] lets the panel tell the scroll pane: "always match the
    /// window's width, and scroll only up and down".
    private static final class PageBody extends JPanel implements Scrollable
    {
        PageBody(JComponent content)
        {
            super(new BorderLayout());
            add(content, BorderLayout.NORTH);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
        {
            return UIScale.scale(16);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
        {
            return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        /// Fills the window when the page is short, so the background has no gap.
        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
        }
    }
}
