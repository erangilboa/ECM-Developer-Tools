@echo off
cd /d "%~dp0"
echo Building and installing ECM-Dev-Workbench...
call gradlew.bat installLocal
if errorlevel 1 (
  echo.
  echo Install failed.
  pause
  exit /b 1
)
echo.
echo Installed. Launching...
set "START=%LOCALAPPDATA%\Programs\ECM-Dev-Workbench\start-workbench.bat"
if exist "%START%" (
  start "" "%START%"
) else (
  echo Shortcuts are on the Start Menu and Desktop: ECM-Dev-Workbench
)
