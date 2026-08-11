DCTM Workbench
==============

Portable package for OpenText Documentum and Extended ECM (OTCS).

Requirements
------------
Java 17 or later on PATH (not needed if this folder contains DCTMWorkbench.exe).

Install on Windows
------------------
1. Right-click install-windows.ps1 -> Run with PowerShell
   or from this folder:

   powershell -ExecutionPolicy Bypass -File .\install-windows.ps1 -SourceDir .

2. Start "DCTM Workbench" from the Start Menu or Desktop.

Uninstall
---------
powershell -ExecutionPolicy Bypass -File .\uninstall-windows.ps1

Run without installing
----------------------
Windows:  start-workbench.bat
Linux/macOS:  ./start-workbench.sh

Then open http://127.0.0.1:18080/ if the browser does not open.

Profiles and logs:  %USERPROFILE%\.dctm-admin  (or ~/.dctm-admin)

OpenText DFC/DFS JARs are not bundled. Point a live DFC profile at your local install.
