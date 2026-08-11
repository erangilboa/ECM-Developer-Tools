@echo off
setlocal
cd /d "%~dp0"

if exist "%~dp0DCTMWorkbench.exe" (
  start "" "%~dp0DCTMWorkbench.exe"
  exit /b 0
)

where java >nul 2>&1
if errorlevel 1 (
  echo Java 17 or later is required to run DCTM Workbench.
  echo Install a JDK from https://adoptium.net/ and try again.
  pause
  exit /b 1
)

echo Starting DCTM Workbench on http://127.0.0.1:18080 ...
java -Dworkbench.desktop=true -Dworkbench.open-browser=true -jar "%~dp0dctm-workbench.jar"
if errorlevel 1 pause
