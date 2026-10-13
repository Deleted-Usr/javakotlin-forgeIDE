#!/usr/bin/env sh
#
# Regenerates the ForgeIDE application icon from forge_icon_generator.kts.
#
# forge_icon_generator.kts draws the icon with Java2D and writes:
#
#   - the PNGs the Swing app loads (src/main/resources/.../icons/app/)
#   - forge.ico in native/cpp-native and native/rust-native for both launchers
#
# Requires a JDK and the Kotlin command-line compiler. Set KOTLIN_HOME to the
# compiler installation or put kotlinc on PATH.
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

if [ -n "${KOTLIN_HOME:-}" ]; then
    COMPILER="$KOTLIN_HOME/bin/kotlinc"
else
    COMPILER=$(command -v kotlinc || true)
fi

if [ -z "$COMPILER" ] || [ ! -x "$COMPILER" ]; then
    echo "Error: kotlinc was not found."
    echo "Set KOTLIN_HOME or add its bin directory to PATH."
    exit 1
fi

cd "$ROOT"

echo "> $COMPILER -script packaging/icon/forge_icon_generator.kts"
"$COMPILER" -script packaging/icon/forge_icon_generator.kts

if [ ! -f native/cpp-native/forge.ico ]; then
    echo "Error: the generator finished but native/cpp-native/forge.ico was not produced."
    exit 1
fi

if [ ! -f native/rust-native/forge.ico ]; then
    echo "Error: the generator finished but native/rust-native/forge.ico was not produced."
    exit 1
fi
echo ""
echo "Done. Rebuild a launcher to embed the new icon in ForgeIDE.exe."
