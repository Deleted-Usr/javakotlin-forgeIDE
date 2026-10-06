# Moving from Jackson to kotlinx.serialization

Forge is replacing Jackson with kotlinx.serialization, one JSON document at a time. Each document's Java records become
Kotlin data classes.

**Why:** kotlinx generates each serializer at compile time instead of inspecting classes by reflection while Forge runs.
A field that can't be saved becomes a build error rather than a failed save. The libraries are also much smaller:
684 KB, against 2.6 MB for Jackson.

## Progress

Each store chooses its own codec, so both libraries can run side by side until the last document has moved.

| Document                   | File                            | Store                                                      | Codec          |
|----------------------------|---------------------------------|------------------------------------------------------------|----------------|
| Run configurations         | `.forge/runConfigurations.json` | `RunConfigurationsStore`                                   | **kotlinx** ✅ |
| Project metadata           | `.forge/project.json`           | `ProjectMetadata`                                          | Jackson        |
| IDE session + IDE settings | `~/.forge/config/*.json`        | `ForgeApplication` **and** `ForgeBootstrap` (shared store) | Jackson        |

## The shared pieces (already done)

- `json/KotlinxJsonCodec.kt` is the drop-in replacement for `JacksonJsonCodec`. Its `Json { }` settings are chosen to
  behave like Jackson did: every property is written, unknown keys are ignored, and an explicit `null` falls back to the
  property's default.
- `json/PathSerializer.kt` stores a `Path` as a portable string and still reads the old `file:` URIs.
- `json/JsonValues.kt` holds two things for `languageSettings`. `JsonValues` converts plain Java maps to and from JSON
  trees. `JsonValueMapSerializer` lets a `Map<String, Any?>` of plain values be a property, so language plugins never
  see the JSON library.

## The recipe

Use `workspace/runconfig/RunConfigurations.kt` and `RunConfiguration.kt` as worked examples.

1. **Record → data class.** `public record Foo(int a, String b)` becomes:

   ```kotlin
   @JvmRecord      // compiles to a real Java record, so Java still calls foo.a(), not foo.getA()
   @Serializable   // the compiler plugin writes a serializer for this class
   data class Foo(val a: Int, val b: String)
   ```

2. **Paths.** Add `@file:UseSerializers(PathSerializer::class)` as the first line of the file, above `package`.

3. **Statics.** Constants go in a `companion object` as `const val` (Java sees a normal static field). Factory
   methods such as `defaults()` get `@JvmStatic` so Java can keep calling `Foo.defaults()`.

4. **"null means default" becomes a default value.** `x == null ? List.of() : x` in a compact constructor becomes
   `val x: List<String> = emptyList()`. That covers a missing key, and the codec's `coerceInputValues` covers an
   explicit `null`. This also removes the need for `Boolean` instead of `boolean`. `Editor.showMinimap` only used the
   boxed type so a missing value would be `null` rather than `false`. Now it can simply be
   `val showMinimap: Boolean = true`.

5. **`schemaVersion` never gets a default.** If it had one, a file written before versioning would quietly read as
   the current version. `SessionService` and `SettingsService` rely on that read *failing* so they can run their
   legacy migration.

6. **Checks go in `init`.** `requireSupportedVersion(...)` and other "this is invalid, refuse it" rules move into an
   `init { }` block, using Kotlin's `require(...)` where it fits. `init` runs on deserialization too, and a failure
   becomes the same `IOException` Jackson produced.

7. **Normalising needs a new home.** A Java compact constructor could *rewrite* its arguments (trim, clamp, copy). A
   Kotlin constructor cannot. Pick a home for each rewrite rule:
   - **Where values enter.** The run-configuration dialog now normalises paths itself, because nothing else needed it:
     `RunAction` already normalises again before using a path.
   - **A `normalized()` function** called once after reading, when hand-edited files must be fixed up. The editor's
     font size and tab width rules are the main example of this.
   - **Defensive copies.** A Kotlin `List` is read-only only from Kotlin. Java can still change the `ArrayList` it
     passed in, so Java callers should pass `List.copyOf(...)`. `RunConfigurationManager.commit` shows this.

8. **Swap the codec** in that document's store, compile, then open a project or restart Forge with an existing file.

## Annotation translations

| Jackson                                | kotlinx                                                                                |
|----------------------------------------|----------------------------------------------------------------------------------------|
| `@JsonProperty("name")`                | `@SerialName("name")`                                                                  |
| `@JsonAlias("projectName")`            | `@JsonNames("projectName")`, which needs `@OptIn(ExperimentalSerializationApi::class)` |
| enum constant `@JsonProperty("UTF-8")` | `@SerialName("UTF-8")` on the constant                                                 |

An enum with a constructor value converts directly:

```kotlin
@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class Encoding(val charset: Charset) {
    @SerialName("UTF-8") @JsonNames("UTF8") UTF8(StandardCharsets.UTF_8),
    // ...
    ;

    override fun toString(): String = charset.name()
}
```

`charset` is a constructor `val`, so Java still calls `encoding.getCharset()`, not `charset()`. `@JvmRecord` does not
apply to enums. Either update the callers or annotate the property with `@get:JvmName("charset")`.

## Gotchas found in the first conversion

- **A Kotlin property can't implement an abstract Java method.** For that reason `VersionedJsonDocument` no longer
  declares `schemaVersion()`. It is now a marker plus the `requireSupportedVersion` helper.
- **Kotlin classes can't be nested in a Java class.** `SessionService.LegacySession` and
  `SettingsService.LegacySettings` will need their own Kotlin files, or move into the Kotlin file of the document
  they migrate to.
- **Java sees Kotlin's `Map<String, Any?>` as `Map<String, ?>`.** You can read it but not `put` into it, which is
  what you want for an immutable document.
- **Kotlin checks nulls at the boundary.** A Java caller passing `null` for a non-null Kotlin parameter gets a
  `NullPointerException` immediately. That replaces the old `Objects.requireNonNull` lines.
- **Formatting changes once.** kotlinx pretty-prints with 4-space indents and `"key": value`. Each file is
  reformatted the first time Forge saves it, but its content stays the same.

## Per-document notes

### Project metadata

`ProjectConfiguration`, `FileHandling`, `Encoding`, `LineSeparatorPolicy`

- `projectName` is stored as `"name"`, and older files use `"projectName"`. Use `@SerialName("name")` plus
  `@JsonNames("projectName")`.
- `languageSettings: Map<String, @Serializable(with = JsonValueMapSerializer::class) Map<String, Any?>> = emptyMap()`
- The trim and "language must not be blank" rules go to `init` (`require`) and `withProjectName`, following step 7.
- `ProjectSettingsService` calls `current.schemaVersion()`. That keeps working because of `@JvmRecord`.

### IDE session and settings

`IDESessionConfiguration`, `WorkbenchLayout`, `IDESettingsConfiguration` and its five groups, `LegacySession`,
`LegacySettings`

- These two documents share one `JsonFileStore`, created in **both** `ForgeApplication` and `ForgeBootstrap`. Convert
  both documents before switching those two lines.
- `Editor` resets an out-of-range `fontSize` or `tabWidth` to its default. That is a step 7 "normalized()" rule, so
  call it in `SettingsService` after reading. Keep the reset-to-default behaviour rather than switching to
  `Math.clamp`, so files that already exist behave the same.
- Give every `Legacy*` property a default. Old files may lack any of them.

## Finishing up

Once the last store uses `KotlinxJsonCodec`:

1. Delete `JacksonJsonCodec.java` and the three Jackson lines in `build.gradle.kts`.
2. Remove the Jackson mentions in `LanguageSettings.java` and the `buildFatJar` comment.
3. Run `gradlew syncDistLibraries` so the exe launchers stop shipping Jackson.
4. Search the codebase for `jackson`. Nothing should be left.

A JUnit test source set would make step 8 of the recipe repeatable. That is a separate, larger improvement; the
`examples/` projects are real files to round-trip in the meantime.
