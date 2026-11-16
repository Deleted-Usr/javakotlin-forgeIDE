package com.willclay.forgeide.ui.icons;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// A set of file icons: a folder of SVG files and an `icon-set.properties` file
/// saying which SVG to use for which key (`ext.java`, `folder`, `kind.interface`
/// ...). See the bundled Atom Material set's properties file for every key.
///
/// **An icon set is data, not code.** It is never a plugin JAR and never runs
/// anything: it is only pictures and a list. So adding one is copying a folder,
/// and there is nothing to keep compatible except the key names.
///
/// **Gaps are fine.** [#icon(String)] returns null for a key the set doesn't
/// have, and [FileIcons] falls back to its own drawn icon. That is also all
/// the built-in [#FORGE] set is: a set with no keys at all.
///
/// Three small conventions on top of "key = file":
///
/// - **Variants by file name.** Next to `x.svg`, a file called `x_dark.svg` is
///   used while a dark theme is on, and `x_open.svg` is the open version of a
///   folder icon (it becomes the key `<key>.open`). Nothing has to list them.
/// - **Layers.** `class.svg + finalMark.svg` draws the second picture on top of
///   the first, the way IntelliJ adds a small mark to a class icon.
/// - **Folder patterns.** `folder.match.<name> = <regex>` gives every folder
///   whose name matches the icon `folder.<name>`. The regex is searched for
///   (so use `^...$` to match a whole name) and ignores case. When several
///   match, the one written first in the file wins.
///
/// [#load] reads the properties file and checks which variants exist, which is
/// file work, so it runs off the EDT. The SVGs themselves are parsed by FlatLaf
/// the first time each icon is painted.
public final class IconSet
{
    public static final String PROPERTIES_FILE = "icon-set.properties";

    /// Forge's own drawn icons, with nothing replaced.
    public static final IconSet FORGE = new IconSet("forge", "Forge", "Drawn by Forge itself", Map.of(), List.of());

    private static final String FOLDER_PATTERN_PREFIX = "folder.match.";

    private final String id;
    private final String displayName;
    private final String credit;

    /// Key to the pictures drawn for it, bottom layer first. Almost always one.
    private final Map<String, List<Layer>> layers;

    /// In the order they were written, which is the order they're tried in.
    private final List<FolderPattern> folderPatterns;

    /// Icons are made on first use and kept, so each SVG is parsed only once.
    /// Only touched on the EDT, while painting.
    private final Map<String, Icon> icons = new HashMap<>();

    /// One picture: its normal SVG, and its `_dark` twin if the set has one.
    private record Layer(URL light, URL dark) { }

    /// A folder-name regex, and the key of the icon it gives.
    private record FolderPattern(Pattern pattern, String key) { }

    private IconSet(String id, String displayName, String credit, Map<String, List<Layer>> layers, List<FolderPattern> folderPatterns)
    {
        this.id             = id;
        this.displayName    = displayName;
        this.credit         = credit;
        this.layers         = Map.copyOf(layers);
        this.folderPatterns = List.copyOf(folderPatterns);
    }

    /// Reads the set in the folder at `base`: a classpath folder for a bundled
    /// set, or a `file:` folder for one in `~/.forge/icon-sets`.
    ///
    /// Blocking: call it off the EDT.
    ///
    /// @param base the folder, ending in `/`
    /// @throws IOException if the properties file is missing or unreadable, or
    ///                     names a file outside the folder, or has a bad regex
    public static IconSet load(String id, URL base) throws IOException
    {
        // Properties forgets the order lines were written in, and folder
        // patterns need it ("first match wins"), so note each key as it's read.
        List<String> order = new ArrayList<>();
        Properties properties = new Properties()
        {
            @Override
            public synchronized Object put(Object key, Object value)
            {
                if (!containsKey(key)) order.add((String) key);
                return super.put(key, value);
            }
        };

        try (InputStream in = resolve(base, PROPERTIES_FILE).openStream())
        {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        Map<String, List<Layer>> layers   = new LinkedHashMap<>();
        List<FolderPattern> folderPatterns = new ArrayList<>();

        for (String rawKey : order)
        {
            String key   = rawKey.trim().toLowerCase(Locale.ROOT);
            String value = properties.getProperty(rawKey).trim();

            if (value.isEmpty() || key.equals("name") || key.equals("credit")) continue;

            if (key.startsWith(FOLDER_PATTERN_PREFIX))
            {
                String name = key.substring(FOLDER_PATTERN_PREFIX.length());
                folderPatterns.add(new FolderPattern(compile(name, value), "folder." + name));
                continue;
            }

            List<Layer> picture = new ArrayList<>();
            for (String file : value.split("\\+"))
            {
                picture.add(layer(base, file.trim()));
            }
            layers.put(key, picture);

            // An open version of a single picture, e.g. a folder: x_open.svg.
            if (picture.size() == 1)
            {
                String file = value.trim();
                URL open = variant(base, file, "_open");
                if (open != null) layers.put(key + ".open", List.of(layer(base, stem(file) + "_open.svg")));
            }
        }

        String name = properties.getProperty("name", id).trim();
        return new IconSet(id, name.isEmpty() ? id : name, properties.getProperty("credit", "").trim(), layers, folderPatterns);
    }

    public String id()          { return id; }
    public String displayName() { return displayName; }

    /// Who made the icons, shown under the choice in Settings. May be empty.
    public String credit()      { return credit; }

    /// The icon for `key`, or null when this set doesn't have one. Call on the EDT.
    public Icon icon(String key)
    {
        List<Layer> picture = layers.get(key);
        if (picture == null) return null;

        return icons.computeIfAbsent(key, k -> new SetIcon(picture.stream().map(SetIcon.Part::of).toList()));
    }

    /// The icon key (`folder.<name>`) for a folder called `folderName`, or null
    /// when no pattern matches.
    public String folderKey(String folderName)
    {
        for (FolderPattern folder : folderPatterns)
        {
            if (folder.pattern().matcher(folderName).find()) return folder.key();
        }

        return null;
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof IconSet set && set.id.equals(id);
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public String toString()
    {
        return displayName;
    }

    private static Pattern compile(String name, String regex) throws IOException
    {
        try
        {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        }
        catch (PatternSyntaxException e)
        {
            throw new IOException("the folder pattern \"" + name + "\" is not a valid regex: " + e.getDescription());
        }
    }

    private static Layer layer(URL base, String file) throws IOException
    {
        return new Layer(resolve(base, file), variant(base, file, "_dark"));
    }

    /// `x_dark.svg` for `x.svg`, say, or null if the set doesn't have one.
    private static URL variant(URL base, String file, String suffix) throws IOException
    {
        if (!file.endsWith(".svg")) return null;

        URL variant = resolve(base, stem(file) + suffix + ".svg");
        return exists(variant) ? variant : null;
    }

    private static String stem(String file)
    {
        return file.endsWith(".svg") ? file.substring(0, file.length() - 4) : file;
    }

    /// Always drawn at 16x16, whatever size the SVG says, so a set can't make
    /// tree rows taller or tabs wider. FlatLaf scales it for HiDPI screens.
    private static FlatSVGIcon svg(URL url)
    {
        return new FlatSVGIcon(url).derive(16, 16);
    }

    /// `file` must sit directly in the set's folder: a set can't point at
    /// pictures somewhere else on disk.
    ///
    /// Joined as text rather than with `URI.resolve`, because a bundled set
    /// lives in a `jar:` URL once Forge is packaged, and `resolve` does nothing
    /// with those. The URI constructor escapes spaces and the like.
    private static URL resolve(URL base, String file) throws IOException
    {
        if (file.isEmpty() || file.contains("/") || file.contains("\\") || file.equals("..") || file.equals("."))
        {
            throw new IOException("\"" + file + "\" must be a file in the icon set's own folder");
        }

        try
        {
            String escaped = new URI(null, null, file, null).getRawPath();
            return URI.create(base.toExternalForm() + escaped).toURL();
        }
        catch (URISyntaxException | IllegalArgumentException e)
        {
            throw new IOException("Bad icon file name \"" + file + "\"", e);
        }
    }

    private static boolean exists(URL url)
    {
        try (InputStream ignored = url.openStream())
        {
            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /// Paints each layer in turn. Each one uses its dark SVG while a dark theme
    /// is on, if the set has one; that's decided at paint time, so changing
    /// theme needs no reload.
    private record SetIcon(List<Part> parts) implements Icon
    {
        private record Part(FlatSVGIcon light, FlatSVGIcon dark)
        {
            static Part of(Layer layer)
            {
                return new Part(svg(layer.light()), layer.dark() == null ? null : svg(layer.dark()));
            }

            FlatSVGIcon current()
            {
                return dark != null && FlatSVGIcon.isDarkLaf() ? dark : light;
            }
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            for (Part part : parts) part.current().paintIcon(component, graphics, x, y);
        }

        @Override public int getIconWidth()  { return parts.getFirst().current().getIconWidth(); }
        @Override public int getIconHeight() { return parts.getFirst().current().getIconHeight(); }
    }
}
