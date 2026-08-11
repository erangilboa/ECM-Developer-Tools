@echo off
setlocal
cd /d "%~dp0"

if exist "%~dp0ECM-Dev-Workbench.exe" (
  start "" "%~dp0ECM-Dev-Workbench.exe"
  exit /b 0
)

where java >nul 2>&1
if errorlevel 1 (
  echo Java 17 or later is required to run ECM-Dev-Workbench.
  echo Install a JDK from https://adoptium.net/ and try again.
  pause
  exit /b 1
)

if not exist "%~dp0ECM-Dev-Workbench.jar" (
  echo ECM-Dev-Workbench.jar was not found in this folder.
  pause
  exit /b 1
)

echo Starting ECM-Dev-Workbench on http://127.0.0.1:18080 ...
java -Dworkbench.desktop=true -Dworkbench.open-browser=true -jar "%~dp0ECM-Dev-Workbench.jar"
if errorlevel 1 pause
