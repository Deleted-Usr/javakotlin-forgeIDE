package com.willclay.forgeide.ui.projects

import java.awt.BorderLayout
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import javax.swing.*

/** Runs project I/O off the EDT while a modal event loop keeps Swing responsive. */
object ProjectProgress {
    @JvmStatic
    @Throws(IOException::class)
    fun <T> run(parent: Window, title: String, task: Callable<T>): T {
        check(SwingUtilities.isEventDispatchThread())

        // Focus can still name a disposed dialog, or the unrelated startup splash.
        // Only use a showing window in this parent's ownership chain.
        val active = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        val owner = active?.takeIf { window ->
            window.isShowing && generateSequence(window) { it.owner }.any { it === parent }
        } ?: parent

        val progress = JDialog(owner, title, ModalityType.APPLICATION_MODAL).apply {
            defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
            isResizable           = false

            contentPane = JPanel(BorderLayout(0, 12)).apply {
                border = BorderFactory.createEmptyBorder(20, 24, 20, 24)

                add(JLabel("$title..."), BorderLayout.NORTH)
                add(JProgressBar().apply { isIndeterminate = true }, BorderLayout.CENTER)

                preferredSize = Dimension(320, 92)
            }

            pack()
            setLocationRelativeTo(owner)
        }

        val worker = object : SwingWorker<T, Void>() {
            override fun doInBackground(): T = task.call()
            override fun done()              = progress.dispose()
        }

        try {
            worker.execute()
            progress.isVisible = true

            return worker.get()
        }
        catch (exception: ExecutionException) {
            val cause = exception.cause

            if (cause is IOException) {
                throw cause
            }

            throw IOException(cause?.message ?: "Could not update project", cause)
        }
        catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Project operation interrupted", exception)
        }
        finally {
            progress.dispose()
        }
    }
}
