package com.willclay.forgeide.lang.kotlin;

import com.willclay.forgeide.lang.api.settings.LanguageSettings;
import com.willclay.forgeide.lang.jvm.JvmSettings;
import com.willclay.forgeide.workspace.Project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** The complete set of Kotlin/JVM settings owned by the Kotlin language. */
public record KotlinSettings(
        JvmSettings jvm,
        String compilerCommand,
        String jvmTarget,
        String languageVersion,
        boolean progressiveMode) implements LanguageSettings
{
    public static final String ID = "kotlin";

    public KotlinSettings
    {
        Objects.requireNonNull(jvm, "jvm");
        compilerCommand = requireText(compilerCommand, "Kotlin compiler command");
        jvmTarget = requireText(jvmTarget, "Kotlin JVM target");
        languageVersion = requireText(languageVersion, "Kotlin language version");
    }

    public static KotlinSettings defaults()
    {
        String compiler = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")
                ? "kotlinc.bat"
                : "kotlinc";
        return new KotlinSettings(JvmSettings.defaults(), compiler,
                Integer.toString(Runtime.version().feature()), "2.4", false);
    }

    public static KotlinSettings from(Project project)
    {
        Map<String, Object> json = project.configuration().languageSettings().get(ID);
        if (json == null) return defaults();

        KotlinSettings defaults = defaults();
        return new KotlinSettings(
                JvmSettings.fromJson(json),
                text(json.get("compilerCommand"), defaults.compilerCommand),
                text(json.get("jvmTarget"), defaults.jvmTarget),
                text(json.get("languageVersion"), defaults.languageVersion),
                Boolean.TRUE.equals(json.get("progressiveMode")));
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
        json.put("compilerCommand", compilerCommand);
        json.put("jvmTarget", jvmTarget);
        json.put("languageVersion", languageVersion);
        json.put("progressiveMode", progressiveMode);
        return Collections.unmodifiableMap(json);
    }

    private static String text(Object value, String fallback)
    {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static String requireText(String value, String label)
    {
        value = Objects.requireNonNull(value, label).trim();
        if (value.isEmpty()) throw new IllegalArgumentException(label + " must not be blank.");
        return value;
    }
}
