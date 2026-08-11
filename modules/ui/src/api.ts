import type {
  BusinessWorkspace,
  ConnectionProfile,
  FolderContents,
  GridResult,
  JobDetail,
  JobInfo,
  ObjectDump,
  SessionView,
  TypeInfo,
} from "./types";

async function http<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      message = body.error || JSON.stringify(body);
    } catch {
      message = await res.text();
    }
    throw new Error(message);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

export const api = {
  profiles: () => http<ConnectionProfile[]>("/api/profiles"),
  saveProfile: (profile: ConnectionProfile, secret?: string) =>
    http<ConnectionProfile>("/api/profiles", {
      method: "POST",
      body: JSON.stringify({ profile, secret }),
    }),
  connect: (profileId: string, secret?: string) =>
    http<SessionView>("/api/sessions", {
      method: "POST",
      body: JSON.stringify({ profileId, secret }),
    }),
  roots: (sid: string) => http<import("./types").BrowseNode[]>(`/api/sessions/${sid}/browse/roots`),
  children: (sid: string, id: string, q?: string) =>
    http<FolderContents>(
      `/api/sessions/${sid}/browse/${encodeURIComponent(id)}/children${q ? `?q=${encodeURIComponent(q)}` : ""}`
    ),
  dump: (sid: string, id: string) =>
    http<ObjectDump>(`/api/sessions/${sid}/objects/${encodeURIComponent(id)}/dump`),
  saveDump: (sid: string, dump: ObjectDump) =>
    http<void>(`/api/sessions/${sid}/objects/${encodeURIComponent(dump.id)}/dump`, {
      method: "PUT",
      body: JSON.stringify(dump),
    }),
  dql: (sid: string, dql: string) =>
    http<GridResult>(`/api/sessions/${sid}/dql`, {
      method: "POST",
      body: JSON.stringify({ dql, mode: "READ", maxRows: 500 }),
    }),
  search: (sid: string, query: string) =>
    http<GridResult>(`/api/sessions/${sid}/search`, {
      method: "POST",
      body: JSON.stringify({ query, limit: 100 }),
    }),
  jobs: (sid: string) => http<{ jobs: JobInfo[] }>(`/api/sessions/${sid}/jobs`),
  jobDetail: (sid: string, jobId: string) =>
    http<JobDetail>(`/api/sessions/${sid}/jobs/${encodeURIComponent(jobId)}`),
  runJob: (sid: string, jobId: string) =>
    http<void>(`/api/sessions/${sid}/jobs/${encodeURIComponent(jobId)}/run`, { method: "POST" }),
  contentUrl: (sid: string, objectId: string, inline = true) =>
    `/api/sessions/${sid}/objects/${encodeURIComponent(objectId)}/content?disposition=${inline ? "inline" : "attachment"}`,
  types: (sid: string) => http<{ types: TypeInfo[] }>(`/api/sessions/${sid}/types`),
  workspaces: (sid: string) => http<BusinessWorkspace[]>(`/api/sessions/${sid}/workspaces`),
  iapi: (sid: string, command: string) =>
    http<{ ok: boolean; output: string; currentId: string }>(`/api/sessions/${sid}/iapi`, {
      method: "POST",
      body: JSON.stringify({ command }),
    }),
  resetMock: (sid: string) => http<void>(`/api/sessions/${sid}/reset-mock`, { method: "POST" }),
  stub: (module: string) => http<{ title: string; summary: string; stub: boolean }>(`/api/stubs/${module}`),
  queries: () => http<{ id: string; name: string; text: string; product: string }[]>("/api/queries"),
  saveQuery: (name: string, text: string, product: string) =>
    http("/api/queries", { method: "POST", body: JSON.stringify({ name, text, product }) }),
};
