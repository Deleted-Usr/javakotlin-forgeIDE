package com.willclay.forgeide.services.settings.icons;

import com.willclay.forgeide.ui.icons.IconSet;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/// Every icon set Forge can offer: the built-in ones, then any found in a
/// folder (`~/.forge/icon-sets`), one sub-folder per set.
///
/// Works the same way as the theme catalog: [#reload()] reads the folder off
/// the EDT and swaps in a new immutable [Snapshot], so the EDT can keep asking
/// for [#sets()] while a reload runs and sees either the old list or the new
/// one, never half of each.
public final class IconSetCatalog
{
    /// The set a fresh install uses, and the fallback for a saved id that no
    /// longer exists (a deleted folder, say).
    public static final String DEFAULT_ID = "atom-material";

    /// Sets in other folders get this prefix, so a folder called `forge` can
    /// never be confused with the built-in set of that id.
    private static final String CUSTOM_PREFIX = "custom:";

    private static final String BUNDLED_ROOT = "/com/willclay/forgeide/icons/sets/";

    private final Path directory;
    private volatile Snapshot snapshot = new Snapshot(List.of(IconSet.FORGE), List.of());

    private record Snapshot(List<IconSet> sets, List<String> problems) { }

    public IconSetCatalog(Path directory)
    {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    /// The folder user icon sets are read from.
    public Path directory()
    {
        return directory;
    }

    /// Reads the bundled sets and every set in the folder again. A set that
    /// can't be read is left out, and the reason is added to [#problems()].
    ///
    /// Blocking: call it off the EDT.
    public void reload()
    {
        List<IconSet> sets   = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        sets.add(IconSet.FORGE);
        loadBundled(DEFAULT_ID, sets, problems);

        for (Path folder : listFolders(problems))
        {
            String name = folder.getFileName().toString();

            try
            {
                URL base = folder.toUri().toURL(); // a folder's URI already ends in '/'
                sets.add(IconSet.load(CUSTOM_PREFIX + name.toLowerCase(Locale.ROOT), base));
            }
            catch (IOException e)
            {
                problems.add(name + ": " + e.getMessage());
            }
        }

        snapshot = new Snapshot(List.copyOf(sets), List.copyOf(problems));
    }

    /// The sets on offer, built-in ones first.
    public List<IconSet> sets()
    {
        return snapshot.sets();
    }

    /// A plain-English line for each set that couldn't be read.
    public List<String> problems()
    {
        return snapshot.problems();
    }

    /// The set saved as `id`, or the default when it's gone, or Forge's own
    /// icons if even that couldn't be read.
    public IconSet resolve(String id)
    {
        List<IconSet> sets = sets();

        return sets.stream().filter(set -> set.id().equals(id)).findFirst()
                .or(() -> sets.stream().filter(set -> set.id().equals(DEFAULT_ID)).findFirst())
                .orElse(IconSet.FORGE);
    }

    /// Bundled sets live inside Forge's JAR. Their folder is found from the
    /// properties file, because asking a JAR for a *folder* only works if it
    /// happens to contain an entry for it.
    private static void loadBundled(String id, List<IconSet> sets, List<String> problems)
    {
        URL properties = IconSetCatalog.class.getResource(BUNDLED_ROOT + id + "/" + IconSet.PROPERTIES_FILE);

        if (properties == null)
        {
            problems.add("The built-in \"" + id + "\" icon set is missing from Forge's files.");
            return;
        }

        try
        {
            String text = properties.toExternalForm();
            URL base = URI.create(text.substring(0, text.length() - IconSet.PROPERTIES_FILE.length())).toURL();

            sets.add(IconSet.load(id, base));
        }
        catch (IOException | IllegalArgumentException e)
        {
            problems.add("The built-in \"" + id + "\" icon set could not be read: " + e.getMessage());
        }
    }

    /// Sub-folders that contain an `icon-set.properties`, sorted by name so the
    /// list in Settings doesn't change order between runs. Other folders are
    /// skipped quietly: they may be work in progress.
    private List<Path> listFolders(List<String> problems)
    {
        if (!Files.isDirectory(directory)) return List.of();

        try (Stream<Path> children = Files.list(directory))
        {
            return children
                    .filter(Files::isDirectory)
                    .filter(folder -> Files.isRegularFile(folder.resolve(IconSet.PROPERTIES_FILE)))
                    .sorted()
                    .toList();
        }
        catch (IOException e)
        {
            problems.add("Could not read " + directory + ": " + e.getMessage());
            return List.of();
        }
    }
}
