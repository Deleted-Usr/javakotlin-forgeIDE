#Requires -Version 5.1

<#
.SYNOPSIS
    Compiles one ForgeIDE language module into a loadable plugin JAR.

.DESCRIPTION
    A language module is mixed Kotlin and Java, so it needs two compiler
    passes over a single output directory:

      1. kotlinc, given the .kt AND .java sources. It parses the Java files
         for symbol resolution but emits class files only for the Kotlin
         ones.
      2. javac, given only the .java sources, with pass 1's output first on
         its classpath so those files can see the Kotlin classes.

    Running kotlinc first works regardless of which language references the
    other, so it keeps working if the module later grows a Java class that
    calls into Kotlin.

    The JAR deliberately does NOT bundle kotlin-stdlib, FlatLaf or Jackson.
    LanguagePluginLoader creates its URLClassLoader with the application
    class loader as parent, so the host already supplies all three. A second
    copy of kotlin-stdlib in a child loader is at best ignored and at worst
    produces ClassCastExceptions between identically named types.

.PARAMETER Name
    Module directory under modules/.

.PARAMETER HostClasses
    Compiled ForgeIDE classes to compile against. Defaults to
    build/classes/forge-ide, falling back to IntelliJ's out/production/ForgeIDE.

.PARAMETER Release
    Optional javac --release value. Left unset by default: the plugin should
    be built by the same JDK that built the host, and pinning an older
    release makes javac reject the host's newer class files.

.PARAMETER JvmTarget
    Optional kotlinc -jvm-target value. Unset by default, matching how the
    host's own Kotlin sources are currently compiled.

.PARAMETER Install
    Copy the finished JAR into ~/.forge/plugins.

.EXAMPLE
    .\packaging\build-plugin.ps1 -Install

.EXAMPLE
    .\packaging\build-plugin.ps1 -Name forge-lang-cpp -PluginId forge.cpp `
        -LanguageId cpp -Install
#>

[CmdletBinding()]
param(
    [string] $Name        = "forge-lang-kotlin",
    [string] $Version     = "1.0",
    [string] $PluginId    = "forge.kotlin",
    [string] $LanguageId  = "kotlin",
    [string] $ApiVersion  = "1",
    [string] $HostClasses = "",
    [string] $Release     = "",
    [string] $JvmTarget   = "",
    [switch] $Install
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

function Join-Root([string] $relative)
{
    return ((Join-Path $root $relative) -replace '\\', '/')
}

# javac and kotlinc argument files treat a backslash as an escape character
# inside quoted entries, so every path written to one must use forward
# slashes. Writing them without a BOM matters for the same reason it matters
# for the manifest: the tools read these files as plain UTF-8.
function Write-Utf8NoBom([string] $path, [string[]] $lines)
{
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, (($lines -join "`r`n") + "`r`n"), $encoding)
}

function Write-ArgFile([string] $path, [string[]] $arguments)
{
    Write-Utf8NoBom $path ($arguments | ForEach-Object { '"' + $_ + '"' })
}

# ---------------------------------------------------------------- toolchain

if ($env:JAVA_HOME)
{
    $javac = (Join-Path $env:JAVA_HOME "bin/javac.exe")
    $jar   = (Join-Path $env:JAVA_HOME "bin/jar.exe")
}
else
{
    $javacCommand = Get-Command javac -ErrorAction SilentlyContinue
    $jarCommand   = Get-Command jar   -ErrorAction SilentlyContinue
    if ($null -eq $javacCommand -or $null -eq $jarCommand)
    {
        throw "javac and jar were not found. Set JAVA_HOME to a full JDK installation."
    }
    $javac = $javacCommand.Source
    $jar   = $jarCommand.Source
}

if (-not (Test-Path $javac)) { throw "javac was not found at $javac." }
if (-not (Test-Path $jar))   { throw "jar was not found at $jar." }

# Kotlin is only needed by a module that actually contains Kotlin, so a missing
# installation stays non-fatal until pass 1 is about to run. A language plugin
# written entirely in Java builds without it.
$kotlinHome = $env:KOTLIN_HOME
if (-not $kotlinHome)
{
    $kotlincCommand = Get-Command kotlinc.bat -ErrorAction SilentlyContinue
    if ($null -ne $kotlincCommand)
    {
        $kotlinHome = Split-Path -Parent (Split-Path -Parent $kotlincCommand.Source)
    }
}

$kotlinc = $null
if ($kotlinHome)
{
    $kotlincPath = (Join-Path $kotlinHome "bin/kotlinc.bat")
    if (Test-Path $kotlincPath) { $kotlinc = $kotlincPath }
}

# ------------------------------------------------------------------- layout

$src     = Join-Root "modules/$Name/src/main"
$out     = Join-Root "build/modules/$Name/classes"
$argsDir = Join-Root "build/modules/$Name"
$jarPath = Join-Root "dist/plugins/$Name-$Version.jar"

if (-not (Test-Path $src)) { throw "Module sources were not found at $src." }

if ($HostClasses)
{
    $hostOut = Join-Root $HostClasses
}
else
{
    $hostOut = Join-Root "build/classes/forge-ide"
    if (-not (Test-Path $hostOut))
    {
        # IntelliJ's output is a perfectly good stand-in until there is a
        # build-host script to sit alongside this one.
        $hostOut = Join-Root "out/production/ForgeIDE"
    }
}

if (-not (Test-Path $hostOut))
{
    throw "Compiled ForgeIDE classes were not found at $hostOut. Build the IDE first, or pass -HostClasses."
}

# kotlin-stdlib is needed to COMPILE against, not to ship. Prefer a vendored
# copy so the build does not depend on whichever Kotlin is installed.
$stdlib = $null
$vendored = Join-Root "libs/kotlin"
if (Test-Path $vendored)
{
    $found = Get-ChildItem $vendored -Filter "kotlin-stdlib*.jar" -File |
             Where-Object { $_.Name -notlike "*-sources.jar" } |
             Select-Object -First 1
    if ($null -ne $found) { $stdlib = ($found.FullName -replace '\\', '/') }
}
if ($null -eq $stdlib -and $kotlinHome)
{
    $stdlibPath = ((Join-Path $kotlinHome "lib/kotlin-stdlib.jar") -replace '\\', '/')
    if (Test-Path $stdlibPath) { $stdlib = $stdlibPath }
}

$libs = @()
$libsDir = Join-Root "libs"
if (Test-Path $libsDir)
{
    $libs = @(Get-ChildItem $libsDir -Recurse -Filter *.jar -File |
              Where-Object { $_.Name -notlike "*-sources.jar" } |
              ForEach-Object { $_.FullName -replace '\\', '/' })
}

$classpath = ((@($hostOut) + $libs + @($stdlib) | Where-Object { $_ } | Select-Object -Unique) -join ';')

$ktFiles   = @(Get-ChildItem $src -Recurse -Filter *.kt   -File | ForEach-Object { $_.FullName -replace '\\', '/' })
$javaFiles = @(Get-ChildItem $src -Recurse -Filter *.java -File | ForEach-Object { $_.FullName -replace '\\', '/' })

if ($ktFiles.Count -eq 0 -and $javaFiles.Count -eq 0)
{
    throw "No Kotlin or Java sources were found under $src."
}

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out     | Out-Null
New-Item -ItemType Directory -Force -Path $argsDir | Out-Null

Write-Output "Module      : $Name $Version"
Write-Output "Host classes: $hostOut"
if ($ktFiles.Count -gt 0) { Write-Output "Kotlin      : $kotlinHome" }
Write-Output "Sources     : $($ktFiles.Count) Kotlin, $($javaFiles.Count) Java"
Write-Output ""

# ------------------------------------------------------- pass 1: kotlinc

if ($ktFiles.Count -gt 0)
{
    if ($null -eq $kotlinc)
    {
        throw "$Name contains Kotlin sources but kotlinc was not found. Set KOTLIN_HOME or put kotlinc on PATH."
    }
    if ($null -eq $stdlib)
    {
        throw "$Name contains Kotlin sources but kotlin-stdlib was not found. Vendor it into libs/kotlin or set KOTLIN_HOME."
    }

    $kotlincArguments = @()
    if ($JvmTarget) { $kotlincArguments += @("-jvm-target", $JvmTarget) }
    $kotlincArguments += @(
        "-module-name", ($Name -replace '-', '_'),
        "-classpath", $classpath,
        "-d", $out
    )
    # The Java sources go to kotlinc too: it resolves symbols from them but
    # emits nothing for them.
    $kotlincArguments += $ktFiles
    $kotlincArguments += $javaFiles

    $kotlincArgFile = (Join-Path $argsDir "kotlinc.args")
    Write-ArgFile $kotlincArgFile $kotlincArguments

    Write-Output "kotlinc ..."
    & $kotlinc "@$kotlincArgFile"
    if ($LASTEXITCODE -ne 0) { throw "kotlinc failed with exit code $LASTEXITCODE." }
}

# --------------------------------------------------------- pass 2: javac

if ($javaFiles.Count -gt 0)
{
    $javacArguments = @()
    if ($Release) { $javacArguments += @("--release", $Release) }
    $javacArguments += @(
        "-encoding", "UTF-8",
        # Pass 1's output comes first so the Java sources see the Kotlin classes.
        "-classpath", "$out;$classpath",
        "-sourcepath", (Join-Path $src "java"),
        # Without this javac silently compiles whatever it finds through
        # -sourcepath and scatters extra classes into the module output.
        "-implicit:none",
        "-d", $out
    )
    $javacArguments += $javaFiles

    $javacArgFile = (Join-Path $argsDir "javac.args")
    Write-ArgFile $javacArgFile $javacArguments

    Write-Output "javac ..."
    & $javac "@$javacArgFile"
    if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE." }
}

# ------------------------------------------------------------- packaging

# Copies META-INF/services, which is the whole of plugin discovery. The
# manifest is generated below rather than copied, because .gitignore excludes
# committed MANIFEST.MF files.
$moduleMeta = (Join-Path $src "resources/META-INF")
if (Test-Path $moduleMeta)
{
    $targetMeta = (Join-Path $out "META-INF")
    New-Item -ItemType Directory -Force -Path $targetMeta | Out-Null
    Get-ChildItem $moduleMeta -Recurse -File |
        Where-Object { $_.Name -ne "MANIFEST.MF" } |
        ForEach-Object {
            $relative = $_.FullName.Substring($moduleMeta.Length).TrimStart('\', '/')
            $destination = (Join-Path $targetMeta $relative)
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
            Copy-Item $_.FullName $destination -Force
        }
}

$services = (Join-Path $out "META-INF/services/com.willclay.forgeide.lang.api.LanguageProvider")
if (-not (Test-Path $services))
{
    throw "No LanguageProvider service file was found. LanguagePluginLoader would not see this plugin."
}

# No Class-Path attribute: the host supplies every shared dependency through
# the plugin loader's parent. The Forge-* attributes give bootstrap something
# to read back when reporting which plugins loaded.
$manifest = (Join-Path $argsDir "MANIFEST.MF")
Write-Utf8NoBom $manifest @(
    "Manifest-Version: 1.0",
    "Created-By: packaging/build-plugin.ps1",
    "Forge-Plugin-Id: $PluginId",
    "Forge-Plugin-Version: $Version",
    "Forge-Language-Id: $LanguageId",
    "Forge-Api-Version: $ApiVersion"
)

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $jarPath) | Out-Null
if (Test-Path $jarPath) { Remove-Item $jarPath -Force }

Write-Output "jar ..."
& $jar --create --file $jarPath --manifest $manifest -C $out .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE." }

$size = [Math]::Round((Get-Item $jarPath).Length / 1KB, 1)
Write-Output ""
Write-Output "Built $jarPath ($size KB)"

if ($Install)
{
    $plugins = (Join-Path $env:USERPROFILE ".forge/plugins")
    New-Item -ItemType Directory -Force -Path $plugins | Out-Null
    Copy-Item $jarPath (Join-Path $plugins (Split-Path -Leaf $jarPath)) -Force
    Write-Output "Installed to $plugins"
}
