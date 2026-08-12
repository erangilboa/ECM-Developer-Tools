# Architecture

Dual-product local workbench: Documentum and Extended ECM (OTCS) share a shell and a `RepositorySession` SPI. Adapters never leak product metaphors into the wrong UI — capabilities drive which modules appear.

```
UI (React) → Spring Boot API → RepositorySession
                                  ├ DocumentumSession (DQL, IAPI, jobs)
                                  │    ├ DfcBridge → MockDfcBridge | LiveDfcBridge
                                  │    ├ DCTM REST adapter
                                  │    └ DFS stub
                                  └ OtcsSession (search, workspaces, categories, agents/jobs)
                                       └ OtcsBridge → MockOtcsBridge | LiveOtcsRest
Shared OTDS client feeds Documentum REST (23.4+) and OTCS REST.
```

## Design rules

- UI and HTTP API talk only to `RepositorySession` / capability-gated extras.
- Live DFC and mock DFC implement the same `DfcBridge`.
- Live OTCS and mock OTCS implement the same `OtcsBridge`.
- DFC JARs load in an isolated `URLClassLoader` (javax-era DFC vs Jakarta Spring Boot).
- No OpenText proprietary libraries in git.

## DQL and IAPI grammar

`com.dctm.workbench.core.grammar.GrammarCheck` is the source of truth for editor diagnostics. It does not execute queries. `POST /api/grammar/check` `{ language: "dql"|"iapi", text }` returns offset/length/line/column markers. The DQL and IAPI Monaco editors debounce that API and draw squiggles. Incomplete prefixes while typing are silent; closed mistakes (unterminated strings, missing FROM, unknown IAPI arity) are errors. `execquery`/`query` also run the DQL checker on the third field.

## Sessions

The server keeps connected sessions in memory, keyed by a random session id. `GET /api/sessions/{id}` returns enriched diagnostics (auth mode, REST base URL, CGI root, connected-at timestamp) without secrets. Closing the process drops mock mutations unless persisted under `~/.ecm-dev-workbench/`.

## Developer productivity (Phase 1)

- **Quick Open** (`Ctrl+P` / `Ctrl+K`): jump to modules, recent objects, query history; paste Documentum object ids, OTCS node ids, or REST URLs (`POST /api/sessions/{id}/resolve`).
- **Query library**: saved queries in `queries.json`; durable run history in `query-history.json` (`GET/POST /api/query-history`, `DELETE /api/queries/{id}`).
- **Cross-navigation**: dump attribute values that look like object ids, ACL names, or types expose links; DQL results and dump screens hand off to IAPI templates when `IAPI` is available.
- **Session strip**: top-bar popover shows connection diagnostics and capability list.

## Phase 2 — API debugging

- **REST explorer**: `POST /api/sessions/{id}/rest/proxy` reuses connection auth (Basic, Bearer, OTCSTicket, CSRF/cookies for DCTM REST). Response includes status, redacted headers, body, and `elapsedMs`.
- **Execution history**: `GET/POST /api/execution-history` persists DQL, IAPI, REST, and SEARCH runs in `execution-history.json` (secrets redacted). UI History drawer supports rerun.
- **Error panel**: structured errors with **Copy diagnostic bundle** (session metadata only; passwords/tokens redacted).
- **Timings**: IAPI and Search responses include `elapsedMs` alongside existing DQL timing.

## Security

Passwords, OTCS tickets, and OTDS tokens are stored encrypted (AES-GCM) in the local profile store. Traces redact secrets.
