#Requires -Version 5.1

<#
.SYNOPSIS
    Regenerates the ForgeIDE application icon from forge_icon_generator.kts.

.DESCRIPTION
    forge_icon_generator.kts draws the icon with Java2D and writes:

      - the PNGs the Swing app loads (src/main/resources/.../icons/app/)
      - forge.ico in native/cpp-native and native/rust-native for both launchers

    Requires a JDK and the Kotlin command-line compiler. Set KOTLIN_HOME to
    the compiler installation or put kotlinc on PATH.

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
$generator = Join-Path $root "packaging/icon/forge_icon_generator.kts"
$cppIco    = Join-Path $root "native/cpp-native/forge.ico"
$rustIco   = Join-Path $root "native/rust-native/forge.ico"

if ($env:KOTLIN_HOME)
{
    $compiler = Join-Path $env:KOTLIN_HOME "bin/kotlinc.bat"
}
else
{
    $compilerCommand = Get-Command kotlinc.bat -ErrorAction SilentlyContinue
    if ($null -eq $compilerCommand) { throw "kotlinc was not found. Set KOTLIN_HOME or add its bin directory to PATH." }
    $compiler = $compilerCommand.Source
}

if (-not (Test-Path $compiler)) { throw "kotlinc was not found at $compiler." }

Push-Location $root
try
{
    # The relative path matches what you would type from the repository root.
    Write-Output "> $compiler -script packaging/icon/forge_icon_generator.kts"
    & $compiler -script $generator
    if ($LASTEXITCODE -ne 0) { throw "forge_icon_generator.kts failed with exit code $LASTEXITCODE." }
}
finally
{
    Pop-Location
}

if (-not (Test-Path $cppIco)) { throw "The generator finished but $cppIco was not produced." }
if (-not (Test-Path $rustIco)) { throw "The generator finished but $rustIco was not produced." }
Write-Output ""
Write-Output "Done. Rebuild a launcher to embed the new icon in ForgeIDE.exe."
