package com.willclay.forgeide.compiler;

import java.io.*;
import java.nio.charset.StandardCharsets;
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

    private ProcessRunner() { }

    public static int execute(ProcessBuilder builder, Consumer<String> output, Consumer<Writer> onInputReady)
            throws IOException, InterruptedException
    {
        // One merged stream: simpler to drain, and errors keep their position
        // relative to the normal output instead of arriving in a clump.
        builder.redirectErrorStream(true);

        Process process = builder.start();

        Writer input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        if (onInputReady != null) onInputReady.accept(input);

        // Chunks, not lines. A BufferedReader hands back a line only once it has
        // seen the newline that ends it, so a prompt written with print() would
        // sit in the reader until the program's next println — which is exactly
        // when it is least useful, because by then the answer has been typed.
        try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
        {
            char[] buffer = new char[READ_BUFFER_SIZE];
            int count;

            while ((count = reader.read(buffer)) != -1)
            {
                output.accept(new String(buffer, 0, count));
            }
        }
        catch (IOException e)
        {
            process.destroy();
            throw e;
        }
        finally
        {
            // The stream ends when the child exits, so nothing can be sent to it
            // after this point. Closing here also releases the pipe if the caller
            // forgot to.
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
}
