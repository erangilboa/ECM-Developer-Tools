# Version policy (21.2–24.2)

The client is Java 17 bytecode. It runs on JDK 17+.

## Documentum

- Treat DCTM REST HAL as stable; feature-detect from product-info / home.
- REST DQL is SELECT-only. EXECUTE / IAPI / apply require DFC (mock or live).
- `SELECT * FROM dm_job` via REST is rewritten to include `r_object_id AS method_id`.
- Warn if a live DFC jar version and server version diverge.
- HTTP Basic is supported for 21.2-style labs. OTDS is preferred on 23.4+.

## Extended ECM / Content Server

- Prefer REST v2 for nodes, search, and business workspaces; fall back to v1 auth/volumes.
- Refresh `OTCSTicket` from every response header (tickets expire).
- Writes typically use `multipart/form-data` with a JSON part named `body`.
- Business Workspace REST exists from ~21.4. If missing, list nodes of subtype 848.
- CGI roots vary: `/otcs/cs.exe`, `/otcs/cs/`, `/otcs/llisapi.dll`. Store the CGI root on the profile.

## Shared OTDS

Used for OTCS and for Documentum REST in mixed 23.4+ landscapes. Browser SSO is stubbed; password / stored bearer are implemented.
