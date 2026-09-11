package com.willclay.forgeide.ui.icons;

import com.formdev.flatlaf.icons.FlatAbstractIcon;
import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.nio.file.Path;
import java.util.Locale;

/// Shared, scalable file-type symbols for tree rows, editor tabs and breadcrumbs.
/// Classification uses the path's name only; it never reads the filesystem.
public final class FileIcons
{
    private static final Icon JAVA = new FileIcon("J", "Objects.Yellow");
    private static final Icon KOTLIN = new FileIcon("K", "Objects.Purple");
    private static final Icon CPP = new FileIcon("C", "Objects.Blue");
    private static final Icon MARKDOWN = new FileIcon("M", "Objects.Blue");
    private static final Icon CONFIG = new FileIcon("=", "Objects.Green");
    private static final Icon FILE = new FileIcon("", "Objects.Grey");
    private static final Icon SOURCE = new FileIcon("", "Objects.Green");
    private static final Icon FOLDER = new FileIcon(null, "Objects.Yellow");

    private FileIcons() { }

    public static Icon folder() { return FOLDER; }

    public static Icon forPath(Path path) { return forPath(path, false); }

    public static Icon forPath(Path path, boolean sourceFile)
    {
        String name = path == null || path.getFileName() == null
                ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1);
        return switch (extension)
        {
            case "java" -> JAVA;
            case "kt", "kts" -> KOTLIN;
            case "c", "cc", "cpp", "cxx", "h", "hpp", "hxx" -> CPP;
            case "md", "markdown" -> MARKDOWN;
            case "json", "xml", "yaml", "yml", "properties", "toml" -> CONFIG;
            default -> sourceFile ? SOURCE : FILE;
        };
    }

    private static final class FileIcon extends FlatAbstractIcon
    {
        private final String letter;
        private final String colourKey;

        private FileIcon(String letter, String colourKey)
        {
            super(16, 16, null);
            this.letter = letter;
            this.colourKey = colourKey;
        }

        @Override
        protected void paintIcon(Component component, Graphics2D graphics)
        {
            Color colour = UIManager.getColor(colourKey);
            if (colour == null) colour = UIManager.getColor("Label.foreground");
            graphics.setColor(colour == null ? Color.GRAY : colour);
            graphics.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D outline = new Path2D.Float();
            if (letter == null)
            {
                outline.moveTo(1.5, 4); outline.lineTo(6, 4); outline.lineTo(8, 6);
                outline.lineTo(14.5, 6); outline.lineTo(14.5, 13); outline.lineTo(1.5, 13);
            }
            else
            {
                outline.moveTo(3, 1.5); outline.lineTo(10, 1.5); outline.lineTo(13, 4.5);
                outline.lineTo(13, 14.5); outline.lineTo(3, 14.5);
            }
            outline.closePath();
            graphics.draw(outline);
            if (letter != null && !letter.isEmpty())
            {
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
                FontMetrics metrics = graphics.getFontMetrics();
                graphics.drawString(letter, (16 - metrics.stringWidth(letter)) / 2f, 11.5f);
            }
        }
    }
}
