param(
    [string] $SourceDir,
    [string] $InstallDir = (Join-Path $env:LOCALAPPDATA "Programs\ECM-Dev-Workbench"),
    [switch] $NoShortcuts
)

$ErrorActionPreference = "Stop"

function Find-PackageRoot([string] $start) {
    $candidates = @($start)
    $nested = Join-Path $start "ECM-Dev-Workbench"
    if (Test-Path $nested) { $candidates += $nested }

    foreach ($dir in $candidates) {
        $bat = Join-Path $dir "start-workbench.bat"
        $exe = Join-Path $dir "ECM-Dev-Workbench.exe"
        $jar = Join-Path $dir "ECM-Dev-Workbench.jar"
        if ((Test-Path $bat) -or (Test-Path $exe) -or (Test-Path $jar)) {
            return (Resolve-Path $dir).Path
        }
    }
    return $null
}

if (-not $SourceDir) {
    $SourceDir = $PSScriptRoot
}

if (-not (Test-Path $SourceDir)) {
    throw @"
Source folder does not exist: $SourceDir

Unzip the release package, then run this script from that folder with no arguments:

  powershell -ExecutionPolicy Bypass -File .\install-windows.ps1

Download: https://github.com/erangilboa/ECM-Developer-Tools/releases/latest
  - ECM-Developer-Tools-*-windows-x64.zip  (recommended)
  - ECM-Developer-Tools-*-portable.zip
"@
}

$package = Find-PackageRoot $SourceDir
if (-not $package) {
    throw @"
This folder is not an install package (no start-workbench.bat, ECM-Dev-Workbench.exe, or ECM-Dev-Workbench.jar):

  $SourceDir

If you downloaded GitHub source code, that is not the installer.
Get the zip from: https://github.com/erangilboa/ECM-Developer-Tools/releases/latest
Unzip it and run install-windows.ps1 from inside the unzipped folder (no SourceDir prompt).
"@
}

Write-Host "Installing ECM-Dev-Workbench"
Write-Host "  from $package"
Write-Host "  to   $InstallDir"

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Get-ChildItem $InstallDir -Force -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -ne "logs" } |
    Remove-Item -Recurse -Force

Copy-Item -Path (Join-Path $package "*") -Destination $InstallDir -Recurse -Force

$target = Join-Path $InstallDir "start-workbench.bat"
$exe = Join-Path $InstallDir "ECM-Dev-Workbench.exe"
if (-not (Test-Path $target) -and (Test-Path $exe)) {
    @"
@echo off
start "" "%~dp0ECM-Dev-Workbench.exe"
"@ | Set-Content -Path $target -Encoding ASCII
}
if (-not (Test-Path $target)) {
    throw "Install copied files but start-workbench.bat is still missing in $InstallDir"
}

if (-not $NoShortcuts) {
    $ws = New-Object -ComObject WScript.Shell
    $startMenu = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs"
    New-Item -ItemType Directory -Force -Path $startMenu | Out-Null
    foreach ($folder in @($startMenu, [Environment]::GetFolderPath("Desktop"))) {
        $lnkPath = Join-Path $folder "ECM-Dev-Workbench.lnk"
        $lnk = $ws.CreateShortcut($lnkPath)
        if (Test-Path $exe) {
            $lnk.TargetPath = $exe
        } else {
            $lnk.TargetPath = $target
        }
        $lnk.WorkingDirectory = $InstallDir
        $lnk.WindowStyle = 1
        $lnk.Description = "ECM-Dev-Workbench"
        $ico = Join-Path $InstallDir "app-icon.ico"
        if (Test-Path $ico) {
            $lnk.IconLocation = "$ico,0"
        }
        $lnk.Save()
        Write-Host "Shortcut: $lnkPath"
    }
}

Write-Host "Install complete."
Write-Host "Start Menu / Desktop: ECM-Dev-Workbench"
Write-Host "Or run: $target"
