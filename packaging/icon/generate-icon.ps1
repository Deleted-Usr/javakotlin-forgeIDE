#Requires -Version 5.1

<#
.SYNOPSIS
    Regenerates the ForgeIDE application icon from ForgeIconGenerator.java.

.DESCRIPTION
    ForgeIconGenerator.java draws the icon with Java2D and writes:

      - the PNGs the Swing app loads (src/main/resources/.../icons/app/)
      - native/cpp-native/forge.ico, embedded in the C++ launcher

    Java 11 and later can run a single .java file directly, with no separate
    compile step, which is all this script does. It then copies forge.ico
    into native/rust-native so both launchers always carry the same icon.

    The generator writes paths relative to the repository root, so the script
    runs it from there whatever folder you call it from.

    Rebuild a launcher afterwards (packaging/executable/build-exe-*.ps1) to
    put the new icon into ForgeIDE.exe.

.EXAMPLE
    .\packaging\icon\generate-icon.ps1
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$root      = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$generator = Join-Path $root "packaging/icon/ForgeIconGenerator.java"
$cppIco    = Join-Path $root "native/cpp-native/forge.ico"
$rustIco   = Join-Path $root "native/rust-native/forge.ico"

if ($env:JAVA_HOME)
{
    $java = Join-Path $env:JAVA_HOME "bin/java.exe"
}
else
{
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($null -eq $javaCommand) { throw "java was not found. Set JAVA_HOME to a JDK (11 or newer)." }
    $java = $javaCommand.Source
}

if (-not (Test-Path $java)) { throw "java was not found at $java." }

Push-Location $root
try
{
    # The relative path matches what you would type from the repository root.
    Write-Output "> $java packaging/icon/ForgeIconGenerator.java"
    & $java $generator
    if ($LASTEXITCODE -ne 0) { throw "ForgeIconGenerator failed with exit code $LASTEXITCODE." }
}
finally
{
    Pop-Location
}

if (-not (Test-Path $cppIco)) { throw "The generator finished but $cppIco was not produced." }

Copy-Item $cppIco $rustIco -Force
Write-Output "Copied forge.ico to $rustIco"
Write-Output ""
Write-Output "Done. Rebuild a launcher to embed the new icon in ForgeIDE.exe."
