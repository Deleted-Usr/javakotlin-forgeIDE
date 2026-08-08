package com.willclay.forgeide.lang;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable catalogue of languages installed in Forge.
 *
 * <p>The registry discovers languages; it does not track a mutable "current"
 * language. The open project is the sole owner of that choice.</p>
 */
public final class LanguageRegistry
{
    private final Map<String, Language> languagesById;

    public LanguageRegistry(Collection<? extends Language> languages)
    {
        Objects.requireNonNull(languages, "languages");

        Map<String, Language> indexed = new LinkedHashMap<>();
        for (Language language : languages)
        {
            Objects.requireNonNull(language, "language");

            if (language.id().isBlank())
            {
                throw new IllegalArgumentException("A language id cannot be blank.");
            }
            if (indexed.putIfAbsent(language.id(), language) != null)
            {
                throw new IllegalArgumentException("Duplicate language id: " + language.id());
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
