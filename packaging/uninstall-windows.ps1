param(
    [string] $InstallDir = (Join-Path $env:LOCALAPPDATA "Programs\ECM-Dev-Workbench")
)

$ErrorActionPreference = "Stop"

foreach ($folder in @(
        (Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs"),
        [Environment]::GetFolderPath("Desktop")
    )) {
    $lnk = Join-Path $folder "ECM-Dev-Workbench.lnk"
    if (Test-Path $lnk) {
        Remove-Item $lnk -Force
        Write-Host "Removed $lnk"
    }
}

if (Test-Path $InstallDir) {
    Remove-Item $InstallDir -Recurse -Force
    Write-Host "Removed $InstallDir"
}

Write-Host "ECM-Dev-Workbench uninstalled. Profiles in $env:USERPROFILE\.ecm-dev-workbench were left in place."
