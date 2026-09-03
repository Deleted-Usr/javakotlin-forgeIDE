package com.willclay.forgeide.actions;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;

/// The base class for every command the IDE can perform.
///
/// A [javax.swing.Action] is a command plus everything the UI needs to
/// display it: its name, its shortcut, its tooltip, its icon, and — the part
/// that matters most here — whether it is currently enabled. Handing the same
/// instance to a menu item and to a toolbar button means the two can never
/// disagree, because there is only one piece of state.
///
/// Subclasses override [#perform()] rather than
/// `actionPerformed(ActionEvent)`. The event carries nothing an action of
/// this kind needs — which button was pressed is exactly the thing the action
/// layer exists to stop caring about — so it is swallowed here.
///
/// TODO - add a constructor taking an [Icon] once there is an icon set,
///        and a `String key` for a resource bundle once anything needs
///        translating. Every action gets both for free at that point.
public abstract class ForgeAction extends AbstractAction
{
    protected ForgeAction(String name)
    {
        this(name, null, null);
    }

    protected ForgeAction(String name, KeyStroke shortcut)
    {
        this(name, shortcut, null);
    }

    /// @param name     the label shown in menus and on toolbar buttons
    /// @param shortcut the accelerator, or null for none — see [Shortcuts]
    /// @param tooltip  hover text, or null to reuse the name
    protected ForgeAction(String name, KeyStroke shortcut, String tooltip)
    {
        super(name);

        if (shortcut != null) putValue(ACCELERATOR_KEY, shortcut);
        putValue(SHORT_DESCRIPTION, tooltip == null ? name : tooltip);
    }

    /// The work the action actually does. Always called on the Event Dispatch Thread.
    protected abstract void perform();

    /// Runs this action from code rather than from a click — one action
    /// delegating to another, for example Save falling back to Save As.
    ///
    /// Disabled actions do nothing, which is what a user pressing a greyed-out
    /// button would get.
    public final void trigger()
    {
        if (isEnabled()) perform();
    }

    public final void setIcon(Icon icon)
    {
        putValue(SMALL_ICON, icon);
    }

    public final String getName()
    {
        return String.valueOf(getValue(NAME));
    }

    /// Final on purpose. Everything Swing invokes funnels through here, so
    /// subclasses cannot accidentally bypass the one entry point.
    @Override
    public final void actionPerformed(ActionEvent event)
    {
        perform();
    }
}
