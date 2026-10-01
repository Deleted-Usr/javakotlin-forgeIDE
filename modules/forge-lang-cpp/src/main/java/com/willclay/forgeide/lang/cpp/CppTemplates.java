package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.lang.api.templates.FileTemplate;
import com.willclay.forgeide.lang.api.templates.FileTemplates;
import com.willclay.forgeide.lang.api.templates.TemplateIcon;
import com.willclay.forgeide.lang.api.templates.TemplateOption;
import com.willclay.forgeide.lang.api.templates.TemplateRequest;

import java.util.List;
import java.util.Set;

/// The kinds of file offered by the "New C++ File" dialog.
///
/// C++ has no packages, so [TemplateRequest#packageName()] is ignored. Types
/// are written as header-only to keep each template a single file; splitting a
/// class into a `.hpp` and a `.cpp` is left to the programmer.
final class CppTemplates
{
    private static final String FINAL = "final";

    private static final String HEADER = ".hpp";
    private static final String SOURCE = CppSources.DEFAULT_EXTENSION;

    private static final TemplateIcon CLASS = TemplateIcon.type("C", "Objects.Blue");
    private static final TemplateIcon STRUCT = TemplateIcon.type("S", "Objects.Purple");
    private static final TemplateIcon HEADER_FILE = TemplateIcon.file("H", "Objects.Purple");

    static final FileTemplates ALL = new FileTemplates(
            "File",
            List.of(
                    new FileTemplate("Class", HEADER, CLASS, Set.of(FINAL), request -> """
                            #pragma once

                            class %s%s
                            {
                            public:
                                %s() = default;
                            };
                            """.formatted(request.name(), finalSuffix(request), request.name())),

                    new FileTemplate("Struct", HEADER, STRUCT, Set.of(FINAL), request -> """
                            #pragma once

                            struct %s%s
                            {
                            };
                            """.formatted(request.name(), finalSuffix(request))),

                    new FileTemplate("Header File", HEADER, HEADER_FILE, request -> """
                            #pragma once
                            """),

                    // No icon: a source file shows the language's own C++ file icon.
                    new FileTemplate("Source File", SOURCE, request -> """
                            #include <iostream>

                            int main()
                            {
                                std::cout << "Hello, World!" << std::endl;
                                return 0;
                            }
                            """)
            ),
            List.of(new TemplateOption(FINAL, "Make Final"))
    );

    private CppTemplates() { }

    private static String finalSuffix(TemplateRequest request)
    {
        return request.has(FINAL) ? " final" : "";
    }
}
