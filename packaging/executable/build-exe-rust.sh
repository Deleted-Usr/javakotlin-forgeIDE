#!/usr/bin/env sh
#
# Builds the Rust ForgeIDE.exe launcher and copies it into dist/.
#
# native/rust-native is the Rust twin of the C++ launcher: it finds
# dist/runtime/bin/server/jvm.dll, starts the JVM through JNI and runs
# ForgeIDE's Main class. Cargo does all the work, including build.rs, which
# embeds forge.ico as the exe's icon.
#
# The launcher is Windows-only, so this script is for Git Bash or MSYS2 on
# Windows. The PowerShell sibling, build-exe-rust.ps1, does the same job.
#
# Both launchers produce a file called ForgeIDE.exe. Whichever one you build
# last is the one that ends up in dist/.
#
#   ./packaging/executable/build-exe-rust.sh
#   ./packaging/executable/build-exe-rust.sh --profile dev

set -eu

PROFILE="release"
CLEAN="no"
COPY="yes"

usage()
{
    echo "Usage: $0 [--profile release|dev] [--clean] [--no-copy]"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --profile)  PROFILE="$2"; shift 2 ;;
        --clean)    CLEAN="yes";  shift   ;;
        --no-copy)  COPY="no";    shift   ;;
        -h|--help)  usage; exit 0 ;;
        *)          echo "Error: unknown option $1"; usage; exit 1 ;;
    esac
done

case "$PROFILE" in
    release) FOLDER="release" ;;
    # Cargo names the dev profile's output folder "debug", for historical reasons.
    dev)     FOLDER="debug" ;;
    *)       echo "Error: --profile must be release or dev."; exit 1 ;;
esac

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) ;;
    *)
        echo "Error: the ForgeIDE launcher is a Windows program."
        echo "Run this script from Git Bash or MSYS2 on Windows."
        exit 1 ;;
esac

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
MANIFEST="$ROOT/native/rust-native/Cargo.toml"
DIST="$ROOT/dist"

# Prints the real command before running it, so the console shows exactly
# what happened.
run()
{
    echo "> $*"
    "$@"
}

CARGO=$(command -v cargo || true)
if [ -z "$CARGO" ]; then
    echo "Error: cargo was not found. Install Rust from https://rustup.rs."
    exit 1
fi

echo "Launcher: Rust ($PROFILE)"
echo "Crate   : $MANIFEST"
echo ""

if [ "$CLEAN" = "yes" ]; then
    run "$CARGO" clean --manifest-path "$MANIFEST"
fi

run "$CARGO" build --manifest-path "$MANIFEST" --profile "$PROFILE"

EXE="$ROOT/native/rust-native/target/$FOLDER/ForgeIDE.exe"
if [ ! -f "$EXE" ]; then
    echo "Error: the build finished but $EXE was not produced."
    exit 1
fi

echo ""
echo "Built $EXE"

if [ "$COPY" = "yes" ]; then
    mkdir -p "$DIST"
    cp "$EXE" "$DIST/ForgeIDE.exe"
    echo "Copied to $DIST/ForgeIDE.exe"
fi
