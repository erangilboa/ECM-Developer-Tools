ECM-Dev-Workbench
=================

Portable package for OpenText Documentum and Extended ECM (OTCS).

Requirements
------------
Java 17 or later on PATH (not needed if this folder contains ECM-Dev-Workbench.exe).

Install on Windows
------------------
Double-click install-windows.cmd
or:

   powershell -ExecutionPolicy Bypass -File .\install-windows.ps1

Do not pass a SourceDir. The script installs from this unzipped folder.

2. Start "ECM-Dev-Workbench" from the Start Menu or Desktop.

Uninstall
---------
powershell -ExecutionPolicy Bypass -File .\uninstall-windows.ps1

Run without installing
----------------------
Windows:  start-workbench.bat
Linux/macOS:  ./start-workbench.sh

Then open http://127.0.0.1:18080/ if the browser does not open.

Profiles and logs:  %USERPROFILE%\.ecm-dev-workbench  (or ~/.ecm-dev-workbench)

OpenText DFC/DFS JARs are not bundled. Point a live DFC profile at your local install.
