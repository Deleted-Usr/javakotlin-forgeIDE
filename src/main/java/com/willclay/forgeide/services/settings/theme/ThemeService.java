package com.willclay.forgeide.services.settings.theme;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.highlighting.TokenTheme;
import com.willclay.forgeide.highlighting.TokenType;
import com.willclay.forgeide.services.ForgeConsole;
import com.willclay.forgeide.services.settings.SettingsService;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;

import javax.swing.JFrame;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/// Applies the selected IDE theme, keeps the choice in the IDE settings store,
/// and looks after the custom theme documents in its [ThemeCatalog].
///
/// Custom themes are meant to be edited while Forge is running. Saving a theme
/// document in Forge's own editor reloads the documents and re-applies the
/// current theme, so a change shows up the moment it is saved, and anything
/// wrong with a document is explained in the Console.
///
/// Everything here runs on the Event Dispatch Thread except the reading and
/// writing of documents, which happens in [SwingWorker]s.
public final class ThemeService
{
    /// The Swing keys a new theme document starts with: the editor's own
    /// background and text colour. They are what the token colours have to be
    /// readable against, and what the rest of the editor (the current-line
    /// band, the gutter) is mixed from, so they belong with the tokens.
    private static final List<String> STARTER_SWING_KEYS = List.of("TextPane.background", "TextPane.foreground");

    private final JFrame frame;
    private final CodeEditorPanel editorPanel;
    private final EditorManager editorManager;
    private final SettingsService settings;
    private final ThemeCatalog catalog;
    private final List<Runnable> listeners = new ArrayList<>();

    private Theme currentTheme = AppTheme.DEFAULT;

    public ThemeService(
            JFrame frame,
            CodeEditorPanel editorPanel,
            EditorManager editorManager,
            SettingsService settings,
            ThemeCatalog catalog
    )
    {
        this.frame         = Objects.requireNonNull(frame, "frame");
        this.editorPanel   = Objects.requireNonNull(editorPanel, "editorPanel");
        this.editorManager = Objects.requireNonNull(editorManager, "editorManager");
        this.settings      = Objects.requireNonNull(settings, "settings");
        this.catalog       = Objects.requireNonNull(catalog, "catalog");

        editorManager.addSaveListener(this::fileSaved);
    }

    /// Installs a theme's look and feel, with its Swing overrides, as Swing's
    /// look and feel. [com.willclay.forgeide.Main] needs exactly this step
    /// before any window exists, which is why it is static.
    ///
    /// The overrides are handed to FlatLaf as *global extra defaults*. FlatLaf
    /// merges those into the theme's own `.properties` before it works out any
    /// value, so overriding a variable such as `@accentColor` reaches every key
    /// that is built from it. They are replaced on every install (with nothing,
    /// for a built-in theme), which is what stops one custom theme's changes
    /// from leaking into the next theme chosen.
    public static void installLookAndFeel(Theme theme) throws UnsupportedLookAndFeelException
    {
        Map<String, String> overrides = theme.getSwingOverrides();

        FlatLaf.setGlobalExtraDefaults(overrides.isEmpty() ? null : overrides);
        UIManager.setLookAndFeel(theme.getSwingTheme());
    }

    /// Applies the persisted theme while the application window is starting.
    public void applySavedTheme()
    {
        String savedId = settings.getThemeId();
        Optional<Theme> saved = catalog.find(savedId);

        // The id is left in settings.json: if it names a custom theme whose
        // document is broken, the theme comes back once the document is fixed.
        if (saved.isEmpty())
        {
            ForgeConsole.println("Theme: \"" + savedId + "\" was not found, so "
                    + AppTheme.DEFAULT.getDisplayName() + " is used instead.");
        }

        Theme theme = saved.orElse(AppTheme.DEFAULT);

        if (!apply(theme, false) && theme != AppTheme.DEFAULT && apply(AppTheme.DEFAULT, false))
        {
            try
            {
                settings.setTheme(AppTheme.DEFAULT);
            }
            catch (IOException exception)
            {
                Utils.showErrorMessage(frame, "The fallback theme could not be saved: " + exception.getMessage());
            }
        }
    }

    public Theme getTheme()
    {
        return currentTheme;
    }

    public boolean setTheme(Theme theme)
    {
        return apply(Objects.requireNonNull(theme, "theme"), true);
    }

    /// The themes on offer, the folder custom ones come from, and any problems
    /// reading them.
    public ThemeCatalog getCatalog()
    {
        return catalog;
    }

    /// Runs `listener` whenever the current theme or the list of themes changes.
    public void addChangeListener(Runnable listener)
    {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeChangeListener(Runnable listener)
    {
        listeners.remove(listener);
    }

    /// Reads the theme documents again on a background thread. Afterwards the
    /// current theme is re-applied if it is a custom one, so changes to its
    /// document show up, and any problems are printed to the Console.
    public void reloadCustomThemes()
    {
        new SwingWorker<Void, Void>()
        {
            @Override
            protected Void doInBackground()
            {
                catalog.reload();
                return null;
            }

            @Override
            protected void done()
            {
                reportProblems();

                // A theme whose document no longer loads stays on screen.
                // Jumping to another theme because of a half-typed colour would
                // be worse than keeping the last good version until it is fixed.
                if (currentTheme instanceof CustomTheme current)
                {
                    catalog.find(current.id()).ifPresent(reloaded -> apply(reloaded, false));
                }

                fireChanged();
            }
        }.execute();
    }

    /// Starts a new theme document as a copy of the current theme, switches to
    /// it, and opens the document in the editor to be changed.
    ///
    /// The copy lists every token type with its current colour, so the document
    /// shows what can be changed instead of starting out empty.
    public void createCustomTheme(String name)
    {
        CustomThemeDocument document = starterDocument(Objects.requireNonNull(name, "name"));

        new SwingWorker<Path, Void>()
        {
            @Override
            protected Path doInBackground() throws IOException
            {
                Path file = catalog.create(document);
                catalog.reload();
                return file;
            }

            @Override
            protected void done()
            {
                Path file;
                try
                {
                    file = get();
                }
                catch (ExecutionException exception)
                {
                    Utils.showErrorMessage(frame, "The theme could not be created: " + exception.getCause().getMessage());
                    return;
                }
                catch (InterruptedException exception)
                {
                    Thread.currentThread().interrupt();
                    return;
                }

                reportProblems();

                // setTheme announces the change itself; otherwise the list of
                // themes has still changed and listeners need to hear about it.
                Optional<Theme> created = catalog.find(CustomTheme.idFor(file));
                if (created.isEmpty() || !setTheme(created.get())) fireChanged();

                editDocument(file);
            }
        }.execute();
    }

    /// Opens a theme document in the editor. Saving it there applies the changes.
    public void editDocument(Path document)
    {
        try
        {
            editorManager.openFile(document);
        }
        catch (IOException exception)
        {
            Utils.showErrorMessage(frame, "Could not open " + document.getFileName() + ": " + exception.getMessage());
        }
    }

    private void fileSaved(Path file)
    {
        if (catalog.isDocument(file)) reloadCustomThemes();
    }

    /// Builds the document for a new theme from the current one. It reads the
    /// installed look and feel's colours, so it must run on the EDT.
    private CustomThemeDocument starterDocument(String name)
    {
        AppTheme base = switch (currentTheme)
        {
            case AppTheme builtIn   -> builtIn;
            case CustomTheme custom -> custom.getBase();
        };

        Map<String, String> swing = new LinkedHashMap<>(currentTheme.getSwingOverrides());
        if (base.getSwingTheme() instanceof FlatLaf)
        {
            for (String key : STARTER_SWING_KEYS)
            {
                Color colour = UIManager.getColor(key);
                if (colour != null) swing.putIfAbsent(key, CustomTheme.formatColour(colour));
            }
        }

        Map<String, CustomThemeDocument.TokenStyle> tokens = new LinkedHashMap<>();

        for (TokenType type : TokenType.values())
        {
            TokenTheme.TokenStyle style = currentTheme.getTokenTheme().styleOf(type);

            tokens.put(
                    CustomTheme.tokenKey(type),
                    new CustomThemeDocument.TokenStyle(CustomTheme.formatColour(style.colour()), style.bold())
            );
        }

        return CustomThemeDocument.create(name, base.id(), swing, tokens);
    }

    private void reportProblems()
    {
        for (String problem : catalog.problems()) ForgeConsole.println("Theme: " + problem);
    }

    private void fireChanged()
    {
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private boolean apply(Theme theme, boolean persist)
    {
        Theme previousTheme     = currentTheme;
        boolean animate         = persist && frame.isShowing();
        boolean snapshotVisible = false;

        try
        {
            if (animate)
            {
                FlatAnimatedLafChange.showSnapshot();
                snapshotVisible = true;
            }

            install(theme);

            if (persist)
            {
                settings.setTheme(theme);
            }

            currentTheme = theme;

            if (snapshotVisible)
            {
                FlatAnimatedLafChange.hideSnapshotWithAnimation();
                snapshotVisible = false;
            }

            fireChanged();
            return true;
        }
        catch (UnsupportedLookAndFeelException _)
        {
            if (persist)
            {
                Utils.showErrorMessage(frame, "The selected look and feel is unsupported. The previous theme will be kept.");
            }
            return false;
        }
        catch (IOException exception)
        {
            try
            {
                install(previousTheme);
            }
            catch (UnsupportedLookAndFeelException ignored)
            {
                // The previous theme was already active, so failure here is non-recoverable.
            }

            Utils.showErrorMessage(frame, "The selected theme could not be saved: " + exception.getMessage());
            return false;
        }
        finally
        {
            // Prevent a failed theme change from leaving the snapshot overlay visible.
            if (snapshotVisible)
            {
                FlatAnimatedLafChange.stop();
            }
        }
    }

    private void install(Theme theme) throws UnsupportedLookAndFeelException
    {
        installLookAndFeel(theme);
        FlatLaf.updateUI();

        // Update custom content before revealing the new UI.
        editorPanel.setTheme(theme.getTokenTheme());
    }
}
