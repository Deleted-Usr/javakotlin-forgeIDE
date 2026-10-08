package com.willclay.forgeide.services.settings.theme;

import com.formdev.flatlaf.FlatDarkLaf;

/// The Tokyo Night colour scheme (its "Night" variant). FlatLaf loads the
/// matching TokyoNightLaf.properties resource alongside its standard dark defaults.
public final class TokyoNightLaf extends FlatDarkLaf
{
    @Override
    public String getName() { return "Tokyo Night"; }

    @Override
    public String getDescription() { return "Deep indigo surfaces with neon blue and purple accents"; }
}
