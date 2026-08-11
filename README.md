# ECM Developer Tools

**[Download the latest installation](https://github.com/erangilboa/ECM-Developer-Tools/releases/latest)**

Portable developer workbench for **OpenText Documentum** (21.2–24.2) and **OpenText Extended ECM / Content Server (OTCS)** (21.2–24.2).

Documentum UX is inspired by DQL Buddy (not a clone). Extended ECM is a second product with nodes, categories, and Business Workspaces — not DQL.

## No Content Server required for development

First launch creates two mock profiles:

- `Local mock (Documentum)` — in-memory FakeDocbase (DFC semantics, subset DQL/IAPI)
- `Local mock (Extended ECM)` — in-memory OTCS nodes/workspaces

Default connection is the Documentum mock.

## Prerequisites

- JDK 17+ (sources target 17; Gradle 9.1 runs on JDK 17–25)
- Node.js 20+ (to *build* the UI; the installed app does not need Node)

OpenText DFC/DFS JARs are **not** bundled. Point a live DFC profile at your install if you have one.

## Install (Windows)

**Download:** https://github.com/erangilboa/ECM-Developer-Tools/releases/latest

| Package | Use when |
| --- | --- |
| `ECM-Developer-Tools-*-windows-x64.zip` | Windows install with bundled Java (recommended) |
| `ECM-Developer-Tools-*-portable.zip` | Any OS; requires Java 17+ on PATH |

Unzip the Windows package and run `install-windows.ps1` or `start-workbench.bat`. Details: [install/README.md](install/README.md).

To build and install from this source tree, double-click `install.bat` (or run the Gradle task):

```bat
gradlew.bat installLocal
```

That builds the UI into the server JAR, then copies the app to `%LOCALAPPDATA%\Programs\DCTM-Workbench` and creates **Start Menu** and **Desktop** shortcuts named **DCTM Workbench**.

Launch the shortcut. A small status window appears and the browser opens at http://127.0.0.1:18080/. Quit from that window (or close it) to stop the server.

To uninstall:

```bat
powershell -ExecutionPolicy Bypass -File packaging\uninstall-windows.ps1
```

Profiles stay in `%USERPROFILE%\.dctm-admin`.

### Portable zip (any OS)

```bat
gradlew.bat dist
```

Artifacts:

| Path | What |
| --- | --- |
| `build/dist/dctm-workbench-0.1.0.zip` | JAR + start scripts (needs Java 17+ on PATH) |
| `build/dist/dctm-workbench/` | Unzipped portable folder |
| `build/dist/DCTMWorkbench/` | App image with a bundled runtime (`jpackage`, no separate Java install) |

Unzip the zip, then run `start-workbench.bat` / `start-workbench.sh`, or on Windows:

```bat
powershell -ExecutionPolicy Bypass -File install-windows.ps1 -SourceDir .
```

## Run (development)

```bash
# backend
./gradlew :server:bootRun

# UI (second terminal)
cd modules/ui
npm install
npm run dev
```

Open http://localhost:5173 (Vite proxies `/api` to http://localhost:18080).

Production-style (API + built UI):

```bash
cd modules/ui && npm install && npm run build
./gradlew :server:bootRun
```

Then open http://localhost:18080.

The API uses port **18080** so it does not collide with a local Firebase emulator on 8080. Override with `--server.port=…` if needed.

## Layout

| Module | Role |
| --- | --- |
| `modules/core` | Product-neutral SPI, capabilities, DTOs |
| `modules/otds` | OTDS token/ticket helpers |
| `modules/dfc-mock` | Mock DFC + FakeDocbase |
| `modules/dfc-adapter` | Live DFC via isolated classloader |
| `modules/otcs-mock` | Mock OTCS |
| `modules/otcs-adapter` | Content Server REST v2 |
| `modules/rest-adapter` | Documentum REST HAL |
| `modules/dfs-adapter` | DFS stub |
| `modules/server` | Spring Boot API |
| `modules/ui` | React workbench |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/CAPABILITIES.md](docs/CAPABILITIES.md), [docs/VERSIONS.md](docs/VERSIONS.md), [docs/FEATURE_MAP.md](docs/FEATURE_MAP.md).

Profiles and secrets live in `%USERPROFILE%\.dctm-admin\` (or `~/.dctm-admin`).
