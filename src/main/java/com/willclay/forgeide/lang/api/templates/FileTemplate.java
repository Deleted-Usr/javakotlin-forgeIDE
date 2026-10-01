package com.willclay.forgeide.lang.api.templates;

import java.util.Objects;
import java.util.Set;

/// One kind of file a language can create: a Java interface, a Kotlin data
/// class, a C++ header. Each one becomes a button in the "New ..." dialog.
///
/// @param displayName      the button text, for example `Interface`
/// @param extension        the extension the created file gets, including the
///                         dot. Templates in one language can differ — C++
///                         headers are `.hpp` and sources `.cpp`.
/// @param icon             the button's icon, or `null` to use the language's
///                         own file icon
/// @param supportedOptions ids of the [TemplateOption]s that make sense for
///                         this kind. The rest are greyed out while it is chosen.
/// @param body             writes the file's starting text
public record FileTemplate(String displayName, String extension, TemplateIcon icon, Set<String> supportedOptions, Body body)
{
    /// Turns a request into file contents. A lambda is the usual way to write one.
    @FunctionalInterface
    public interface Body
    {
        String render(TemplateRequest request);
    }

    public FileTemplate
    {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(extension, "extension");
        Objects.requireNonNull(body, "body");
        supportedOptions = supportedOptions == null ? Set.of() : Set.copyOf(supportedOptions);
    }

    /// A template with an icon and no options.
    public FileTemplate(String displayName, String extension, TemplateIcon icon, Body body)
    {
        this(displayName, extension, icon, Set.of(), body);
    }

    /// A template that uses the language's file icon.
    public FileTemplate(String displayName, String extension, Set<String> supportedOptions, Body body)
    {
        this(displayName, extension, null, supportedOptions, body);
    }

    /// A template with the language's file icon and no options.
    public FileTemplate(String displayName, String extension, Body body)
    {
        this(displayName, extension, null, Set.of(), body);
    }

    public boolean supports(TemplateOption option)
    {
        return supportedOptions.contains(option.id());
    }

    public String render(TemplateRequest request)
    {
        return body.render(request);
    }
}
