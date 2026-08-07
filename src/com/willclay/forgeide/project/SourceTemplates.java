package com.willclay.forgeide.project;

/**
 * Starter source the editor opens with. Separated from the UI so that adding
 * more templates later does not mean touching a Swing class.
 */
public final class SourceTemplates
{
    private static final String DEFAULT_CLASS_NAME = "Main";

    private SourceTemplates() { }

    /**
     * A small default class for a new, unsaved editor document.
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
                """.formatted(DEFAULT_CLASS_NAME);
    }

     public static String newClass()
     {
         return """
                import java.awt.*;
                import javax.swing.*;
                
                public class %s
                {
                
                }
                """.formatted(DEFAULT_CLASS_NAME);
     }

    /** Suggested filename when a new document is first saved into a project. */
    public static String defaultFileName()
    {
        return DEFAULT_CLASS_NAME + ".java";
    }
}
