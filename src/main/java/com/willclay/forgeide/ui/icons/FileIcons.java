package com.willclay.forgeide.ui.icons;

import com.formdev.flatlaf.icons.FlatAbstractIcon;
import com.willclay.forgeide.lang.api.FileIconStyle;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.templates.TemplateIcon;
import javax.swing.Icon;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
public final class FileIcons
{
    private static final Icon MARKDOWN = new FileIcon("M", "Objects.Blue");
    private static final Icon CONFIG = new FileIcon("=", "Objects.Green");
    private static final Icon FILE = new FileIcon("", "Objects.Grey");
    private static final Icon SOURCE = new FileIcon("", "Objects.Green");
    private static final Icon FOLDER = new FileIcon(null, "Objects.Yellow");

    private static final Map<String, Icon> BUILT_IN = Map.of(
            "md", MARKDOWN, "markdown", MARKDOWN,
            "json", CONFIG, "xml", CONFIG, "yaml", CONFIG, "yml", CONFIG,
            "properties", CONFIG, "toml", CONFIG
    );

    /// Extension (lowercase, no dot) to icon. Replaced as a whole rather than
    /// edited, and volatile, so a painting thread never sees it half-built.
    private static volatile Map<String, Icon> languageIcons = Map.of();

    private FileIcons() { }

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

    public static Icon folder() { return FOLDER; }

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
                ? new FileIcon(icon.letter(), icon.colourKey(), true)
                : new FileIcon(icon.letter(), icon.colourKey());
    }

    public static Icon forPath(Path path) { return forPath(path, false); }

    public static Icon forPath(Path path, boolean sourceFile)
    {
        String name = path == null || path.getFileName() == null
                ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);

        // Try every suffix, longest first, so a language can claim a compound
        // extension such as `.d.ts` as well as an ordinary one.
        Map<String, Icon> languages = languageIcons;
        for (int dot = name.indexOf('.'); dot >= 0; dot = name.indexOf('.', dot + 1))
        {
            String extension = name.substring(dot + 1);

            Icon icon = languages.get(extension);
            if (icon == null) icon = BUILT_IN.get(extension);
            if (icon != null) return icon;
        }

        return sourceFile ? SOURCE : FILE;
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
        private final boolean round;

        private FileIcon(String letter, String colourKey)
        {
            this(letter, colourKey, false);
        }

        private FileIcon(String letter, String colourKey, boolean round)
        {
            super(16, 16, null);
            this.letter = letter;
            this.colourKey = colourKey;
            this.round = round;
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

            Shape outline = round ? new Ellipse2D.Float(1, 1, 14, 14) : pageOutline(letter == null);
            Area shape = new Area(outline);

            if (letter != null && !letter.isEmpty())
            {
                // Cut the glyph out of the filled shape so the letter is transparent.
                Font font = new Font(Font.SANS_SERIF, Font.BOLD, round ? 10 : 9);

                GlyphVector glyphs = font.createGlyphVector(graphics.getFontRenderContext(), letter);
                Rectangle2D bounds = glyphs.getVisualBounds();

                float x = (float) (8 - bounds.getCenterX());
                float y = (float) ((round ? 8 : 10.5) - bounds.getCenterY());

                Shape glyph = glyphs.getOutline(x, y);
                shape.subtract(new Area(glyph));
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
}
