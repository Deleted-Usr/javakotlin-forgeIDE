package com.willclay.forgeide.lang.api;

/**
 * Supplies plugin metadata and constructs a language implementation for ForgeIDE.
 * <p>
 * Implementations form the discovery boundary between the IDE and built-in or
 * external language plugins. They are intended to be registered as
 * {@link java.util.ServiceLoader} providers, so implementations should be cheap
 * to instantiate and must be usable through a public no-argument constructor.
 * Provider metadata describes the plugin itself; language-specific identity and
 * capabilities are exposed by the {@link Language} returned from
 * {@link #createLanguage()}.
 */
public interface LanguageProvider
{
    /**
     * Returns the stable, globally unique identifier for this plugin.
     * <p>
     * The identifier should be independent of the language identifier and should
     * not change between releases of the same plugin.
     *
     * @return the plugin identifier, for example {@code forge.java}
     */
    String pluginId();

    /**
     * Returns the version of the plugin implementation.
     *
     * @return a non-blank, displayable version string
     */
    String pluginVersion();

    /**
     * Creates the language implementation contributed by this plugin.
     * <p>
     * The returned object owns the language's lexer, project conventions, file
     * templates, and optional toolchain. Each invocation should return an
     * independently usable instance.
     *
     * @return a new language implementation; never {@code null}
     */
    Language createLanguage();
}
