package com.willclay.forgeide.services.settings.theme;

import com.formdev.flatlaf.FlatDarkLaf;

/// The Catppuccin colour scheme (its dark "Mocha" flavour). FlatLaf loads the
/// matching CatppuccinMochaLaf.properties resource alongside its standard dark defaults.
public final class CatppuccinMochaLaf extends FlatDarkLaf
{
    @Override
    public String getName() { return "Catppuccin Mocha"; }

    @Override
    public String getDescription() { return "Soft pastel colours on a warm dark base"; }
}
