package com.willclay.forgeide.ui.dialogs;

import com.formdev.flatlaf.FlatClientProperties;
import com.willclay.forgeide.lang.api.templates.FileTemplate;
import com.willclay.forgeide.lang.api.templates.FileTemplates;
import com.willclay.forgeide.lang.api.templates.TemplateOption;
import com.willclay.forgeide.lang.api.templates.TemplateRequest;
import com.willclay.forgeide.ui.icons.FileIcons;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// The "New Java Class" style dialog: a name, a choice of what kind of file to
/// make, and a few toggles that change it.
///
/// The dialog is the same for every language. What fills it — the kinds, the
/// toggles and the text each kind produces — comes from the project language's
/// [FileTemplates], so this class never mentions Java or Kotlin.
///
/// It only *asks*. It returns a [Choice] and the caller decides what to do with
/// it: open an unsaved tab, or create a file in the explorer.
///
/// Keyboard: type a name, Up and Down change the kind, Enter creates, Escape
/// cancels. Double-clicking a kind creates it immediately.
public final class NewFileDialog extends JDialog
{
    /// Type names are written straight into code, so they have to be identifiers.
    /// This is the common subset of Java, Kotlin and C++ identifiers.
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /// What the user chose.
    ///
    /// @param options the ids of the toggles that were on and that the chosen
    ///                template supports
    public record Choice(FileTemplate template, String name, Set<String> options)
    {
        /// The file name, extension included — `Player.java`.
        public String fileName()
        {
            return name + template.extension();
        }

        /// The starting text of the file.
        public String render(String packageName)
        {
            return template.render(new TemplateRequest(name, packageName, options));
        }
    }

    private final FileTemplates templates;
    private final Icon languageIcon;
    private final JTextField nameField = new JTextField(24);
    private final JLabel errorLabel = new JLabel(" ");
    private final List<JToggleButton> kindButtons = new ArrayList<>();
    private final Map<TemplateOption, JToggleButton> optionButtons = new LinkedHashMap<>();

    private FileTemplate selected;
    private Choice result;

    private NewFileDialog(Window owner, String title, FileTemplates templates, Icon languageIcon)
    {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.templates = templates;
        this.languageIcon = languageIcon;

        setContentPane(createContent());
        installKeyboard();

        select(templates.templates().get(0));

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);

        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowOpened(WindowEvent event)
            {
                nameField.requestFocusInWindow();
            }
        });
    }

    /// Shows the dialog and waits for the user.
    ///
    /// @param languageIcon shown on any template that has no icon of its own;
    ///                     normally the language's file icon
    /// @return the choice, or empty if the dialog was cancelled
    public static Optional<Choice> show(Component parent, String title, FileTemplates templates, Icon languageIcon)
    {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (owner == null && parent instanceof Window window) owner = window;

        NewFileDialog dialog = new NewFileDialog(owner, title, templates, languageIcon);
        dialog.setVisible(true); // blocks until the dialog closes, because it is modal

        return Optional.ofNullable(dialog.result);
    }

    // --- Layout --- //

    /// No heading inside the dialog: the title bar already names it, and
    /// repeating it here only pushes the name field further down.
    private JPanel createContent()
    {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        nameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Name");
        nameField.getAccessibleContext().setAccessibleName("Name");
        c.insets = new Insets(0, 0, 2, 0);
        content.add(nameField, c);

        errorLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Component.error.focusedBorderColor");
        c.insets = new Insets(0, 2, 8, 0);
        content.add(errorLabel, c);

        c.insets = new Insets(0, 24, 12, 24);
        content.add(createKindButtons(), c);

        if (!templates.options().isEmpty())
        {
            c.insets = new Insets(0, 0, 12, 0);
            content.add(createOptionButtons(), c);
        }

        c.insets = new Insets(4, 0, 0, 0);
        content.add(createDialogButtons(), c);

        return content;
    }

    private JPanel createKindButtons()
    {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        ButtonGroup group = new ButtonGroup();

        for (FileTemplate template : templates.templates())
        {
            JToggleButton button = new JToggleButton(template.displayName(), iconFor(template));

            // Left-aligned so the icons form a column, as in IntelliJ's dialog.
            button.setHorizontalAlignment(JToggleButton.LEADING);
            button.setIconTextGap(8);
            button.putClientProperty(FlatClientProperties.STYLE, "margin: 4,10,4,10");
            button.setFocusable(false); // focus stays in the name field; Up and Down move the selection

            button.addActionListener(event -> select(template));
            button.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent event)
                {
                    if (event.getClickCount() == 2) create();
                }
            });

            group.add(button);
            kindButtons.add(button);
            panel.add(button);
        }

        return panel;
    }

    private Icon iconFor(FileTemplate template)
    {
        return template.icon() == null ? languageIcon : FileIcons.forTemplate(template.icon());
    }

    private JPanel createOptionButtons()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));

        for (TemplateOption option : templates.options())
        {
            JToggleButton button = new JToggleButton(option.label());
            button.addActionListener(event ->
            {
                if (button.isSelected()) clearExcluded(option);
            });

            optionButtons.put(option, button);
            panel.add(button);
        }

        return panel;
    }

    private JPanel createDialogButtons()
    {
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());

        JButton create = new JButton("Create");
        create.addActionListener(event -> create());
        getRootPane().setDefaultButton(create); // Enter anywhere in the dialog

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.add(cancel);
        panel.add(create);

        return panel;
    }

    private void installKeyboard()
    {
        JComponent root = getRootPane();
        bind(root, JComponent.WHEN_IN_FOCUSED_WINDOW, "ESCAPE", "cancel", this::dispose);

        bind(nameField, JComponent.WHEN_FOCUSED, "UP", "previousKind", () -> moveSelection(-1));
        bind(nameField, JComponent.WHEN_FOCUSED, "DOWN", "nextKind", () -> moveSelection(1));
    }

    private static void bind(JComponent component, int condition, String key, String name, Runnable action)
    {
        component.getInputMap(condition).put(KeyStroke.getKeyStroke(key), name);
        component.getActionMap().put(name, new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent event)
            {
                action.run();
            }
        });
    }

    // --- Behaviour --- //

    private void select(FileTemplate template)
    {
        selected = template;

        int index = templates.templates().indexOf(template);
        kindButtons.get(index).setSelected(true);

        // A toggle that does not apply to this kind is greyed out, not cleared,
        // so flicking between kinds does not lose what the user switched on.
        optionButtons.forEach((option, button) -> button.setEnabled(template.supports(option)));
    }

    private void moveSelection(int step)
    {
        List<FileTemplate> all = templates.templates();
        int index = Math.floorMod(all.indexOf(selected) + step, all.size());
        select(all.get(index));
    }

    private void clearExcluded(TemplateOption option)
    {
        optionButtons.forEach((other, button) ->
        {
            if (option.excludes().contains(other.id())) button.setSelected(false);
        });
    }

    private void create()
    {
        String name = stripExtension(nameField.getText().trim(), selected.extension());

        if (name.isEmpty())
        {
            showError("Enter a name.");
            return;
        }
        if (!NAME.matcher(name).matches())
        {
            showError("Use letters, digits and underscores, starting with a letter.");
            return;
        }

        Set<String> options = new LinkedHashSet<>();
        optionButtons.forEach((option, button) ->
        {
            if (button.isSelected() && selected.supports(option)) options.add(option.id());
        });

        result = new Choice(selected, name, options);
        dispose();
    }

    private void showError(String message)
    {
        errorLabel.setText(message);
        nameField.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
        nameField.requestFocusInWindow();
    }

    /// Typing `Player.java` is a natural slip; it means `Player`.
    private static String stripExtension(String name, String extension)
    {
        return name.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))
                ? name.substring(0, name.length() - extension.length())
                : name;
    }
}
