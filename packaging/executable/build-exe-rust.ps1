#Requires -Version 5.1

<#
.SYNOPSIS
    Builds the Rust ForgeIDE.exe launcher and copies it into dist/.

.DESCRIPTION
    native/rust-native is the Rust twin of the C++ launcher: it finds
    dist/runtime/bin/server/jvm.dll, starts the JVM through JNI and runs
    ForgeIDE's Main class. Cargo does all the work, including build.rs,
    which embeds forge.ico as the exe's icon.

    Cargo keeps its output in native/rust-native/target (already ignored by
    Git), so builds share a cache with RustRover or any IDE that opens the
    crate.

    Both launchers produce a file called ForgeIDE.exe. Whichever one you
    build last is the one that ends up in dist/.

.PARAMETER CargoProfile
    Cargo profile: release (the default) or dev, for an unoptimised build
    with debug info.

.PARAMETER Clean
    Run cargo clean first.

.PARAMETER NoCopy
    Build only; leave dist/ForgeIDE.exe as it is.

.EXAMPLE
    .\packaging\executable\build-exe-rust.ps1

.EXAMPLE
    .\packaging\executable\build-exe-rust.ps1 -CargoProfile dev
#>

[CmdletBinding()]
param(
    [ValidateSet("release", "dev")]
    [string] $CargoProfile = "release",
    [switch] $Clean,
    [switch] $NoCopy
)

$ErrorActionPreference = "Stop"

$root     = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$manifest = Join-Path $root "native/rust-native/Cargo.toml"
$dist     = Join-Path $root "dist"

# Prints the real command before running it, so the console shows exactly
# what happened — the same thing you would type to do it by hand.
function Invoke-Tool([string] $exe, [string[]] $arguments)
{
    Write-Output "> $exe $($arguments -join ' ')"
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "$(Split-Path -Leaf $exe) failed with exit code $LASTEXITCODE." }
}

$cargoCommand = Get-Command cargo.exe -ErrorAction SilentlyContinue
if ($null -eq $cargoCommand) { throw "cargo was not found. Install Rust from https://rustup.rs." }
$cargo = $cargoCommand.Source

Write-Output "Launcher: Rust ($CargoProfile)"
Write-Output "Crate   : $manifest"
Write-Output ""

if ($Clean) { Invoke-Tool $cargo @("clean", "--manifest-path", $manifest) }

Invoke-Tool $cargo @("build", "--manifest-path", $manifest, "--profile", $CargoProfile)

# Cargo names the dev profile's output folder "debug", for historical reasons.
$folder = $CargoProfile
if ($CargoProfile -eq "dev") { $folder = "debug" }

$exe = Join-Path $root "native/rust-native/target/$folder/ForgeIDE.exe"
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
