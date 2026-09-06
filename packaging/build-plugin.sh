#!/usr/bin/env sh
#
# Compiles one ForgeIDE language module into a loadable plugin JAR.
#
# A language module is mixed Kotlin and Java, so it needs two compiler passes
# over a single output directory:
#
#   1. kotlinc, given the .kt AND .java sources. It parses the Java files for
#      symbol resolution but emits class files only for the Kotlin ones.
#   2. javac, given only the .java sources, with pass 1's output first on its
#      classpath so those files can see the Kotlin classes.
#
# The JAR deliberately does not bundle kotlin-stdlib, FlatLaf or Jackson.
# LanguagePluginLoader creates its URLClassLoader with the application class
# loader as parent, so the host already supplies all three.
#
# The PowerShell sibling, build-plugin.ps1, is the one to use on Windows.
#
# The defaults build the Kotlin module. Another module is named on the command
# line, for example:
#
#   ./packaging/build-plugin.sh --name forge-lang-cpp --plugin-id forge.cpp \
#       --language-id cpp --install

set -eu

NAME="forge-lang-kotlin"
VERSION="1.0"
PLUGIN_ID="forge.kotlin"
LANGUAGE_ID="kotlin"
API_VERSION="1"
HOST_CLASSES=""
RELEASE=""
JVM_TARGET=""
INSTALL="no"

usage()
{
    echo "Usage: $0 [--name NAME] [--version VERSION] [--plugin-id ID]"
    echo "          [--language-id ID] [--api-version N] [--host-classes DIR]"
    echo "          [--release N] [--jvm-target N] [--install]"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --name)          NAME="$2";          shift 2 ;;
        --version)       VERSION="$2";       shift 2 ;;
        --plugin-id)     PLUGIN_ID="$2";     shift 2 ;;
        --language-id)   LANGUAGE_ID="$2";   shift 2 ;;
        --api-version)   API_VERSION="$2";   shift 2 ;;
        --host-classes)  HOST_CLASSES="$2";  shift 2 ;;
        --release)       RELEASE="$2";       shift 2 ;;
        --jvm-target)    JVM_TARGET="$2";    shift 2 ;;
        --install)       INSTALL="yes";      shift   ;;
        -h|--help)       usage; exit 0 ;;
        *)               echo "Error: unknown option $1"; usage; exit 1 ;;
    esac
done

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

# ----------------------------------------------------------------- toolchain

if [ -n "${JAVA_HOME:-}" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAR="$JAVA_HOME/bin/jar"
else
    JAVAC=$(command -v javac || true)
    JAR=$(command -v jar || true)
fi

if [ -z "$JAVAC" ] || [ ! -x "$JAVAC" ] || [ -z "$JAR" ] || [ ! -x "$JAR" ]; then
    echo "Error: javac and jar were not found."
    echo "Set JAVA_HOME to a full JDK installation."
    exit 1
fi

# Kotlin is only needed by a module that actually contains Kotlin, so a missing
# installation stays non-fatal until pass 1 is about to run. A language plugin
# written entirely in Java builds without it.
if [ -n "${KOTLIN_HOME:-}" ]; then
    KOTLINC="$KOTLIN_HOME/bin/kotlinc"
else
    KOTLINC=$(command -v kotlinc || true)
fi

KOTLIN_LIB=""
if [ -n "$KOTLINC" ] && [ -x "$KOTLINC" ]; then
    KOTLIN_LIB=$(CDPATH= cd -- "$(dirname -- "$KOTLINC")/../lib" && pwd)
else
    KOTLINC=""
fi

# -------------------------------------------------------------------- layout

SRC="$ROOT/modules/$NAME/src"
OUT="$ROOT/build/modules/$NAME/classes"
ARGS_DIR="$ROOT/build/modules/$NAME"
JAR_PATH="$ROOT/dist/plugins/$NAME-$VERSION.jar"

if [ ! -d "$SRC" ]; then
    echo "Error: module sources were not found at $SRC."
    exit 1
fi

if [ -n "$HOST_CLASSES" ]; then
    HOST_OUT="$HOST_CLASSES"
elif [ -d "$ROOT/build/classes/forge-ide" ]; then
    HOST_OUT="$ROOT/build/classes/forge-ide"
else
    # IntelliJ's output is a stand-in until there is a build-host script.
    HOST_OUT="$ROOT/out/production/ForgeIDE"
fi

if [ ! -d "$HOST_OUT" ]; then
    echo "Error: compiled ForgeIDE classes were not found at $HOST_OUT."
    echo "Build the IDE first, or pass --host-classes."
    exit 1
fi

# kotlin-stdlib is needed to compile against, not to ship. Prefer a vendored
# copy so the build does not depend on whichever Kotlin is installed.
STDLIB=""
if [ -d "$ROOT/libs/kotlin" ]; then
    STDLIB=$(find "$ROOT/libs/kotlin" -name 'kotlin-stdlib*.jar' ! -name '*-sources.jar' | sort | head -n 1)
fi
if [ -z "$STDLIB" ] && [ -n "$KOTLIN_LIB" ] && [ -f "$KOTLIN_LIB/kotlin-stdlib.jar" ]; then
    STDLIB="$KOTLIN_LIB/kotlin-stdlib.jar"
fi

CLASSPATH="$HOST_OUT"
if [ -d "$ROOT/libs" ]; then
    for jar in $(find "$ROOT/libs" -name '*.jar' ! -name '*-sources.jar' | sort); do
        CLASSPATH="$CLASSPATH:$jar"
    done
fi
if [ -n "$STDLIB" ]; then
    CLASSPATH="$CLASSPATH:$STDLIB"
fi

rm -rf "$OUT"
mkdir -p "$OUT" "$ARGS_DIR"

KT_FILES=$(find "$SRC" -name '*.kt' | sort)
JAVA_FILES=$(find "$SRC" -name '*.java' | sort)

if [ -z "$KT_FILES" ] && [ -z "$JAVA_FILES" ]; then
    echo "Error: no Kotlin or Java sources were found under $SRC."
    exit 1
fi

echo "Module      : $NAME $VERSION"
echo "Host classes: $HOST_OUT"
echo "Kotlin      : $KOTLIN_LIB"
echo ""

# --------------------------------------------------------- pass 1: kotlinc

if [ -n "$KT_FILES" ]; then
    if [ -z "$KOTLINC" ]; then
        echo "Error: $NAME contains Kotlin sources but kotlinc was not found."
        echo "Set KOTLIN_HOME or put kotlinc on PATH."
        exit 1
    fi
    if [ -z "$STDLIB" ]; then
        echo "Error: $NAME contains Kotlin sources but kotlin-stdlib was not found."
        echo "Vendor it into libs/kotlin or set KOTLIN_HOME."
        exit 1
    fi

    KOTLINC_ARGS="$ARGS_DIR/kotlinc.args"
    : > "$KOTLINC_ARGS"

    if [ -n "$JVM_TARGET" ]; then
        printf '"%s"\n"%s"\n' "-jvm-target" "$JVM_TARGET" >> "$KOTLINC_ARGS"
    fi

    printf '"%s"\n"%s"\n' "-module-name" "$(echo "$NAME" | tr '-' '_')" >> "$KOTLINC_ARGS"
    printf '"%s"\n"%s"\n' "-classpath" "$CLASSPATH"                     >> "$KOTLINC_ARGS"
    printf '"%s"\n"%s"\n' "-d" "$OUT"                                   >> "$KOTLINC_ARGS"

    # The Java sources go to kotlinc too: it resolves symbols from them but
    # emits nothing for them.
    for file in $KT_FILES $JAVA_FILES; do
        printf '"%s"\n' "$file" >> "$KOTLINC_ARGS"
    done

    echo "kotlinc ..."
    "$KOTLINC" "@$KOTLINC_ARGS"
fi

# ----------------------------------------------------------- pass 2: javac

if [ -n "$JAVA_FILES" ]; then
    JAVAC_ARGS="$ARGS_DIR/javac.args"
    : > "$JAVAC_ARGS"

    if [ -n "$RELEASE" ]; then
        printf '"%s"\n"%s"\n' "--release" "$RELEASE" >> "$JAVAC_ARGS"
    fi

    printf '"%s"\n"%s"\n' "-encoding" "UTF-8" >> "$JAVAC_ARGS"
    # Pass 1's output comes first so the Java sources see the Kotlin classes.
    printf '"%s"\n"%s"\n' "-classpath" "$OUT:$CLASSPATH" >> "$JAVAC_ARGS"
    printf '"%s"\n"%s"\n' "-sourcepath" "$SRC" >> "$JAVAC_ARGS"
    # Without this javac silently compiles whatever it finds through
    # -sourcepath and scatters extra classes into the module output.
    printf '"%s"\n' "-implicit:none" >> "$JAVAC_ARGS"
    printf '"%s"\n"%s"\n' "-d" "$OUT" >> "$JAVAC_ARGS"

    for file in $JAVA_FILES; do
        printf '"%s"\n' "$file" >> "$JAVAC_ARGS"
    done

    echo "javac ..."
    "$JAVAC" "@$JAVAC_ARGS"
fi

# ------------------------------------------------------------- packaging

# Copies META-INF/services, which is the whole of plugin discovery. The
# manifest is generated below rather than copied, because .gitignore excludes
# committed MANIFEST.MF files.
if [ -d "$SRC/META-INF" ]; then
    mkdir -p "$OUT/META-INF"
    (cd "$SRC/META-INF" && find . -type f ! -name 'MANIFEST.MF' -exec sh -c '
        for file do
            mkdir -p "$1/$(dirname "$file")"
            cp "$file" "$1/$file"
        done
    ' sh "$OUT/META-INF" {} +)
fi

SERVICES="$OUT/META-INF/services/com.willclay.forgeide.lang.api.LanguageProvider"
if [ ! -f "$SERVICES" ]; then
    echo "Error: no LanguageProvider service file was found."
    echo "LanguagePluginLoader would not see this plugin."
    exit 1
fi

# No Class-Path attribute: the host supplies every shared dependency through
# the plugin loader's parent. The Forge-* attributes give bootstrap something
# to read back when reporting which plugins loaded.
MANIFEST="$ARGS_DIR/MANIFEST.MF"
cat > "$MANIFEST" <<EOF
Manifest-Version: 1.0
Created-By: packaging/build-plugin.sh
Forge-Plugin-Id: $PLUGIN_ID
Forge-Plugin-Version: $VERSION
Forge-Language-Id: $LANGUAGE_ID
Forge-Api-Version: $API_VERSION
EOF

mkdir -p "$(dirname "$JAR_PATH")"
rm -f "$JAR_PATH"

echo "jar ..."
"$JAR" --create --file "$JAR_PATH" --manifest "$MANIFEST" -C "$OUT" .

echo ""
echo "Built $JAR_PATH"

if [ "$INSTALL" = "yes" ]; then
    PLUGINS="$HOME/.forge/plugins"
    mkdir -p "$PLUGINS"
    cp "$JAR_PATH" "$PLUGINS/"
    echo "Installed to $PLUGINS"
fi
