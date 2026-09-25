<#
.SYNOPSIS
Copies every .kts script from this repository into the folder the Darkan client scans
for scripts, overwriting files of the same name.

.PARAMETER Clean
Also delete .kts files in the destination that are not in this repository.

.PARAMETER Dest
Install somewhere other than the detected folder (also: $env:DARKAN_SCRIPTS_DIR).
#>
[CmdletBinding()]
param(
    [switch]$Clean,
    [string]$Dest = $env:DARKAN_SCRIPTS_DIR
)

$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path -LiteralPath $PSScriptRoot).Path
if (-not $Dest) {
    $home_ = if ($env:USERPROFILE) { $env:USERPROFILE } else { $HOME }
    if (-not $home_) { throw 'Cannot detect the home folder; pass -Dest DIR' }
    $Dest = Join-Path $home_ '.darkan\scripts'
}

New-Item -ItemType Directory -Force -Path $Dest | Out-Null
$Dest = (Resolve-Path -LiteralPath $Dest).Path

if ($Dest.TrimEnd('\') -ieq $repo.TrimEnd('\')) {
    Write-Host "This repository already is the scripts folder ($Dest); nothing to copy."
    exit 0
}

$scripts = Get-ChildItem -LiteralPath $repo -Filter *.kts -File
foreach ($script in $scripts) {
    Copy-Item -LiteralPath $script.FullName -Destination $Dest -Force
}

$shared = @()
$repoLib = Join-Path $repo 'lib'
$destLib = Join-Path $Dest 'lib'
if (Test-Path -LiteralPath $repoLib) {
    New-Item -ItemType Directory -Force -Path $destLib | Out-Null
    $shared = Get-ChildItem -LiteralPath $repoLib -Filter *.kts -File
    foreach ($file in $shared) {
        Copy-Item -LiteralPath $file.FullName -Destination $destLib -Force
    }
}

if ($Clean) {
    foreach ($existing in Get-ChildItem -LiteralPath $Dest -Filter *.kts -File) {
        if (-not (Test-Path -LiteralPath (Join-Path $repo $existing.Name))) {
            Remove-Item -LiteralPath $existing.FullName -Force
            Write-Host "Removed stale $($existing.Name)"
        }
    }
    if (Test-Path -LiteralPath $destLib) {
        foreach ($existing in Get-ChildItem -LiteralPath $destLib -Filter *.kts -File) {
            if (-not (Test-Path -LiteralPath (Join-Path $repoLib $existing.Name))) {
                Remove-Item -LiteralPath $existing.FullName -Force
                Write-Host "Removed stale lib\$($existing.Name)"
            }
        }
    }
}

Write-Host "Installed $($scripts.Count) script(s) and $($shared.Count) shared file(s) into $Dest"
Write-Host 'Press "Reload scripts" in the bot sidebar (or restart the client) to load them.'
