package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.lang.api.settings.LanguageSettings;
import com.willclay.forgeide.lang.jvm.JvmSettings;
import com.willclay.forgeide.workspace.Project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// The complete set of Java settings owned by the built-in Java language.
public record JavaSettings(
        JvmSettings jvm,
        int release,
        boolean previewFeatures,
        JavaCompilerBackend compilerBackend) implements LanguageSettings
{
    public static final String ID = "java";

    public JavaSettings
    {
        Objects.requireNonNull(jvm, "jvm");
        Objects.requireNonNull(compilerBackend, "compilerBackend");
        if (release < 8) throw new IllegalArgumentException("Java release must be at least 8.");
    }

    public static JavaSettings defaults()
    {
        return new JavaSettings(
                JvmSettings.defaults(),
                Runtime.version().feature(),
                false,
                JavaCompilerBackend.EXTERNAL_JAVAC);
    }

    public static JavaSettings from(Project project)
    {
        Map<String, Object> json = project.configuration().languageSettings().get(ID);
        if (json == null) return defaults();

        Object release = json.get("release");
        int releaseNumber = release instanceof Number number
                ? number.intValue()
                : defaults().release;
        if (releaseNumber < 8) releaseNumber = defaults().release;

        return new JavaSettings(
                JvmSettings.fromJson(json),
                releaseNumber,
                Boolean.TRUE.equals(json.get("previewFeatures")),
                JavaCompilerBackend.fromJson(json.get("compilerBackend")));
    }

    @Override
    public String languageId()
    {
        return ID;
    }

    @Override
    public Map<String, Object> toJson()
    {
        Map<String, Object> json = new LinkedHashMap<>(jvm.toJson());
        json.put("release", release);
        json.put("previewFeatures", previewFeatures);
        json.put("compilerBackend", compilerBackend.persistedName());
        return Collections.unmodifiableMap(json);
    }
}
