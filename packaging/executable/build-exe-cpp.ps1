#Requires -Version 5.1

<#
.SYNOPSIS
    Builds the C++ ForgeIDE.exe launcher and copies it into dist/.

.DESCRIPTION
    The launcher (native/cpp-native) is a small Windows program that finds
    dist/runtime/bin/server/jvm.dll, starts the JVM through JNI and runs
    ForgeIDE's Main class. It is built with CMake and MinGW g++, and embeds
    the application icon through forge.rc.

    CMake is a native Windows program, so it does not reliably find a
    compiler from whatever shell launched it. This script therefore looks
    for the tools itself and hands them to CMake explicitly:

      1. g++, gcc and windres from -MinGW if given, otherwise from PATH,
         otherwise from CLion's bundled MinGW.
      2. ninja from PATH, otherwise from CLion's bundled copy.

    The build happens in build/native/cpp-native, which .gitignore already
    covers, so it never mixes with CLion's own cmake-build-* folders.

    CMake's find_package(JNI) needs a JDK. Set JAVA_HOME if it cannot find
    one.

.PARAMETER Config
    CMake build type. Release (the default) is smaller and faster; Debug
    keeps symbols for stepping through the launcher in a debugger.

.PARAMETER MinGW
    A MinGW bin directory to use instead of searching for one.

.PARAMETER Clean
    Delete the build directory first. Needed after switching compilers,
    because CMake remembers the compiler a build directory was made with.

.PARAMETER NoCopy
    Build only; leave dist/ForgeIDE.exe as it is.

.EXAMPLE
    .\packaging\executable\build-exe-cpp.ps1

.EXAMPLE
    .\packaging\executable\build-exe-cpp.ps1 -Config Debug -Clean
#>

[CmdletBinding()]
param(
    [ValidateSet("Release", "Debug", "RelWithDebInfo", "MinSizeRel")]
    [string] $Config = "Release",
    [string] $MinGW  = "",
    [switch] $Clean,
    [switch] $NoCopy
)

$ErrorActionPreference = "Stop"

$root     = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$source   = Join-Path $root "native/cpp-native"
$buildDir = Join-Path $root "build/native/cpp-native"
$dist     = Join-Path $root "dist"

# Prints the real command before running it, so the console shows exactly
# what happened — the same thing you would type to do it by hand.
function Invoke-Tool([string] $exe, [string[]] $arguments)
{
    Write-Output "> $exe $($arguments -join ' ')"
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "$(Split-Path -Leaf $exe) failed with exit code $LASTEXITCODE." }
}

function Find-OnPath([string] $name)
{
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if ($null -eq $command) { return $null }
    return $command.Source
}

# ----------------------------------------------------------------- toolchain

$cmake = Find-OnPath "cmake.exe"
if ($null -eq $cmake) { throw "cmake was not found. Install CMake and put it on PATH." }

$clionBin = Join-Path $env:LOCALAPPDATA "Programs/CLion/bin"

if ($MinGW)
{
    $mingwBin = $MinGW
}
else
{
    $gpp = Find-OnPath "g++.exe"
    if ($null -ne $gpp) { $mingwBin = Split-Path -Parent $gpp }
    else { $mingwBin = Join-Path $clionBin "mingw/bin" }
}

$gcc     = Join-Path $mingwBin "gcc.exe"
$gpp     = Join-Path $mingwBin "g++.exe"
$windres = Join-Path $mingwBin "windres.exe"

foreach ($tool in @($gcc, $gpp, $windres))
{
    if (-not (Test-Path $tool))
    {
        throw "$tool was not found. Install MinGW (or CLion), or pass -MinGW <bin directory>."
    }
}

$ninja = Find-OnPath "ninja.exe"
if ($null -eq $ninja)
{
    $ninja = Join-Path $clionBin "ninja/win/x64/ninja.exe"
    if (-not (Test-Path $ninja)) { throw "ninja was not found. Install Ninja and put it on PATH." }
}

Write-Output "Launcher : C++ ($Config)"
Write-Output "Compiler : $gpp"
Write-Output "Build dir: $buildDir"
Write-Output ""

# --------------------------------------------------------------------- build

if ($Clean -and (Test-Path $buildDir)) { Remove-Item $buildDir -Recurse -Force }

# CMake wants forward slashes in -D paths; backslashes can be read as escapes.
Invoke-Tool $cmake @(
    "-S", $source,
    "-B", $buildDir,
    "-G", "Ninja",
    "-DCMAKE_BUILD_TYPE=$Config",
    "-DCMAKE_MAKE_PROGRAM=$($ninja -replace '\\', '/')",
    "-DCMAKE_C_COMPILER=$($gcc -replace '\\', '/')",
    "-DCMAKE_CXX_COMPILER=$($gpp -replace '\\', '/')",
    "-DCMAKE_RC_COMPILER=$($windres -replace '\\', '/')"
)

Invoke-Tool $cmake @("--build", $buildDir)

$exe = Join-Path $buildDir "ForgeIDE.exe"
if (-not (Test-Path $exe)) { throw "The build finished but $exe was not produced." }

$size = [Math]::Round((Get-Item $exe).Length / 1KB, 1)
Write-Output ""
Write-Output "Built $exe ($size KB)"

if (-not $NoCopy)
{
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    Copy-Item $exe (Join-Path $dist "ForgeIDE.exe") -Force
    Write-Output "Copied to $(Join-Path $dist 'ForgeIDE.exe')"
}
