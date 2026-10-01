package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.lang.api.templates.FileTemplate;
import com.willclay.forgeide.lang.api.templates.FileTemplates;
import com.willclay.forgeide.lang.api.templates.TemplateIcon;
import com.willclay.forgeide.lang.api.templates.TemplateOption;
import com.willclay.forgeide.lang.api.templates.TemplateRequest;

import java.util.List;
import java.util.Set;

/// The kinds of file offered by the "New Java Class" dialog.
final class JavaTemplates
{
    private static final String FINAL = "final";
    private static final String ABSTRACT = "abstract";

    private static final String EXTENSION = JavaClassNames.EXTENSION;

    // IntelliJ's colours for each kind of type, so the dialog reads the same way.
    private static final TemplateIcon CLASS = TemplateIcon.type("C", "Objects.Blue");
    private static final TemplateIcon INTERFACE = TemplateIcon.type("I", "Objects.Green");
    private static final TemplateIcon RECORD = TemplateIcon.type("R", "Objects.Yellow");
    private static final TemplateIcon ENUM = TemplateIcon.type("E", "Objects.YellowDark");
    private static final TemplateIcon ANNOTATION = TemplateIcon.type("@", "Objects.Purple");
    private static final TemplateIcon EXCEPTION = TemplateIcon.type("!", "Objects.Red");

    static final FileTemplates ALL = new FileTemplates(
            "Class",
            List.of(
                    new FileTemplate("Class", EXTENSION, CLASS, Set.of(FINAL, ABSTRACT), request ->
                            header(request) + """
                            public %sclass %s
                            {
                            }
                            """.formatted(modifiers(request), request.name())),

                    new FileTemplate("Interface", EXTENSION, INTERFACE, request ->
                            header(request) + """
                            public interface %s
                            {
                            }
                            """.formatted(request.name())),

                    new FileTemplate("Record", EXTENSION, RECORD, request ->
                            header(request) + """
                            public record %s()
                            {
                            }
                            """.formatted(request.name())),

                    new FileTemplate("Enum", EXTENSION, ENUM, request ->
                            header(request) + """
                            public enum %s
                            {
                            }
                            """.formatted(request.name())),

                    new FileTemplate("Annotation", EXTENSION, ANNOTATION, request ->
                            header(request) + """
                            public @interface %s
                            {
                            }
                            """.formatted(request.name())),

                    new FileTemplate("Exception", EXTENSION, EXCEPTION, Set.of(FINAL, ABSTRACT), request ->
                            header(request) + """
                            public %sclass %s extends Exception
                            {
                                public %s(String message)
                                {
                                    super(message);
                                }
                            }
                            """.formatted(modifiers(request), request.name(), request.name())),

                    // A compact source file (Java 25) is always in the unnamed
                    // package, so it never gets a package line, even inside one.
                    new FileTemplate("Compact Source File", EXTENSION, request -> """
                            void main()
                            {
                                IO.println("Hello, World!");
                            }
                            """)
            ),
            List.of(
                    new TemplateOption(FINAL, "Make Final", Set.of(ABSTRACT)),
                    new TemplateOption(ABSTRACT, "Make Abstract", Set.of(FINAL))
            )
    );

    private JavaTemplates() { }

    private static String header(TemplateRequest request)
    {
        return request.hasPackage() ? "package " + request.packageName() + ";\n\n" : "";
    }

    private static String modifiers(TemplateRequest request)
    {
        if (request.has(ABSTRACT)) return "abstract ";
        if (request.has(FINAL)) return "final ";
        return "";
    }
}
