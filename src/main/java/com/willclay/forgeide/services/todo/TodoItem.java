package com.willclay.forgeide.services.todo;

import java.nio.file.Path;

/// One TO-DO comment. `line` is 1-based, as people count lines; `text` has any
/// continuation lines joined on with spaces, ready to show in a single row.
public record TodoItem(Path file, int line, int column, String text) { }
