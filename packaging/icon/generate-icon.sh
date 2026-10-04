#!/usr/bin/env sh
#
# Regenerates the ForgeIDE application icon from ForgeIconGenerator.java.
#
# ForgeIconGenerator.java draws the icon with Java2D and writes:
#
#   - the PNGs the Swing app loads (src/main/resources/.../icons/app/)
#   - native/cpp-native/forge.ico, embedded in the C++ launcher
#
# Java 11 and later can run a single .java file directly, with no separate
# compile step, which is all this script does. It then copies forge.ico into
# native/rust-native so both launchers always carry the same icon.
#
# The generator writes paths relative to the repository root, so the script
# runs it from there whatever folder you call it from. Unlike the launcher
# builds, this works on any OS.
#
# Rebuild a launcher afterwards (packaging/executable/build-exe-*.sh) to put
# the new icon into ForgeIDE.exe.
#
#   ./packaging/icon/generate-icon.sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)

if [ -n "${JAVA_HOME:-}" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=$(command -v java || true)
fi

if [ -z "$JAVA" ] || [ ! -x "$JAVA" ]; then
    echo "Error: java was not found."
    echo "Set JAVA_HOME to a JDK (11 or newer)."
    exit 1
fi

cd "$ROOT"

echo "> $JAVA packaging/icon/ForgeIconGenerator.java"
"$JAVA" packaging/icon/ForgeIconGenerator.java

if [ ! -f native/cpp-native/forge.ico ]; then
    echo "Error: the generator finished but native/cpp-native/forge.ico was not produced."
    exit 1
fi

cp native/cpp-native/forge.ico native/rust-native/forge.ico
echo "Copied forge.ico to native/rust-native/forge.ico"
echo ""
echo "Done. Rebuild a launcher to embed the new icon in ForgeIDE.exe."
