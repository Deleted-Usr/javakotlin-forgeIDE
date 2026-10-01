package com.willclay.forgeide.lang.api.templates;

import java.util.List;
import java.util.Objects;

/// Everything a language puts in its "New ..." dialog.
///
/// The dialog itself belongs to the IDE and is the same for every language; a
/// language only supplies this data. That keeps Swing out of language plugins
/// and makes adding a new kind of file a one-line change.
///
/// @param noun      what the dialog creates, used in its title and menu item —
///                  `Class` gives "New Java Class"
/// @param templates the kinds of file to choose between, in display order; the
///                  first is selected when the dialog opens
/// @param options   toggles shown beneath the kinds, possibly none
public record FileTemplates(String noun, List<FileTemplate> templates, List<TemplateOption> options)
{
    public FileTemplates
    {
        Objects.requireNonNull(noun, "noun");
        templates = List.copyOf(templates);
        options = options == null ? List.of() : List.copyOf(options);

        if (templates.isEmpty()) throw new IllegalArgumentException("At least one template is required.");
    }

    public FileTemplates(String noun, List<FileTemplate> templates)
    {
        this(noun, templates, List.of());
    }
}
