package com.willclay.forgeide.ui.icons;

import com.formdev.flatlaf.icons.FlatAbstractIcon;
import com.willclay.forgeide.lang.api.DeclarationKind;
import com.willclay.forgeide.lang.api.FileIconStyle;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.templates.TemplateIcon;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/// Shared, scalable file-type symbols for tree rows, editor tabs and breadcrumbs.
/// Classification uses the path's name only; it never reads the filesystem.
///
/// **Source files get their icon from their language.** Each installed
/// [Language] describes its icon with [Language#fileIcon()] and lists its
/// extensions; [#registerLanguages(Collection)] turns those into a lookup table
/// once, at startup. A newly installed language plugin therefore gets icons
/// without anything in this class changing.
///
/// The only extensions still named here are the ones no language owns —
/// Markdown and configuration files.
///
/// **Type icons.** A source file that declares a class, interface, enum and so
/// on shows a round badge for that kind instead of the page icon, the same
/// badges the "New ..." dialog uses. Working out the kind means reading the
/// file, which this class never does: [#useDeclarations(Function)] hands it a
/// lookup that answers from a cache and reads files on a background thread.
///
/// **Icon sets.** Everything above is Forge's own drawn icons. An [IconSet]
/// chosen in Settings can replace any of them by key. This class always asks
/// the set first and falls back to its drawing when the set has no icon for
/// that key, so a set never leaves a file without an icon.
public final class FileIcons
{
    private static final Icon MARKDOWN = new FileIcon("M",  "Objects.Blue");
    private static final Icon CONFIG   = new FileIcon("=",  "Objects.Green");
    private static final Icon FILE     = new FileIcon("",   "Objects.Grey");
    private static final Icon SOURCE   = new FileIcon("",   "Objects.Green");
    private static final Icon FOLDER   = new FileIcon(null, "Objects.Yellow");

    private static final Map<String, Icon> BUILT_IN = Map.of(
            "md", MARKDOWN, "markdown", MARKDOWN,
            "json", CONFIG, "xml", CONFIG, "yaml", CONFIG, "yml", CONFIG,
            "properties", CONFIG, "toml", CONFIG
    );

    /// One badge per kind, in the same letters and colours as the "New ..."
    /// dialog. Abstract classes are hollow (there is something still to fill
    /// in) and final classes carry a small pin (nothing more can be attached).
    private static final Map<DeclarationKind, Icon> KIND_ICONS = kindIcons();

    /// Answers "what does this file declare?" from a cache, or empty if nobody
    /// has said yet. Set once the main window exists. See [#useDeclarations].
    private static volatile Function<Path, Optional<DeclarationKind>> declarations = path -> Optional.empty();

    /// Extension (lowercase, no dot) to icon. Replaced as a whole rather than
    /// edited, and volatile, so a painting thread never sees it half-built.
    private static volatile Map<String, Icon> languageIcons = Map.of();

    /// The chosen icon set. Starts as Forge's own icons, so anything painted
    /// before settings are applied still has an icon.
    private static volatile IconSet iconSet = IconSet.FORGE;

    private FileIcons() { }

    /// Switches every file and folder icon to `set`, with Forge's own icons
    /// filling any gaps. Call on the EDT, then repaint whatever shows icons.
    public static void useIconSet(IconSet set)
    {
        iconSet = set == null ? IconSet.FORGE : set;
    }

    /// Builds the icon table from the installed languages. Call once, after
    /// the languages are discovered and before anything is painted.
    ///
    /// If two languages claim the same extension, the first one listed keeps it
    /// — the same order the registry uses everywhere else.
    public static void registerLanguages(Collection<? extends Language> languages)
    {
        Map<String, Icon> icons = new HashMap<>();

        for (Language language : languages)
        {
            FileIconStyle style = language.fileIcon();
            if (style == null) continue;

            Icon icon = new FileIcon(style.letter(), style.colourKey());
            for (String extension : language.extensions())
            {
                icons.putIfAbsent(normaliseExtension(extension), icon);
            }
        }

        languageIcons = Map.copyOf(icons);
    }

    /// Lets source files show a badge for the type they declare. `lookup` is
    /// only called on the EDT and must answer from memory, never by reading the
    /// file: it runs while a tree row or tab is being painted.
    public static void useDeclarations(Function<Path, Optional<DeclarationKind>> lookup)
    {
        declarations = lookup;
    }

    /// The icon for a kind of type, for anything that wants to show one.
    public static Icon forKind(DeclarationKind kind)
    {
        return fromSet("kind." + kind.name().toLowerCase(Locale.ROOT), KIND_ICONS.get(kind));
    }

    public static Icon folder() { return fromSet("folder", FOLDER); }

    /// The icon a language describes for its files — the one its source files
    /// show in the explorer.
    public static Icon forStyle(FileIconStyle style)
    {
        return new FileIcon(style.letter(), style.colourKey());
    }

    /// A "New ..." dialog button's icon: a round badge for a type, a page for a file.
    public static Icon forTemplate(TemplateIcon icon)
    {
        return icon.shape() == TemplateIcon.Shape.TYPE
                ? new FileIcon(icon.letter(), icon.colourKey(), Badge.FILLED)
                : new FileIcon(icon.letter(), icon.colourKey());
    }

    public static Icon forPath(Path path) { return forPath(path, false); }

    public static Icon forPath(Path path, boolean sourceFile)
    {
        String name = path == null || path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        IconSet set = iconSet;

        // A whole name first, so `build.gradle.kts` can look different from other `.kts` files.
        Icon named = setIcon(set, "filename." + name);
        if (named != null) return named;

        // Try every suffix, longest first, so a language can claim a compound
        // extension such as `.d.ts` as well as an ordinary one.
        Map<String, Icon> languages = languageIcons;
        for (int dot = name.indexOf('.'); dot >= 0; dot = name.indexOf('.', dot + 1))
        {
            String extension = name.substring(dot + 1);
            Icon drawn = languages.get(extension);

            if (drawn != null)
            {
                // A language's file: show the type it declares, once that is known.
                Icon kind = kindIcon(path);
                if (kind != null)
                {
                    return kind;
                }
            }
            else
            {
                drawn = BUILT_IN.get(extension);
            }

            Icon fromSet = setIcon(set, "ext." + extension);
            if (fromSet != null)
            {
                return fromSet;
            }

            if (drawn != null)
            {
                return drawn;
            }
        }

        return fromSet("file", sourceFile ? SOURCE : FILE);
    }

    /// Null until the file has been read, and for a file with no main type.
    /// Off the EDT (a size measured from a background thread, say) it never asks,
    /// because the lookup's cache belongs to the EDT.
    private static Icon kindIcon(Path path)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            return null;
        }

        return declarations.apply(path).map(FileIcons::forKind).orElse(null);
    }

    /// The set's icon for `key`, or `drawn` when the set has none.
    private static Icon fromSet(String key, Icon drawn)
    {
        Icon icon = setIcon(iconSet, key);
        return icon != null ? icon : drawn;
    }

    /// Off the EDT, sets are not asked: their icons are made lazily, on the EDT.
    /// Every set icon is 16x16, the same as the drawn ones, so a size measured
    /// from the drawn icon is still right.
    private static Icon setIcon(IconSet set, String key)
    {
        return SwingUtilities.isEventDispatchThread() ? set.icon(key) : null;
    }

    private static Map<DeclarationKind, Icon> kindIcons()
    {
        Map<DeclarationKind, Icon> icons = new EnumMap<>(DeclarationKind.class);

        icons.put(DeclarationKind.CLASS,          new FileIcon("C", "Objects.Blue",       Badge.FILLED));
        icons.put(DeclarationKind.ABSTRACT_CLASS, new FileIcon("C", "Objects.Blue",       Badge.HOLLOW));
        icons.put(DeclarationKind.FINAL_CLASS,    new FileIcon("C", "Objects.Blue",       Badge.PINNED));
        icons.put(DeclarationKind.SEALED_CLASS,   new FileIcon("S", "Objects.Green",      Badge.FILLED));
        icons.put(DeclarationKind.INTERFACE,      new FileIcon("I", "Objects.Green",      Badge.FILLED));
        icons.put(DeclarationKind.ENUM,           new FileIcon("E", "Objects.YellowDark", Badge.FILLED));
        icons.put(DeclarationKind.RECORD,         new FileIcon("R", "Objects.Yellow",     Badge.FILLED));
        icons.put(DeclarationKind.ANNOTATION,     new FileIcon("@", "Objects.Purple",     Badge.FILLED));
        icons.put(DeclarationKind.OBJECT,         new FileIcon("O", "Objects.Yellow",     Badge.FILLED));
        icons.put(DeclarationKind.EXCEPTION,      new FileIcon("!", "Objects.Red",        Badge.FILLED));

        return Map.copyOf(icons);
    }

    private static String normaliseExtension(String extension)
    {
        String lower = extension.toLowerCase(Locale.ROOT);
        return lower.startsWith(".") ? lower.substring(1) : lower;
    }

    private static final class FileIcon extends FlatAbstractIcon
    {
        private final String letter;
        private final String colourKey;
        private final Badge badge;

        private FileIcon(String letter, String colourKey)
        {
            this(letter, colourKey, Badge.NONE);
        }

        private FileIcon(String letter, String colourKey, Badge badge)
        {
            super(16, 16, null);

            this.letter    = letter;
            this.colourKey = colourKey;
            this.badge     = badge;
        }

        @Override
        protected void paintIcon(Component component, Graphics2D graphics)
        {
            Color colour = UIManager.getColor(colourKey);

            if (colour == null)
            {
                colour = UIManager.getColor("Label.foreground");
            }

            graphics.setColor(colour == null ? Color.GRAY : colour);

            boolean round = badge != Badge.NONE;
            Shape outline = round ? new Ellipse2D.Float(1, 1, 14, 14) : pageOutline(letter == null);
            Area shape    = new Area(outline);

            if (badge == Badge.HOLLOW)
            {
                // Just the rim: the shape is still "to be filled in", like the class.
                shape.subtract(new Area(new Ellipse2D.Float(2.5f, 2.5f, 11, 11)));
            }

            if (letter != null && !letter.isEmpty())
            {
                Font font = new Font(Font.SANS_SERIF, Font.BOLD, round ? 10 : 9);

                GlyphVector glyphs = font.createGlyphVector(graphics.getFontRenderContext(), letter);
                Rectangle2D bounds = glyphs.getVisualBounds();

                float x = (float) (8 - bounds.getCenterX());
                float y = (float) ((round ? 8 : 10.5) - bounds.getCenterY());

                Area glyph = new Area(glyphs.getOutline(x, y));

                // A hollow badge has nothing to cut the letter out of, so the
                // letter is drawn solid inside the rim instead.
                if (badge == Badge.HOLLOW)
                {
                    shape.add(glyph);
                }
                else
                {
                    shape.subtract(glyph); // the letter shows through as a transparent cut-out
                }
            }

            if (badge == Badge.PINNED)
            {
                // A small dot in the bottom-right corner, with a transparent gap
                // around it so it reads as a separate mark.
                shape.subtract(new Area(new Ellipse2D.Float(9.5f, 9.5f, 7, 7)));
                shape.add(new Area(new Ellipse2D.Float(11, 11, 4.5f, 4.5f)));
            }

            graphics.fill(shape);
        }

        /// A folder tab when `folder` is true, otherwise a page with a folded corner.
        private static Path2D pageOutline(boolean folder)
        {
            Path2D outline = new Path2D.Float();

            if (folder)
            {
                outline.moveTo(1, 3.5); outline.lineTo(6, 3.5); outline.lineTo(8, 5.5);
                outline.lineTo(15, 5.5); outline.lineTo(15, 13.5); outline.lineTo(1, 13.5);
            }
            else
            {
                outline.moveTo(2.5, 1); outline.lineTo(10, 1); outline.lineTo(13.5, 4.5);
                outline.lineTo(13.5, 15); outline.lineTo(2.5, 15);
            }

            outline.closePath();
            return outline;
        }
    }

    /// The shape of an icon: a page, or one of the round badges types use.
    private enum Badge
    {
        /// A page (or folder) with the letter cut out: a plain file.
        NONE,
        /// A filled circle with the letter cut out: an ordinary type.
        FILLED,
        /// A ring with a solid letter: an abstract class.
        HOLLOW,
        /// A filled circle with a pin in the corner: a final class.
        PINNED
    }
}
