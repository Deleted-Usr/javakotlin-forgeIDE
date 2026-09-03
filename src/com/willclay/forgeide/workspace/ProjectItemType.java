package com.willclay.forgeide.workspace;

/// What a node in the project tree represents.
///
/// DIRECTORY and FILE are facts about the disk. PROJECT is not — it is a
/// directory the IDE has been told to treat as a root, which is why it gets its
/// own type: the tree draws it differently, and Close Project only applies to it.
public enum ProjectItemType
{
    PROJECT,
    DIRECTORY,
    FILE
}
