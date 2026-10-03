package com.willclay.forgeide.actions.view;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.ui.WorkbenchPanel;

import javax.swing.KeyStroke;
import java.util.function.Consumer;

/// Shows and hides one part of the workbench.
///
/// The tick in the View menu is not tracked by this class. Swing keeps an
/// Action's `SELECTED_KEY` and a JCheckBoxMenuItem's tick in step in both
/// directions, so the menu updates when [#setSelected] is called from code,
/// and `SELECTED_KEY` is already the new value by the time
/// [#perform()] runs after a click. One source of truth, no listener.
///
/// What to show or hide arrives as a callback rather than as a component,
/// because a split pane child cannot simply be made invisible — the divider
/// stays behind. [WorkbenchPanel] owns that
/// detail.
public final class ToggleViewAction extends ForgeAction
{
    private final Consumer<Boolean> apply;

    public ToggleViewAction(String name, KeyStroke shortcut, boolean visible, Consumer<Boolean> apply)
    {
        super(name, shortcut, "Show or hide the " + name.toLowerCase());

        this.apply = apply;
        putValue(SELECTED_KEY, visible);
    }

    public boolean isSelected()
    {
        return Boolean.TRUE.equals(getValue(SELECTED_KEY));
    }

    /// Toggles from code — the menu tick follows.
    public void setSelected(boolean selected)
    {
        putValue(SELECTED_KEY, selected);
        apply.accept(selected);
    }

    @Override
    protected void perform()
    {
        apply.accept(isSelected());
    }
}
