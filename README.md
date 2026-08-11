# ECM Developer Tools

**[Download the latest installation](https://github.com/erangilboa/ECM-Developer-Tools/releases/latest)**

Local developer workbench for **OpenText Documentum** (21.2–24.2) and **OpenText Extended ECM / Content Server (OTCS)** (21.2–24.2). Connect to one product at a time; the sidebar, id labels, and tools switch to that platform.

Documentum: cabinets, DQL, dump, jobs, IAPI. Extended ECM is a separate product: volumes/nodes, CS search, categories, Business Workspaces, and scheduled agents — not DQL.

You can work **offline against in-memory mocks** or **live** against Documentum REST / DFC and OTCS REST (optional OTDS). OpenText proprietary JARs are not redistributed; a live DFC profile points at your own install.

## Functionality

### Connections and profiles

- Named connection profiles for Documentum (`MOCK_DFC`, `DCTM_REST`, `LIVE_DFC`, DFS stub) and Extended ECM (`MOCK_OTCS`, `OTCS_REST`, CWS stub).
- First launch seeds **Local mock (Documentum)** and **Local mock (Extended ECM)**.
- Username/password, HTTP Basic, OTCS ticket, and OTDS password-grant or stored bearer. Secrets are stored encrypted (AES-GCM) under `%USERPROFILE%\.dctm-admin\` (or `~/.dctm-admin`).
- Connect from the landing cards or the top bar. The status chip shows product, protocol, repository, version, and user.
- Capability matrix drives which modules appear. Unsupported calls fail with a clear message (for example “DQL requires a Documentum session”).
- Mock sessions can be reset from the UI.

### Documentum

| Tool | What it does |
| --- | --- |
| **Repository browser** | Cabinet/folder tree, contents list, breadcrumbs, Up. Open folder, dump, view content, download, copy id/name. |
| **Run DQL** | Monaco editor with highlighting and autocomplete (keywords, types, attributes). SELECT on all Documentum adapters; EXECUTE on mock/live DFC (REST is SELECT-only). Wide result grid, sticky first column, wrap toggle, dump from an object id. Saved queries and history. |
| **Dump** | Object attributes split into **custom** vs **system** (`r_`, `i_`, `a_`). Editable non-readonly fields, save back to the session. Multiple dump tabs, Back to the screen you drilled from. |
| **Document viewer** | Inline text, image, and PDF plus download. MIME guessed from type/content. |
| **Jobs** | `dm_job` list with status, last return, last/next run. Detail pane, Sysadmin/Reports logs, View/Dump a report, **Run now**. |
| **IAPI** | Thin REPL on mock or live DFC (`dump,c,<id>` and related commands). Not available on REST-only sessions. |

Live DFC loads your DFC JARs in an isolated classloader (javax DFC vs Jakarta Spring Boot). REST talks HAL; `SELECT * FROM dm_job` is rewritten so job ids work. Versions 21.2–24.2; HTTP Basic for older labs, OTDS preferred on 23.4+.

### Extended ECM / Content Server

| Tool | What it does |
| --- | --- |
| **Node browser** | Volume/folder tree (Enterprise, Personal, …), contents list, breadcrumbs. Same actions as Documentum browse, using node ids. |
| **Search** | CS search by name or node id; results open node details. |
| **Node details** | Dump-style view: custom node/business fields vs core CS system metadata, plus **categories** (OTCS category attributes). SAP-linked Business Workspaces are flagged and treated as read-mostly. |
| **Business Workspaces** | List/get workspaces (template, external system, BO type/id). Create-via-ECMLink is stubbed. |
| **Jobs** | Content Server **scheduled agents** (notification, index, ECMLink/SAP sync, expiration, …) — not `dm_job`. Status, logs/reports, **Run now**. Live REST uses best-effort `/api/v2/agents` or `/api/v2/scheduledjobs`. |

OTCS REST prefers v2 for nodes, search, and workspaces; refreshes `OTCSTicket` from response headers. Writes use `multipart/form-data` with a JSON `body` part. CGI root is stored on the profile (`/otcs/cs.exe`, `/otcs/cs/`, `/otcs/llisapi.dll`, …).

### Shared workbench UX

- One connected product at a time; sidebar is Documentum *or* Extended ECM, not a mix.
- Drill-down (browser, DQL, search, jobs → dump) keeps a **Back** path; Esc returns. Browser/DQL/search state is kept while you inspect an object.
- Visible action bar (Open, Dump/Details, View, Download, Copy). Right-click is a real menu — no typed `prompt()`.
- Activity log (collapsible) at the bottom.
- Installed app: status window, opens http://127.0.0.1:18080/, Quit stops the server. If the port is already in use, it reopens the existing instance.

### Mock repositories (no Content Server)

| Mock | Seeded content |
| --- | --- |
| Documentum FakeDocbase | Cabinets/folders, sample documents (including a viewable contract), subset DQL/IAPI, `dm_job` plus Sysadmin/Reports logs. |
| OTCS FakeOtcs | Volumes, folders, documents, categories, Business Workspace, scheduled agents and agent log nodes. |

Enough to learn the UI and exercise dump, content, DQL, search, and jobs without a docbase or CS.

### Stubbed (SPI present, UI placeholder)

ScriptRunner, DCTM REST explorer, DFS explorer, ACLs, users/groups, workflows, CWS explorer, ECMLink create-workspace, OTDS browser SSO.

### Out of scope

Consistency fixer (deliberately not included).

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
