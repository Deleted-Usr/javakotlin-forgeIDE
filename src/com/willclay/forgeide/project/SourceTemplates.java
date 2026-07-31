package com.willclay.forgeide.project;

/**
 * Starter source the editor opens with. Separated from the UI so that adding
 * more templates later does not mean touching a Swing class.
 */
public final class SourceTemplates
{
    private SourceTemplates() { }

    /**
     * Must declare {@link ProjectPaths#SCRATCH_CLASS_NAME}: the Run button
     * writes the editor's contents to that file and launches that class.
     */
    public static String scratchClass()
    {
        return """
                public class %s
                {
                    public static void main(String[] args)
                    {
                        System.out.println("Hello, World!");
                    }
                }
                """.formatted(ProjectPaths.SCRATCH_CLASS_NAME);
    }
}
