#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUTPUT_DIR="$SCRIPT_DIR/../dist/runtime"

if [ -n "${JAVA_HOME:-}" ]; then
    JLINK="$JAVA_HOME/bin/jlink"
else
    JLINK=$(command -v jlink || true)
fi

if [ -z "$JLINK" ] || [ ! -x "$JLINK" ]; then
    echo "Error: jlink was not found."
    echo "Set JAVA_HOME to a full JDK installation."
    exit 1
fi

if [ -e "$OUTPUT_DIR" ]; then
    echo "Error: $OUTPUT_DIR already exists."
    echo "Remove it before generating a new runtime."
    exit 1
fi

"$JLINK" \
    --add-modules java.base,java.desktop,java.logging \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --output "$OUTPUT_DIR"

echo "Runtime created at: $OUTPUT_DIR"