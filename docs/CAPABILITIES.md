# Capabilities

The workbench enables modules from `Capability` on the current session. Screens must not hard-code `Product` except when labeling ids (`r_object_id` vs node id).

## Documentum

| Capability | Mock DFC | DCTM REST | Live DFC |
| --- | --- | --- | --- |
| BROWSE | yes | yes | yes |
| OBJECT_READ | yes | yes | yes |
| OBJECT_UPDATE | yes | patch if allowed | yes |
| DQL_SELECT | yes | yes | yes |
| DQL_EXECUTE | yes | no | yes |
| IAPI | yes | no | yes |
| JOB_LIST / JOB_RUN | yes | list + run_now patch | yes |
| TYPE_DICTIONARY | yes | `/types` | yes |
| CONTENT_GET | yes | yes | yes |
| CHECKOUT | yes | if allowed | yes |
| DFS_INVOKE | no | no | stub |
| OTDS_AUTH | n/a | optional | optional |

## Extended ECM (OTCS)

| Capability | Mock OTCS | OTCS REST |
| --- | --- | --- |
| BROWSE | yes | yes |
| OBJECT_READ / UPDATE | yes | yes (multipart body) |
| CS_SEARCH | yes | `/api/v2/search` |
| CS_CATEGORIES | yes | node categories |
| BUSINESS_WORKSPACE | yes | `/api/v2/businessworkspaces` |
| JOB_LIST / JOB_RUN | yes (CS agents) | best-effort `/api/v2/agents` |
| OTDS_AUTH | n/a | ticket / bearer |
| DQL_* / IAPI | no | no |

## UI gating

The React shell resolves each nav item against the session’s `capabilities` and protocol:

- **DFC-only** (`MOCK_DFC` / `LIVE_DFC`): IAPI, DQL EXECUTE, checkout, ACL browser, ScriptRunner.
- **REST-only flavor**: REST explorer (needs `DCTM_REST` / `OTCS_REST`); OTDS SSO needs `OTDS_AUTH`.
- Actions (Run DQL mutate, Save dump, Run job, View content) disable when the matching capability is absent.

Disabled modules stay visible in the nav with an **off** badge and open an explanation panel instead of failing on click.
