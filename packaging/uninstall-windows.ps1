param(
    [string] $InstallDir = (Join-Path $env:LOCALAPPDATA "Programs\DCTM-Workbench")
)

$ErrorActionPreference = "Stop"

foreach ($folder in @(
        (Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs"),
        [Environment]::GetFolderPath("Desktop")
    )) {
    $lnk = Join-Path $folder "DCTM Workbench.lnk"
    if (Test-Path $lnk) {
        Remove-Item $lnk -Force
        Write-Host "Removed $lnk"
    }
}

if (Test-Path $InstallDir) {
    Remove-Item $InstallDir -Recurse -Force
    Write-Host "Removed $InstallDir"
}

Write-Host "DCTM Workbench uninstalled. Profiles in $env:USERPROFILE\.dctm-admin were left in place."
