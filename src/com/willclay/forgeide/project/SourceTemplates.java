package com.willclay.forgeide.project;

/**
 * Starter source the editor opens with. Separated from the UI so that adding
 * more templates later does not mean touching a Swing class.
 */
public final class SourceTemplates
{
    private SourceTemplates() { }

    /**
     * Must declare {@link ProjectPaths#MAIN_CLASS_NAME}: the Run button
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
                """.formatted(ProjectPaths.MAIN_CLASS_NAME);
    }

     public static String newClass()
     {
         // TODO - Swap the MAIN_CLASS_NAME string for the user-chosen name of the new java file
         return """
                import java.awt.*;
                import javax.swing.*;
                
                public class %s
                {
                
                }
                """.formatted(ProjectPaths.MAIN_CLASS_NAME);
     }
}
