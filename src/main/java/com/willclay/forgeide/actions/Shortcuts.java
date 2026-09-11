package com.willclay.forgeide.actions;

import javax.swing.KeyStroke;
import java.awt.Toolkit;
import java.awt.event.InputEvent;

/// Keyboard accelerators.
///
/// The modifier is asked for rather than hard-coded: `CTRL_DOWN_MASK`
/// would give a macOS user Ctrl+S in an environment where every other
/// application uses Command+S. The toolkit knows which one the platform expects.
public final class Shortcuts
{
    /// Ctrl on Windows and Linux, Command on macOS.
    private static final int MENU_MASK = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    private Shortcuts() { }

    /// e.g. Ctrl+S
    public static KeyStroke menu(int keyCode)
    {
        return KeyStroke.getKeyStroke(keyCode, MENU_MASK);
    }

    /// e.g. Ctrl+Shift+S
    public static KeyStroke menuShift(int keyCode)
    {
        return KeyStroke.getKeyStroke(keyCode, MENU_MASK | InputEvent.SHIFT_DOWN_MASK);
    }

    /// e.g. Ctrl+Alt+S
    public static KeyStroke menuAlt(int keyCode)
    {
        return KeyStroke.getKeyStroke(keyCode, MENU_MASK | InputEvent.ALT_DOWN_MASK);
    }

    /// An unmodified key, e.g. F5.
    public static KeyStroke plain(int keyCode)
    {
        return KeyStroke.getKeyStroke(keyCode, 0);
    }
}
