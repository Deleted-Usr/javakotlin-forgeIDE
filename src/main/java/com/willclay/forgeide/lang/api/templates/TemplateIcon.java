package com.willclay.forgeide.lang.api.templates;

import com.willclay.forgeide.lang.api.FileIconStyle;

import java.util.Objects;

/// The icon on a [FileTemplate]'s button in the "New ..." dialog.
///
/// Follows the IntelliJ convention: something that declares a *type* — a class,
/// an interface, an enum — is a round badge with a letter, while something that
/// is just a *file* — a header, a script — is the page-shaped file icon.
///
/// Like [FileIconStyle], this only describes the icon; the IDE paints it.
///
/// @param letter    one or two characters, for example `C` or `@`
/// @param colourKey a FlatLaf `UIManager` colour key such as `Objects.Blue`
public record TemplateIcon(Shape shape, String letter, String colourKey)
{
    public enum Shape
    {
        /// A filled circle — classes, interfaces, enums and other types.
        TYPE,
        /// A page with a folded corner — the same shape as the explorer's file icons.
        FILE
    }

    public TemplateIcon
    {
        Objects.requireNonNull(shape, "shape");
        letter = Objects.requireNonNullElse(letter, "");
        if (colourKey == null || colourKey.isBlank()) colourKey = FileIconStyle.DEFAULT_COLOUR;
    }

    public static TemplateIcon type(String letter, String colourKey)
    {
        return new TemplateIcon(Shape.TYPE, letter, colourKey);
    }

    public static TemplateIcon file(String letter, String colourKey)
    {
        return new TemplateIcon(Shape.FILE, letter, colourKey);
    }
}
