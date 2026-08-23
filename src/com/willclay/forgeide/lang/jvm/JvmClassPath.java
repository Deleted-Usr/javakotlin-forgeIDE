package com.willclay.forgeide.lang.jvm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Builds the runtime and compiler classpath shared by JVM languages. */
public final class JvmClassPath
{
    private static final String JAR_EXTENSION = ".jar";

    private JvmClassPath() { }

    /**
     * Places generated classes first, followed by every JAR below the library
     * root. Library discovery is recursive so projects may organise dependencies
     * into directories such as {@code libs/ui} and {@code libs/json}.
     */
    public static String discover(Path outputRoot, Path librariesRoot) throws IOException
    {
        List<String> entries = new ArrayList<>();
        entries.add(outputRoot.toString());

        if (!Files.isDirectory(librariesRoot)) return entries.getFirst();

        try (Stream<Path> libraries = Files.walk(librariesRoot))
        {
            libraries.filter(Files::isRegularFile)
                    .filter(JvmClassPath::isJar)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(Path::toString)
                    .forEach(entries::add);
        }

        return String.join(File.pathSeparator, entries);
    }

    private static boolean isJar(Path path)
    {
        Path fileName = path.getFileName();
        return fileName != null
                && fileName.toString().toLowerCase(Locale.ROOT).endsWith(JAR_EXTENSION);
    }
}
