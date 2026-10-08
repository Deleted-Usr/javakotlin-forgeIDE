package com.willclay.forgeide.services.todo;

import com.willclay.forgeide.files.SourceFileIO;
import com.willclay.forgeide.highlighting.TodoFinder;
import com.willclay.forgeide.highlighting.Token;
import com.willclay.forgeide.lang.LanguageRegistry;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/// Finds the TO-DOs in source files, using each file's own lexer so that
/// "TO-DO" inside a string is never reported. No Swing, no threads: callers
/// decide where this runs.
public final class TodoScanner
{
    private static final Set<String> BUILD_DIRECTORIES = Set.of("build", "out", "target");
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // generated or minified files, not source anyone wrote

    private final LanguageRegistry languages;

    public TodoScanner(LanguageRegistry languages)
    {
        this.languages = languages;
    }

    /// Scans text with normalised '\n' line endings.
    /// Returned line and column numbers are both 1-based.
    ///
    /// @return the TO-DOs in `text`, or an empty list if no language recognises the file
    public List<TodoItem> scan(Path file, String text)
    {
        Optional<Lexer> lexer = languageFor(file).map(Language::lexer);

        if (lexer.isEmpty())
        {
            return List.of();
        }

        List<Token> tokens     = lexer.get().tokenize(text);
        List<Token> spans      = TodoFinder.find(text, tokens);
        List<TodoItem> results = new ArrayList<>();

        int offset    = 0;
        int line      = 1;
        int lineStart = 0;

        for (int i = 0; i < spans.size(); i++)
        {
            Token marker = spans.get(i);

            // Continue counting from the previous marker instead of
            // rescanning the beginning of the file for every TO-DO.
            while (offset < marker.start())
            {
                if (text.charAt(offset) == '\n')
                {
                    line++;
                    lineStart = offset + 1;
                }

                offset++;
            }

            int column = marker.start() - lineStart + 1;
            StringBuilder description = new StringBuilder(text.substring(marker.start(), marker.end()).strip());

            // TodoFinder emits the marker first, followed by any
            // continuation spans. A new TO-DO/FIXME starts the next item.
            while (i + 1 < spans.size())
            {
                Token next = spans.get(i + 1);

                boolean startsNew = TodoFinder.MARKER.matcher(text).region(next.start(), next.end()).lookingAt();

                if (startsNew)
                {
                    break;
                }

                description.append(' ').append(text.substring(next.start(), next.end()).strip());
                i++;
            }

            results.add(new TodoItem(file, line, column, description.toString()));
        }

        return List.copyOf(results);
    }

    /// Reads a file and scans it. Files no language recognises are skipped
    /// without being read, which also keeps images and jars out.
    public List<TodoItem> scanFile(Path file, Charset charset) throws IOException
    {
        if (languageFor(file).isEmpty())
        {
            return List.of();
        }

        String text = SourceFileIO.read(file, charset).text();
        return scan(file, text);
    }

    /// Walks a whole project. Hidden folder and build output are skipped.
    public Map<Path, List<TodoItem>> scanProject(Project project) throws IOException
    {
        Path root       = project.root();
        Charset charset = project.configuration().fileHandling().encoding().charset();

        TreeMap<Path, List<TodoItem>> results = new TreeMap<>();

        if (!Files.isDirectory(root))
        {
            return results;
        }

        Files.walkFileTree(root, new SimpleFileVisitor<>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException
            {
                // Always enter the explicitly requested project root.
                if (!directory.equals(root))
                {
                    String name = directory.getFileName().toString();

                    boolean hidden      = name.startsWith(".") || Files.isHidden(directory);
                    boolean buildOutput = BUILD_DIRECTORIES.contains(name) || name.startsWith("cmake-build-");

                    if (hidden || buildOutput || project.isExcluded(directory))
                    {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException
            {
                if (attributes.isRegularFile() && (attributes.size() <= MAX_FILE_SIZE))
                {
                    try
                    {
                        List<TodoItem> todos = scanFile(file, charset);

                        if (!todos.isEmpty())
                        {
                            results.put(file, todos);
                        }
                    }
                    catch (IOException unreadable)
                    {
                        // One unreadable file should not hide every other file's TODOs.
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            // SimpleFileVisitor rethrows here by default, which would end the walk
            // at the first folder or file the OS refuses to open.
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception)
            {
                return FileVisitResult.CONTINUE;
            }
        });

        return results;
    }

    private Optional<Language> languageFor(Path file)
    {
        return languages.languages().stream().filter(l -> l.recognises(file)).findFirst();
    }
}
