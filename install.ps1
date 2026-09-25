<#
.SYNOPSIS
Copies every .kts script from this repository into the folder the Darkan client scans
for scripts, overwriting files of the same name.

.PARAMETER Clean
Also delete .kts files in the destination that are not in this repository. private/ is never copied or deleted.

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

$sources = Get-ChildItem -LiteralPath $repo -Filter *.kts -File -Recurse |
    Where-Object { $_.FullName -notmatch '[\\/](\.git|\.claude|private)[\\/]' }

$count = 0
$libs = 0
foreach ($source in $sources) {
    $relative = $source.FullName.Substring($repo.Length).TrimStart('\', '/')
    $target = Join-Path $Dest $relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $source.FullName -Destination $target -Force
    if ($relative -like 'lib*') { $libs++ } else { $count++ }
}

if ($Clean) {
    $existing = Get-ChildItem -LiteralPath $Dest -Filter *.kts -File -Recurse |
        Where-Object { $_.FullName -notmatch '[\\/](\.git|\.claude|private)[\\/]' }
    foreach ($file in $existing) {
        $relative = $file.FullName.Substring($Dest.Length).TrimStart('\', '/')
        if (-not (Test-Path -LiteralPath (Join-Path $repo $relative))) {
            Remove-Item -LiteralPath $file.FullName -Force
            Write-Host "Removed stale $relative"
        }
    }
}

Write-Host "Installed $count script(s) and $libs shared file(s) into $Dest"
Write-Host 'Press "Reload scripts" in the bot sidebar (or restart the client) to load them.'
