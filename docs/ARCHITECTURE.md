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

## Sessions

The server keeps connected sessions in memory, keyed by a random session id. Closing the process drops mock mutations unless persisted under `~/.dctm-admin/`.

## Security

Passwords, OTCS tickets, and OTDS tokens are stored encrypted (AES-GCM) in the local profile store. Traces redact secrets.
