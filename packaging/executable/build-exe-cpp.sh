#!/usr/bin/env sh
#
# Builds the C++ ForgeIDE.exe launcher and copies it into dist/.
#
# The launcher (native/cpp-native) is a small Windows program that finds
# dist/runtime/bin/server/jvm.dll, starts the JVM through JNI and runs
# ForgeIDE's Main class. It is built with CMake and MinGW g++, and embeds the
# application icon through forge.rc.
#
# The launcher is Windows-only, so this script is for Git Bash or MSYS2 on
# Windows. The PowerShell sibling, build-exe-cpp.ps1, does the same job.
#
# CMake is a native Windows program, so it does not reliably find a compiler
# from a POSIX shell. This script looks for the tools itself (--mingw, then
# PATH, then CLion's bundled MinGW) and hands them to CMake explicitly.
#
#   ./packaging/executable/build-exe-cpp.sh
#   ./packaging/executable/build-exe-cpp.sh --config Debug --clean

set -eu

CONFIG="Release"
MINGW=""
CLEAN="no"
COPY="yes"

usage()
{
    echo "Usage: $0 [--config Release|Debug|RelWithDebInfo|MinSizeRel]"
    echo "          [--mingw BIN_DIR] [--clean] [--no-copy]"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --config)   CONFIG="$2"; shift 2 ;;
        --mingw)    MINGW="$2";  shift 2 ;;
        --clean)    CLEAN="yes"; shift   ;;
        --no-copy)  COPY="no";   shift   ;;
        -h|--help)  usage; exit 0 ;;
        *)          echo "Error: unknown option $1"; usage; exit 1 ;;
    esac
done

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) ;;
    *)
        echo "Error: the ForgeIDE launcher is a Windows program."
        echo "Run this script from Git Bash or MSYS2 on Windows."
        exit 1 ;;
esac

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
SOURCE="$ROOT/native/cpp-native"
BUILD_DIR="$ROOT/build/native/cpp-native"
DIST="$ROOT/dist"

# Prints the real command before running it, so the console shows exactly
# what happened.
run()
{
    echo "> $*"
    "$@"
}

# CMake is a Windows program and wants C:/... paths, not /c/....
win_path()
{
    if command -v cygpath > /dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}

# ----------------------------------------------------------------- toolchain

CMAKE=$(command -v cmake || true)
if [ -z "$CMAKE" ]; then
    echo "Error: cmake was not found. Install CMake and put it on PATH."
    exit 1
fi

CLION_BIN="$(win_path "${LOCALAPPDATA:-}")/Programs/CLion/bin"

if [ -n "$MINGW" ]; then
    MINGW_BIN="$MINGW"
elif command -v g++ > /dev/null 2>&1; then
    MINGW_BIN=$(dirname -- "$(command -v g++)")
else
    MINGW_BIN="$CLION_BIN/mingw/bin"
fi

GCC="$MINGW_BIN/gcc.exe"
GPP="$MINGW_BIN/g++.exe"
WINDRES="$MINGW_BIN/windres.exe"

for tool in "$GCC" "$GPP" "$WINDRES"; do
    if [ ! -f "$tool" ]; then
        echo "Error: $tool was not found."
        echo "Install MinGW (or CLion), or pass --mingw <bin directory>."
        exit 1
    fi
done

NINJA=$(command -v ninja || true)
if [ -z "$NINJA" ]; then
    NINJA="$CLION_BIN/ninja/win/x64/ninja.exe"
    if [ ! -f "$NINJA" ]; then
        echo "Error: ninja was not found. Install Ninja and put it on PATH."
        exit 1
    fi
fi

echo "Launcher : C++ ($CONFIG)"
echo "Compiler : $GPP"
echo "Build dir: $BUILD_DIR"
echo ""

# --------------------------------------------------------------------- build

if [ "$CLEAN" = "yes" ]; then
    rm -rf "$BUILD_DIR"
fi

run "$CMAKE" \
    -S "$(win_path "$SOURCE")" \
    -B "$(win_path "$BUILD_DIR")" \
    -G Ninja \
    "-DCMAKE_BUILD_TYPE=$CONFIG" \
    "-DCMAKE_MAKE_PROGRAM=$(win_path "$NINJA")" \
    "-DCMAKE_C_COMPILER=$(win_path "$GCC")" \
    "-DCMAKE_CXX_COMPILER=$(win_path "$GPP")" \
    "-DCMAKE_RC_COMPILER=$(win_path "$WINDRES")"

run "$CMAKE" --build "$(win_path "$BUILD_DIR")"

EXE="$BUILD_DIR/ForgeIDE.exe"
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
