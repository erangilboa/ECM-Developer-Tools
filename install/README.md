# Install ECM-Dev-Workbench

**Download the latest installation:** https://github.com/erangilboa/ECM-Developer-Tools/releases/latest

**ECM-Dev-Workbench** for OpenText **Documentum** (repository browser, DQL, dump, jobs, IAPI, document viewer) and **Extended ECM / OTCS** (node browser, search, categories, Business Workspaces, scheduled agents). Full functionality list: [README.md](../README.md#functionality).

## Windows (recommended)

1. Download **`ECM-Developer-Tools-*-windows-x64.zip`** from the [latest release](https://github.com/erangilboa/ECM-Developer-Tools/releases/latest) — not “Source code”.
2. Unzip it.
3. Double-click `install-windows.cmd` (or run `install-windows.ps1` with **no arguments**). You can also double-click `start-workbench.bat` to run without installing shortcuts.

This package includes a Java runtime. You do not need to install a JDK.

Shortcuts are created on the Start Menu and Desktop as **ECM-Dev-Workbench**. The browser opens at http://127.0.0.1:18080/.

Uninstall:

```powershell
powershell -ExecutionPolicy Bypass -File uninstall-windows.ps1
```

## Portable (any OS, needs Java 17+)

Download **`ECM-Developer-Tools-*-portable.zip`**, unzip, then:

- Windows: `start-workbench.bat`
- Linux/macOS: `./start-workbench.sh`

A copy of the portable zip is also in this folder when you clone the repo.

## Build from source

From the repository root:

```bat
gradlew.bat installLocal
```
