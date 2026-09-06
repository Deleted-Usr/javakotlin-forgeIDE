package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// Builds a project by invoking `g++` directly.
///
/// One command per executable: every translation unit is compiled and linked
/// in a single invocation rather than kept as object files. That costs a full
/// recompile whenever anything changes, which for a project of the size this
/// IDE is meant for is a second or two — and it buys the thing that matters
/// more, which is that the command echoed to the console is the whole build.
/// Object files, a link step and a staleness rule per file would each be
/// another place for the console to stop explaining what happened.
///
/// Whether a rebuild is needed at all is still answered, by
/// [CppSources#isUpToDate], so pressing Run twice does not compile twice.
final class GppBuilder implements CppBuilder
{
    @Override
    public String displayName()
    {
        return "g++";
    }

    @Override
    public boolean compile(Project project, List<Path> sourceFiles, Consumer<String> output)
            throws IOException, InterruptedException
    {
        CppSettings settings = CppSettings.from(project);
        Charset encoding = encodingOf(project);

        List<Path> entryPoints = new ArrayList<>();
        List<Path> others = new ArrayList<>();

        for (Path file : sourceFiles)
        {
            if (!CppSources.isSource(file)) continue;

            if (CppSources.declaresMain(file, encoding)) entryPoints.add(file);
            else others.add(file);
        }

        if (entryPoints.isEmpty() && others.isEmpty()) return true;

        for (Path entryPoint : entryPoints)
        {
            if (link(project, settings, entryPoint, List.of(), output) == null) return false;
        }

        // A file with no main of its own cannot become a program, so the useful
        // thing to do with it is tell the user whether it compiles. -fsyntax-only
        // stops after parsing, which makes this fast enough to sit in front of
        // every Run without anyone noticing it.
        return others.isEmpty() || checkSyntax(project, settings, others, output);
    }

    @Override
    public boolean build(Project project, Consumer<String> output) throws IOException, InterruptedException
    {
        CppSettings settings = CppSettings.from(project);
        Path sourceRoot = settings.sourceRoot(project);

        if (!Files.isDirectory(sourceRoot))
        {
            output.accept("Source directory does not exist: " + sourceRoot + System.lineSeparator());
            return false;
        }

        Charset encoding = encodingOf(project);
        List<Path> entryPoints = CppSources.allUnder(sourceRoot, CppSources::isSource).stream()
                .filter(file -> CppSources.declaresMain(file, encoding))
                .toList();

        if (entryPoints.isEmpty())
        {
            output.accept("No C++ source with a main function was found under "
                    + sourceRoot + System.lineSeparator());
            return false;
        }

        // Every executable, not just the one that would be run. Build means the
        // project, and a project with two programs in it has two things to break.
        for (Path entryPoint : entryPoints)
        {
            if (link(project, settings, entryPoint, List.of(), output) == null) return false;
        }

        return true;
    }

    @Override
    public boolean clean(Project project, Consumer<String> output) throws IOException
    {
        CppSettings settings = CppSettings.from(project);

        Path root = project.root().toAbsolutePath().normalize();
        Path out = settings.outputRoot(project);

        // The output directory is configurable, and "build" pointed at the
        // project root — or at C:\ — is a typo away. Deleting a tree is not
        // something to attempt on trust.
        if (!out.startsWith(root) || out.equals(root))
        {
            throw new IOException("Refusing to clean outside the project: " + out);
        }
        if (!Files.exists(out)) return true;

        try (Stream<Path> tree = Files.walk(out))
        {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }

        output.accept("Removed " + out + System.lineSeparator());
        return true;
    }

    @Override
    public Path prepareExecutable(
            Project project,
            Path entryPoint,
            List<String> extraArguments,
            Consumer<String> output) throws IOException, InterruptedException
    {
        CppSettings settings = CppSettings.from(project);

        // Said here rather than left to the linker. Running the file in the
        // editor is allowed to reach this with anything at all, and "undefined
        // reference to WinMain" is a poor way to learn that the file has no
        // main function in it.
        if (!CppSources.declaresMain(entryPoint, encodingOf(project)))
        {
            output.accept(entryPoint.getFileName() + " has no main function, so there is nothing to run."
                    + System.lineSeparator()
                    + "Run a file that declares one, or choose an entry point in Edit Configurations."
                    + System.lineSeparator());

            return null;
        }

        Path executable = CppSources.executableFor(
                settings.outputRoot(project), settings.sourceRoot(project), entryPoint);

        // Extra arguments always rebuild. Nothing records which flags the last
        // build used, so an executable that is newer than its sources still
        // might not be the executable these arguments describe, and silently
        // running the old one would make the field look broken.
        if (extraArguments.isEmpty() && isUpToDate(project, settings, executable))
        {
            output.accept("Up to date: " + executable + System.lineSeparator());
            return executable;
        }

        return link(project, settings, entryPoint, extraArguments, output);
    }

    // --- Internals --- //

    /// Compiles and links one executable.
    ///
    /// @return the executable, or `null` when `g++` reported a problem
    private Path link(
            Project project,
            CppSettings settings,
            Path entryPoint,
            List<String> extraArguments,
            Consumer<String> output) throws IOException, InterruptedException
    {
        Path sourceRoot = settings.sourceRoot(project);
        Path outputRoot = settings.outputRoot(project);
        Charset encoding = encodingOf(project);

        List<Path> units = CppSources.translationUnits(sourceRoot, entryPoint, encoding);
        Path executable = CppSources.executableFor(outputRoot, sourceRoot, entryPoint);

        Files.createDirectories(outputRoot);

        List<String> command = new ArrayList<>();
        command.add(settings.gpp().command());
        command.addAll(settings.gpp().toFlags());
        command.addAll(inputCharset(encoding));

        for (Path include : settings.includeRoots(project))
        {
            command.add("-I" + include);
        }

        // The source root is an include directory too, so a header sitting
        // beside its implementation is found without configuring anything.
        command.add("-I" + sourceRoot);

        command.addAll(settings.gpp().compilerArguments());

        for (Path unit : units) command.add(unit.toString());

        command.add("-o");
        command.add(executable.toString());

        // Libraries come after the objects that need them, which is the one
        // ordering rule the GNU linker genuinely enforces. The run
        // configuration's arguments come last of all, so they can both override
        // an earlier flag and add a library of their own.
        command.addAll(settings.gpp().linkerArguments());
        command.addAll(extraArguments);

        int status = CppProcesses.execute(command, project.workingDirectory(), output, null);

        return status == 0 ? executable : null;
    }

    /// Parses the given files and stops, reporting anything that will not
    /// compile without producing output for it.
    private boolean checkSyntax(
            Project project,
            CppSettings settings,
            List<Path> files,
            Consumer<String> output) throws InterruptedException
    {
        List<String> command = new ArrayList<>();
        command.add(settings.gpp().command());
        command.add("-fsyntax-only");
        command.addAll(settings.gpp().toFlags());
        command.addAll(inputCharset(encodingOf(project)));

        for (Path include : settings.includeRoots(project)) command.add("-I" + include);
        command.add("-I" + settings.sourceRoot(project));

        command.addAll(settings.gpp().compilerArguments());
        for (Path file : files) command.add(file.toString());

        return CppProcesses.execute(command, project.workingDirectory(), output, null) == 0;
    }

    private boolean isUpToDate(Project project, CppSettings settings, Path executable) throws IOException
    {
        List<Path> watched = new ArrayList<>(settings.includeRoots(project));
        watched.add(settings.sourceRoot(project));

        return CppSources.isUpToDate(executable, watched);
    }

    /// `-finput-charset`, but only when the project is not already in UTF-8.
    ///
    /// GCC assumes UTF-8 and every build of it can decode UTF-8, whereas the
    /// iconv support behind other encodings is a build-time option that some
    /// MinGW distributions leave out. Passing the flag only when it changes
    /// something keeps the common case away from that.
    private static List<String> inputCharset(Charset encoding)
    {
        return encoding.equals(StandardCharsets.UTF_8)
                ? List.of()
                : List.of("-finput-charset=" + encoding.name());
    }

    private static Charset encodingOf(Project project)
    {
        return project.configuration().fileHandling().encoding().charset();
    }
}
