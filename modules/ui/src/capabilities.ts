import type { ConnectionProfile, Protocol, SessionView } from "./types";

/** Capabilities advertised by the server for each protocol (keep in sync with CapabilitySets.java). */
export const PROTOCOL_CAPS: Record<Protocol, readonly string[]> = {
  MOCK_DFC: [
    "BROWSE",
    "OBJECT_READ",
    "OBJECT_UPDATE",
    "CONTENT_GET",
    "CONTENT_PUT",
    "CHECKOUT",
    "DQL_SELECT",
    "DQL_EXECUTE",
    "IAPI",
    "JOB_LIST",
    "JOB_RUN",
    "TYPE_DICTIONARY",
    "ACL_READ",
  ],
  LIVE_DFC: [
    "BROWSE",
    "OBJECT_READ",
    "OBJECT_UPDATE",
    "CONTENT_GET",
    "CONTENT_PUT",
    "CHECKOUT",
    "DQL_SELECT",
    "DQL_EXECUTE",
    "IAPI",
    "JOB_LIST",
    "JOB_RUN",
    "TYPE_DICTIONARY",
    "ACL_READ",
    "DFS_INVOKE",
    "OTDS_AUTH",
  ],
  DCTM_REST: [
    "BROWSE",
    "OBJECT_READ",
    "OBJECT_UPDATE",
    "CONTENT_GET",
    "DQL_SELECT",
    "JOB_LIST",
    "JOB_RUN",
    "TYPE_DICTIONARY",
    "OTDS_AUTH",
  ],
  DFS: ["DFS_INVOKE"],
  MOCK_OTCS: [
    "BROWSE",
    "OBJECT_READ",
    "OBJECT_UPDATE",
    "CONTENT_GET",
    "CONTENT_PUT",
    "CS_SEARCH",
    "CS_CATEGORIES",
    "BUSINESS_WORKSPACE",
    "JOB_LIST",
    "JOB_RUN",
  ],
  OTCS_REST: [
    "BROWSE",
    "OBJECT_READ",
    "OBJECT_UPDATE",
    "CONTENT_GET",
    "CONTENT_PUT",
    "CS_SEARCH",
    "CS_CATEGORIES",
    "BUSINESS_WORKSPACE",
    "JOB_LIST",
    "JOB_RUN",
    "OTDS_AUTH",
  ],
  CWS: ["CWS_INVOKE"],
};

export type CapSource = Pick<SessionView, "capabilities" | "protocol" | "product"> | null | undefined;

export function hasCap(source: CapSource, capability: string): boolean {
  return !!source?.capabilities?.includes(capability);
}

export function isDfcProtocol(protocol?: Protocol | string | null): boolean {
  return protocol === "MOCK_DFC" || protocol === "LIVE_DFC";
}

export function isRestProtocol(protocol?: Protocol | string | null): boolean {
  return protocol === "DCTM_REST" || protocol === "OTCS_REST";
}

export function isMockProtocol(protocol?: Protocol | string | null): boolean {
  return protocol === "MOCK_DFC" || protocol === "MOCK_OTCS";
}

export function capsForProfile(profile: Pick<ConnectionProfile, "protocol">): readonly string[] {
  return PROTOCOL_CAPS[profile.protocol] ?? [];
}

export function profileHas(profile: Pick<ConnectionProfile, "protocol">, capability: string): boolean {
  return capsForProfile(profile).includes(capability);
}

/** Human-readable reason when a DFC- or REST-backed feature is unavailable. */
export function unavailableReason(capability: string, protocol?: Protocol | string | null): string {
  switch (capability) {
    case "IAPI":
    case "DQL_EXECUTE":
    case "CHECKOUT":
    case "ACL_READ":
    case "CONTENT_PUT":
      return isRestProtocol(protocol)
        ? `${capability} needs DFC (mock or live) — not available on ${protocol}`
        : `${capability} is not available on this connection (${protocol || "unknown"})`;
    case "DFS_INVOKE":
      return "DFS needs Live DFC (or a DFS endpoint) — not available on this connection";
    case "OTDS_AUTH":
      return "OTDS SSO needs a REST (or Live DFC) connection with OTDS configured";
    case "CWS_INVOKE":
      return "CWS needs an OTCS CWS endpoint — not available on this connection";
    case "DQL_SELECT":
      return "DQL needs a Documentum session";
    case "CS_SEARCH":
    case "BUSINESS_WORKSPACE":
    case "CS_CATEGORIES":
      return `${capability} needs an Extended ECM (OTCS) session`;
    default:
      return `${capability} is not available on this connection (${protocol || "unknown"})`;
  }
}

const MUTATING_DQL = /^\s*(update|delete|create|insert|alter|drop|grant|revoke|execute|begin)\b/i;

export function isMutatingDql(text: string): boolean {
  return MUTATING_DQL.test(text.trim());
}

export type FeatureChip = { id: string; label: string; enabled: boolean; reason?: string };

/** Landing / profile preview chips for Documentum. */
export function documentumFeatureChips(protocol: Protocol): FeatureChip[] {
  const caps = PROTOCOL_CAPS[protocol] ?? [];
  const has = (c: string) => caps.includes(c);
  return [
    { id: "browse", label: "Browse", enabled: has("BROWSE") },
    { id: "dql", label: "DQL SELECT", enabled: has("DQL_SELECT") },
    {
      id: "dql-exec",
      label: "DQL EXECUTE",
      enabled: has("DQL_EXECUTE"),
      reason: has("DQL_EXECUTE") ? undefined : unavailableReason("DQL_EXECUTE", protocol),
    },
    { id: "dump", label: "Dump", enabled: has("OBJECT_READ") },
    { id: "jobs", label: "Jobs", enabled: has("JOB_LIST") },
    {
      id: "iapi",
      label: "IAPI",
      enabled: has("IAPI"),
      reason: has("IAPI") ? undefined : unavailableReason("IAPI", protocol),
    },
    {
      id: "rest",
      label: "REST",
      enabled: true,
      reason: isRestProtocol(protocol) ? undefined : "Mock session proxy — use REST explorer to try sample paths",
    },
  ];
}

/** Landing / profile preview chips for Extended ECM. */
export function xecmFeatureChips(protocol: Protocol): FeatureChip[] {
  const caps = PROTOCOL_CAPS[protocol] ?? [];
  const has = (c: string) => caps.includes(c);
  return [
    { id: "browse", label: "Browse", enabled: has("BROWSE") },
    { id: "search", label: "Search", enabled: has("CS_SEARCH") },
    { id: "workspaces", label: "Workspaces", enabled: has("BUSINESS_WORKSPACE") },
    { id: "categories", label: "Categories", enabled: has("CS_CATEGORIES") },
    { id: "agents", label: "Agents", enabled: has("JOB_LIST") },
    {
      id: "rest",
      label: "REST",
      enabled: true,
      reason: isRestProtocol(protocol) ? undefined : "Mock session proxy — use REST explorer to try sample paths",
    },
  ];
}
