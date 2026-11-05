package com.willclay.forgeide.services.linecount

import com.willclay.forgeide.workspace.Project
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.Timer

/**
 * Keeps the project's line count current. Counts run on a background thread; results are published, and listeners
 * told, on the EDT.
 *
 * Every refresh recounts the whole project rather than patching in one file's change. A single total is simple to get
 * right, and a file created or deleted is handled the same way as a file saved. The cost is rereading the project, so
 * changes wait for a short pause: Save All or a Git checkout then only causes one recount.
 *
 * Call [projectChanged] on the EDT. [filesChanged] may be called from any thread.
 */
class LineCountService(private val counter: LineCounter)
{
    private val listeners = mutableListOf<Runnable>()

    private val refreshTimer = Timer(REFRESH_DELAY_MS) { startCount() }.apply { isRepeats = false }

    private var project: Project? = null
    private var generation = 0 // bumped on every count, so only the newest result is kept

    /** The latest result, or null if there is none: no project is open, or the first count is still running. */
    var counts: LineCounts? = null
        private set

    fun projectChanged(project: Project?) {
        val sameProject = project != null && project.root() == this.project?.root()

        this.project = project
        refreshTimer.stop()

        // Reconfiguring the open project keeps its old number on screen until the new one arrives.
        if (!sameProject) {
            publish(null)
        }

        startCount()
    }

    /** Something at or under [path] was saved, created or deleted. */
    fun filesChanged(path: Path) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { filesChanged(path) } // the file watcher calls from its own thread
            return
        }

        val root = project?.root() ?: return

        if (path.startsWith(root)) {
            refreshTimer.restart()
        }
    }

    private fun startCount() {
        val myGeneration = ++generation
        val current      = project ?: return // a null project has nothing to count, but must still outdate older counts

        object : SwingWorker<LineCounts, Void>() {
            override fun doInBackground(): LineCounts = counter.countProject(current)

            override fun done() {
                // Back on the EDT
                if (myGeneration != generation) {
                    return // a newer count has replaced this one
                }

                try {
                    publish(get())
                }
                catch (failed: ExecutionException) {
                    System.err.println("Could not count lines of code (${failed.cause})")
                    publish(null)
                }
            }
        }.execute()
    }

    /** The only place [counts] changes, so listeners can never miss an update. */
    private fun publish(next: LineCounts?) {
        counts = next
        listeners.toList().forEach { it.run() }
    }

    fun addChangeListener(listener: Runnable) {
        listeners.add(listener)
    }

    private companion object {
        const val REFRESH_DELAY_MS = 500
    }
}
