package com.willclay.forgeide.services.settings.theme;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/// Every theme Forge can offer: the built-in [AppTheme]s, followed by the
/// [CustomTheme]s read from the documents in one folder (`~/.forge/themes`).
///
/// This is the only class that reads or writes theme documents, and that is
/// file work, so [#reload()] and [#create] must run off the Event Dispatch
/// Thread. What they find is swapped in as one immutable [Snapshot], so the
/// EDT can keep calling [#themes()] or [#find] while a reload is running on
/// another thread: it sees either the old snapshot or the new one, never half
/// of each.
public final class ThemeCatalog
{
    private final Path directory;
    private volatile Snapshot snapshot = new Snapshot(List.of(), List.of());

    /// The custom themes that loaded, and a plain-English line for every
    /// problem found on the way (prefixed with the file it was in).
    private record Snapshot(List<CustomTheme> customThemes, List<String> problems) { }

    public ThemeCatalog(Path directory)
    {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    /// The folder theme documents are read from.
    public Path directory()
    {
        return directory;
    }

    /// Reads every `.json` document in the folder again, replacing the custom
    /// themes found last time. A document that cannot be read only loses its
    /// own theme; the reason is added to [#problems()].
    ///
    /// Blocking: call it off the Event Dispatch Thread.
    public void reload()
    {
        List<CustomTheme> themes = new ArrayList<>();
        List<String> problems    = new ArrayList<>();

        for (Path file : listDocuments(problems))
        {
            String fileName       = file.getFileName().toString();
            List<String> warnings = new ArrayList<>();

            try
            {
                themes.add(CustomTheme.fromDocument(file, CustomThemeDocument.read(file), warnings));
            }
            catch (IOException | IllegalArgumentException e)
            {
                problems.add(fileName + " was not loaded: " + e.getMessage());
            }

            for (String warning : warnings) problems.add(fileName + ": " + warning);
        }

        snapshot = new Snapshot(List.copyOf(themes), List.copyOf(problems));
    }

    private List<Path> listDocuments(List<String> problems)
    {
        try (Stream<Path> files = Files.list(directory))
        {
            return files.filter(this::isDocument).filter(Files::isRegularFile).sorted().toList();
        }
        catch (NoSuchFileException e)
        {
            return List.of();
        }
        catch (IOException e)
        {
            problems.add("Could not list the theme folder " + directory + ": " + e.getMessage());
            return List.of();
        }
    }

    /// Whether `file` is where a theme document would be: a `.json` file
    /// directly inside the folder. Only the path is looked at, so this is safe
    /// to ask on the EDT.
    public boolean isDocument(Path file)
    {
        Path absolute = file.toAbsolutePath().normalize();

        return directory.equals(absolute.getParent())
                &&
               absolute.getFileName()
                       .toString()
                       .toLowerCase(Locale.ROOT)
                       .endsWith(CustomTheme.FILE_EXTENSION);
    }

    /// Every theme, built-in ones first.
    public List<Theme> themes()
    {
        return Stream.concat(Arrays.stream(AppTheme.values()), customThemes().stream()).toList();
    }

    public List<CustomTheme> customThemes()
    {
        return snapshot.customThemes();
    }

    /// What went wrong while reading the documents last time, one line per problem.
    public List<String> problems()
    {
        return snapshot.problems();
    }

    public Optional<Theme> find(String id)
    {
        Optional<AppTheme> builtIn = AppTheme.find(id);

        if (builtIn.isPresent())
        {
            return Optional.of(builtIn.get());
        }

        for (CustomTheme theme : customThemes())
        {
            if (theme.id().equals(id))
            {
                return Optional.of(theme);
            }
        }

        return Optional.empty();
    }

    /// The theme with this id, or [AppTheme#DEFAULT] when there is none: the id
    /// may belong to a custom theme whose document was deleted or broken.
    public Theme resolve(String id)
    {
        return find(id).orElse(AppTheme.DEFAULT);
    }

    /// Saves a new document, with a file name made from the theme's name. If
    /// that name is taken, `-2`, `-3`, ... is added rather than replacing
    /// somebody's existing theme.
    ///
    /// Blocking: call it off the Event Dispatch Thread.
    ///
    /// @return the file written
    public Path create(CustomThemeDocument document) throws IOException
    {
        Files.createDirectories(directory);
        String stem = fileStemFor(document.name());

        for (int attempt = 1; ; attempt++)
        {
            String suffix = attempt == 1 ? "" : "-" + attempt;
            Path file = directory.resolve(stem + suffix + CustomTheme.FILE_EXTENSION);

            try
            {
                CustomThemeDocument.write(file, document);
                return file;
            }
            catch (FileAlreadyExistsException taken)
            {
                // Try the next suffix.
            }
        }
    }

    /// `Copper Night!` becomes `copper-night`: safe on every file system, and
    /// still recognisably the theme's name.
    static String fileStemFor(String name)
    {
        // Splitting "é" into "e" plus an accent mark, then dropping the marks,
        // keeps `Thème` readable as `theme` instead of losing the letter.
        String unaccented = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");

        String stem = unaccented.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        return stem.isEmpty() ? "theme" : stem;
    }
}
