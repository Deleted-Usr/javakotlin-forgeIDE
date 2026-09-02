package com.willclay.forgeide.lang.api.settings;

import javax.swing.JComponent;

/** One settings tab contributed by a project language. */
public interface LanguageSettingsPage
{
    String title();

    JComponent component();

    /** Returns the validated values currently shown by the page. */
    LanguageSettings getValues();
}
