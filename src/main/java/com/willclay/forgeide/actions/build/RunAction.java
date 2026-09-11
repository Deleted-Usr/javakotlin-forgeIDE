package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.actions.file.SaveAction;
import com.willclay.forgeide.actions.file.SaveAllAction;
import com.willclay.forgeide.lang.api.LaunchOptions;
import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.execution.RunTask;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.runconfig.BeforeLaunch;
import com.willclay.forgeide.workspace.runconfig.RunConfiguration;

import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/// Prepares and runs a target with its project's toolchain.
///
/// The target is whichever run configuration the toolbar has selected. With none
/// selected — the dropdown's "Current File" — this behaves as it always did and
/// runs whatever is in the editor.
public final class RunAction extends ForgeAction
{
    private final ActionContext context;
    private final SaveAction save;
    private final SaveAllAction saveAll;
    private final Consumer<RunTask> taskStarter;

    public RunAction(ActionContext context, SaveAction save, SaveAllAction saveAll, Consumer<RunTask> taskStarter)
    {
        super("Run", Shortcuts.menu(KeyEvent.VK_R), "Build and run the selected configuration");
        this.context = context;
        this.save = save;
        this.saveAll = saveAll;
        this.taskStarter = taskStarter;
    }

    @Override
    protected void perform()
    {
        Project project = context.getWorkspace().getProject();
        if (project == null) return;

        Optional<RunConfiguration> configuration = context.getRunConfigurationManager().active();

        Path target = configuration.isPresent()
                ? configuredTarget(project, configuration.get())
                : currentFileTarget(project);

        if (target == null) return; // already reported, or the user cancelled a save

        if (!project.isSourceFile(target))
        {
            // Which of these is wrong depends entirely on where the target came
            // from; telling someone to save an editor file they never chose is
            // what made this confusing.
            if (configuration.isPresent()) reportWrongEntryPoint(project, configuration.get(), target);
            else reportWrongSource(project);

            return;
        }

        Optional<Toolchain> selected = project.language().toolchain();
        if (selected.isEmpty())
        {
            Utils.showErrorMessage(context.getFrame(), project.language().displayName() + " projects cannot be run.");
            return;
        }

        LaunchOptions options = configuration.map(config -> optionsOf(project, config)).orElseGet(LaunchOptions::defaults);
        BeforeLaunch beforeLaunch = configuration.map(RunConfiguration::beforeLaunch).orElse(BeforeLaunch.COMPILE_TARGET);

        taskStarter.accept(RunTask.run(project, selected.get(), context.getConsole(), target, options, beforeLaunch));
    }

    /// A configuration names its own entry point, so the editor is only consulted
    /// for whether anything still needs saving.
    private Path configuredTarget(Project project, RunConfiguration configuration)
    {
        if (!saveModifiedFiles()) return null;

        return resolve(project, configuration.entryPoint());
    }

    /// What Run meant before configurations existed.
    private Path currentFileTarget(Project project)
    {
        EditorManager editor = context.getEditorManager();
        Path sourceFile = editor.getCurrentFile();

        if (sourceFile != null && !project.isSourceFile(sourceFile))
        {
            reportWrongSource(project);
            return null;
        }
        // An untitled current document must acquire a path even when automatic
        // saving before execution is disabled.
        if (sourceFile == null && !save.saveCurrent()) return null;

        if (!saveModifiedFiles()) return null;

        return editor.getCurrentFile();
    }

    private boolean saveModifiedFiles()
    {
        EditorManager editor = context.getEditorManager();

        return !context.getSettingsService().get().saving().saveBeforeBuild()
                || !editor.hasModifiedFiles()
                || saveAll.saveAll();
    }

    private static LaunchOptions optionsOf(Project project, RunConfiguration configuration)
    {
        return new LaunchOptions(
                configuration.runtimeOptions(),
                configuration.programArguments(),
                resolve(project, configuration.workingDirectory()),
                configuration.environment()
        );
    }

    /// A configuration stores paths relative to the project it belongs to.
    private static Path resolve(Project project, Path path)
    {
        Path resolved = path.isAbsolute() ? path : project.root().resolve(path);

        return resolved.toAbsolutePath().normalize();
    }

    private void reportWrongEntryPoint(Project project, RunConfiguration configuration, Path target)
    {
        Utils.showErrorMessage(context.getFrame(),
                "The run configuration \"" + configuration.name() + "\" does not point at a "
                        + project.language().displayName() + " source file:\n" + target
                        + "\n\nChoose its entry point again in Edit Configurations.");
    }

    private void reportWrongSource(Project project)
    {
        Utils.showErrorMessage(context.getFrame(), "Save a " + project.language().displayName() + " source file inside " + project.sourceRoot() + " before running.");
    }
}
