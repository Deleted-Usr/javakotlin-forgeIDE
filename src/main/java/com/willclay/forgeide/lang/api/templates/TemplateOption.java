package com.willclay.forgeide.lang.api.templates;

import java.util.Objects;
import java.util.Set;

/// One toggle in a language's "New ..." dialog, such as Java's "Make Final".
///
/// @param id       what a [FileTemplate] checks for in its [TemplateRequest]
/// @param label    the text on the toggle
/// @param excludes ids of options that cannot be combined with this one. Turning
///                 this option on turns those off — `abstract` and `final` can
///                 never both apply to a Java class.
public record TemplateOption(String id, String label, Set<String> excludes)
{
    public TemplateOption
    {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        excludes = excludes == null ? Set.of() : Set.copyOf(excludes);
    }

    public TemplateOption(String id, String label)
    {
        this(id, label, Set.of());
    }
}
