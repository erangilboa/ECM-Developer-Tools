param(
    [Parameter(Mandatory = $true)]
    [string] $SourceDir,
    [string] $InstallDir = (Join-Path $env:LOCALAPPDATA "Programs\DCTM-Workbench"),
    [switch] $NoShortcuts
)

$ErrorActionPreference = "Stop"
$SourceDir = (Resolve-Path $SourceDir).Path

Write-Host "Installing DCTM Workbench to $InstallDir"

if (Test-Path $InstallDir) {
    Get-ChildItem $InstallDir -Force | Where-Object { $_.Name -ne "logs" } | Remove-Item -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Copy-Item -Path (Join-Path $SourceDir "*") -Destination $InstallDir -Recurse -Force

$target = Join-Path $InstallDir "start-workbench.bat"
if (-not (Test-Path $target)) {
    throw "start-workbench.bat missing in $InstallDir"
}

if (-not $NoShortcuts) {
    $ws = New-Object -ComObject WScript.Shell
    $startMenu = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs"
    New-Item -ItemType Directory -Force -Path $startMenu | Out-Null
    foreach ($folder in @($startMenu, [Environment]::GetFolderPath("Desktop"))) {
        $lnkPath = Join-Path $folder "DCTM Workbench.lnk"
        $lnk = $ws.CreateShortcut($lnkPath)
        $lnk.TargetPath = $target
        $lnk.WorkingDirectory = $InstallDir
        $lnk.WindowStyle = 1
        $lnk.Description = "Documentum and Extended ECM developer workbench"
        $lnk.Save()
        Write-Host "Shortcut: $lnkPath"
    }
}

Write-Host "Install complete."
Write-Host "Start Menu / Desktop: DCTM Workbench"
Write-Host "Or run: $target"
