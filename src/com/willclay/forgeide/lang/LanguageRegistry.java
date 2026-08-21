package com.willclay.forgeide.lang;

import com.willclay.forgeide.lang.api.Language;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Immutable catalogue of languages installed in Forge.
 *
 * <p>The registry discovers languages; it does not track a mutable "current"
 * language. The open project is the sole owner of that choice.</p>
 */
public final class LanguageRegistry
{
    private static final Pattern LANGUAGE_ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");

    private final Map<String, Language> languagesById;

    public LanguageRegistry(Collection<? extends Language> languages)
    {
        Objects.requireNonNull(languages, "languages");

        Map<String, Language> indexed = new LinkedHashMap<>();
        for (Language language : languages)
        {
            Objects.requireNonNull(language, "language");

            String id = Objects.requireNonNull(language.id(), "language.id()");
            if (!LANGUAGE_ID.matcher(id).matches())
            {
                throw new IllegalArgumentException("Invalid language id '" + id + "'. Use lowercase alphanumeric segments separated by '.', '_', or '-'.");
            }
            if (indexed.putIfAbsent(id, language) != null)
            {
                throw new IllegalArgumentException("Duplicate language id: " + id);
            }
        }

        if (indexed.isEmpty())
        {
            throw new IllegalArgumentException("At least one language must be registered.");
        }

        languagesById = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
    }

    /** Returns languages in their configured display order. */
    public List<Language> languages()
    {
        return List.copyOf(languagesById.values());
    }

    public Optional<Language> find(String id)
    {
        return Optional.ofNullable(languagesById.get(id));
    }
}
