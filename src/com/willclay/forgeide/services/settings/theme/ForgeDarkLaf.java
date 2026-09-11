package com.willclay.forgeide.services.settings.theme;

import com.formdev.flatlaf.FlatDarkLaf;

/// Forge's dark appearance. FlatLaf loads the matching ForgeDarkLaf.properties
/// resource alongside its standard dark defaults, keeping styling out of views.
public final class ForgeDarkLaf extends FlatDarkLaf
{
    @Override
    public String getName() { return "Forge Dark"; }

    @Override
    public String getDescription() { return "Charcoal surfaces with a warm copper accent"; }
}
