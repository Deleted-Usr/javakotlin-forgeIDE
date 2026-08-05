package com.willclay.forgeide.workspace;

/**
 * What a node in the project tree represents.
 * <p>
 * DIRECTORY and FILE are facts about the disk. PROJECT is not — it is a
 * directory the IDE has been told to treat as a root, which is why it gets its
 * own type: the tree draws it differently, and Close Project only applies to it.
 */
public enum ProjectItemType
{
    /** The container the projects sit in. Unused while only one project can be open. */
    WORKSPACE,

    PROJECT,
    DIRECTORY,
    FILE
}
