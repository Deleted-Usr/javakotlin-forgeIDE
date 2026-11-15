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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

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
/// [#load] reads the properties file and checks which SVGs have dark variants,
/// which is file work, so it runs off the EDT. The SVGs themselves are parsed
/// by FlatLaf the first time each icon is painted.
public final class IconSet
{
    public static final String PROPERTIES_FILE = "icon-set.properties";

    /// Forge's own drawn icons, with nothing replaced.
    public static final IconSet FORGE = new IconSet("forge", "Forge", "Drawn by Forge itself", Map.of(), Map.of());

    private final String id;
    private final String displayName;
    private final String credit;

    /// Key to the SVG used normally, and to its `_dark` twin where one exists.
    private final Map<String, URL> light;
    private final Map<String, URL> dark;

    /// Icons are made on first use and kept, so each SVG is parsed only once.
    /// Only touched on the EDT, while painting.
    private final Map<String, Icon> icons = new HashMap<>();

    private IconSet(String id, String displayName, String credit, Map<String, URL> light, Map<String, URL> dark)
    {
        this.id          = id;
        this.displayName = displayName;
        this.credit      = credit;
        this.light       = Map.copyOf(light);
        this.dark        = Map.copyOf(dark);
    }

    /// Reads the set in the folder at `base`: a classpath folder for a bundled
    /// set, or a `file:` folder for one in `~/.forge/icon-sets`.
    ///
    /// Blocking: call it off the EDT.
    ///
    /// @param base the folder, ending in `/`
    /// @throws IOException if the properties file is missing or unreadable
    public static IconSet load(String id, URL base) throws IOException
    {
        Properties properties = new Properties();

        try (InputStream in = resolve(base, PROPERTIES_FILE).openStream())
        {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        Map<String, URL> light = new LinkedHashMap<>();
        Map<String, URL> dark  = new LinkedHashMap<>();

        for (String key : properties.stringPropertyNames())
        {
            String file = properties.getProperty(key).trim();
            if (file.isEmpty() || key.equals("name") || key.equals("credit")) continue;

            String normalisedKey = key.trim().toLowerCase(Locale.ROOT);
            light.put(normalisedKey, resolve(base, file));

            if (file.endsWith(".svg"))
            {
                URL darkFile = resolve(base, file.substring(0, file.length() - 4) + "_dark.svg");
                if (exists(darkFile)) dark.put(normalisedKey, darkFile);
            }
        }

        String name = properties.getProperty("name", id).trim();
        return new IconSet(id, name.isEmpty() ? id : name, properties.getProperty("credit", "").trim(), light, dark);
    }

    public String id()          { return id; }
    public String displayName() { return displayName; }

    /// Who made the icons, shown under the choice in Settings. May be empty.
    public String credit()      { return credit; }

    /// The icon for `key`, or null when this set doesn't have one. Call on the EDT.
    public Icon icon(String key)
    {
        if (!light.containsKey(key)) return null;

        return icons.computeIfAbsent(key, k -> new SetIcon(svg(light.get(k)), dark.containsKey(k) ? svg(dark.get(k)) : null));
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
        if (file.contains("/") || file.contains("\\") || file.equals("..") || file.equals("."))
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

    /// Paints the dark SVG while a dark theme is on, if the set has one, and the
    /// normal one otherwise. Decided at paint time, so changing theme needs no
    /// reload.
    private record SetIcon(FlatSVGIcon light, FlatSVGIcon dark) implements Icon
    {
        private FlatSVGIcon current()
        {
            return dark != null && FlatSVGIcon.isDarkLaf() ? dark : light;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            current().paintIcon(component, graphics, x, y);
        }

        @Override public int getIconWidth()  { return current().getIconWidth(); }
        @Override public int getIconHeight() { return current().getIconHeight(); }
    }
}
