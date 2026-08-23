package com.willclay.forgeide.workspace;

import java.nio.file.Path;

/**
 * Told that the contents of a directory are no longer what the listener last
 * saw — not what changed, only where.
 * <p>
 * Deliberately that vague. A precise event ("Player.java was created") has to
 * be right, and file system notifications are not reliable enough for that: a
 * rename arrives as a delete and a create, a move between watched directories
 * arrives as two unrelated events, and a fast editor writing a temp file
 * produces three. "Something under here changed, look again" is a claim that is
 * always true, and re-reading one directory is cheap.
 * <p>
 * Notifications may arrive on the watcher's thread, so anything touching Swing
 * has to hop to the Event Dispatch Thread itself.
 */
@FunctionalInterface
public interface WorkspaceListener
{
    void directoryChanged(Path directory);
    default void configurationChanged() { }
}
