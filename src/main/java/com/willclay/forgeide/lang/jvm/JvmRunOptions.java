package com.willclay.forgeide.lang.jvm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The JVM's own run configuration choices, shared by Java and Kotlin.
///
/// Kept as a record so a toolchain reads `enableAssertions()` rather than
/// digging through the saved map itself. [#from(Map)] and [#toJson()] are the
/// only places that know how it is saved.
///
/// @param enableAssertions whether `assert` statements run. The JVM skips them
///                         unless it is started with `-ea`, which surprises
///                         almost everyone the first time they write one.
public record JvmRunOptions(boolean enableAssertions)
{
    private static final String ENABLE_ASSERTIONS = "enableAssertions";

    public static JvmRunOptions defaults()
    {
        return new JvmRunOptions(false);
    }

    /// Reads the options a [JvmRunOptionsPage] saved; anything missing keeps its default.
    public static JvmRunOptions from(Map<String, Object> options)
    {
        return new JvmRunOptions(Boolean.TRUE.equals(options.get(ENABLE_ASSERTIONS)));
    }

    public Map<String, Object> toJson()
    {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put(ENABLE_ASSERTIONS, enableAssertions);

        return json;
    }

    /// The JVM flags these options add, in the form `java` takes them.
    public List<String> jvmArguments()
    {
        return enableAssertions ? List.of("-ea") : List.of();
    }
}
