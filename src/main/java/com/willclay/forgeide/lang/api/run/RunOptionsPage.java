package com.willclay.forgeide.lang.api.run;

import javax.swing.JComponent;
import java.util.Map;

/// What one language adds to the run configuration dialog.
///
/// Every run configuration has the same core: an entry point, program
/// arguments, a working directory and environment variables. Two things differ
/// between languages, and this page is where a language says so:
///
///  - **What the runtime options are.** The same list means "VM options" to the
///    JVM, compiler flags to C++ and Cargo flags to Rust, so the language names
///    and explains it.
///  - **Choices only it has**, such as Rust's release profile. These are shown
///    in a section of their own, and saved with the configuration under the
///    language's id (see `RunConfiguration.languageOptions`).
///
/// Only the two label methods are required. A language that has nothing to add
/// beyond a better name for the runtime options leaves the rest alone.
///
/// The dialog creates one page and reuses it for every configuration it shows,
/// calling [#load(Map)] each time the selection changes.
public interface RunOptionsPage
{
    /// The heading of the language's section, for example "Rust".
    String title();

    /// What the dialog calls the runtime options for this language, for example
    /// "VM options" or "Cargo options".
    String runtimeOptionsLabel();

    /// One plain-English sentence on where the runtime options end up. Shown
    /// under the field; empty for none.
    default String runtimeOptionsHint()
    {
        return "";
    }

    /// The language's own controls, or null when it has none and the dialog
    /// should show no section for it.
    default JComponent component()
    {
        return null;
    }

    /// Asks to be told whenever a value on the page changes, so the dialog's
    /// command preview can follow along as the user clicks.
    ///
    /// Optional, but a page with controls of its own should call the listener
    /// from each of them; without it, the preview only catches up on the next
    /// change somewhere else in the dialog.
    default void addChangeListener(Runnable listener) { }

    /// Shows a configuration's saved values. An empty map means a configuration
    /// that has never had any, so the page shows its defaults.
    default void load(Map<String, Object> options) { }

    /// The values currently shown, in the same plain JSON-friendly form as
    /// `LanguageSettings.toJson()`: strings, numbers, booleans, lists and maps.
    ///
    /// @throws IllegalArgumentException when a value is invalid; the message is
    ///                                  shown to the user
    default Map<String, Object> getValues()
    {
        return Map.of();
    }
}
