package com.willclay.forgeide.compiler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Starts a child process, drains it, and hands its stdin back.
 * <p>
 * Lives here rather than in a toolchain because every toolchain needs it and
 * none of it is language-specific. The reasoning about chunked reads and merged
 * streams was written once for javac; a Python toolchain that re-derived it
 * would get it wrong.
 */
public final class ProcessRunner
{
    private static final int READ_BUFFER_SIZE = 4096;
    private static final AtomicReference<Process> currentProcess = new AtomicReference<>();

    private ProcessRunner() { }

    public static int execute(ProcessBuilder builder, Consumer<String> output, Consumer<Writer> onInputReady) throws IOException, InterruptedException
    {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        // One merged stream: simpler to drain, and errors keep their position
        // relative to the normal output instead of arriving in a clump.
        builder.redirectErrorStream(true);

        Process process = builder.start();
        if (!currentProcess.compareAndSet(null, process))
        {
            process.destroy();
            throw new IllegalStateException("A process is already running");
        }

        try
        {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            Writer input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
            if (onInputReady != null) onInputReady.accept(input);

            // Chunks, not lines. A BufferedReader hands back a line only once it
            // has seen the newline that ends it, so a prompt written with print()
            // would otherwise be held until the program's next println.
            try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
            {
                char[] buffer = new char[READ_BUFFER_SIZE];
                int count;

                while ((count = reader.read(buffer)) != -1)
                {
                    output.accept(new String(buffer, 0, count));
                }
            }
            finally
            {
                // The stream ends when the child exits, so nothing can be sent
                // after this point. Closing also releases the pipe on failure.
                try
                {
                    input.close();
                }
                catch (IOException ignored)
                {
                    // Already gone.
                }
            }

            return process.waitFor();
        }
        catch (IOException | InterruptedException | RuntimeException e)
        {
            process.destroy();
            throw e;
        }
        finally
        {
            currentProcess.compareAndSet(process, null);
        }
    }

    /** Requests termination of the process currently owned by the active task. */
    public static void stopCurrentProcess()
    {
        Process process = currentProcess.get();
        if (process != null && process.isAlive()) process.destroy();
    }
}
