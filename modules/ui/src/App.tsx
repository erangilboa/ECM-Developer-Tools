import { useCallback, useEffect, useMemo, useState } from "react";
import Editor, { loader } from "@monaco-editor/react";
import { ActionBar, ContextMenu, objectIdColumn, type ObjectAction } from "./ActionMenu";
import { api } from "./api";
import { Browser } from "./Browser";
import { DocumentViewer } from "./DocumentViewer";
import { NavGlyph } from "./NavGlyph";
import { ProductLockup, ProductLogo } from "./ProductLogo";
import { DQL_FUNCTIONS, DQL_KEYWORDS, registerDql } from "./dqlLanguage";
import type {
  AttributeValue,
  BusinessWorkspace,
  ConnectionProfile,
  DumpTab,
  GridResult,
  JobDetail,
  JobInfo,
  ObjectDump,
  Product,
  Protocol,
  SessionView,
  TypeInfo,
} from "./types";

function applyWorkbenchTheme(monaco: any) {
  monaco.editor.defineTheme("workbench", {
    base: "vs-dark",
    inherit: true,
    rules: [],
    colors: {
      "editor.background": "#12161f",
      "editor.foreground": "#e7ebf4",
      "editorLineNumber.foreground": "#5a6274",
      "editor.lineHighlightBackground": "#1a2030",
      "editor.selectionBackground": "#4f8dff55",
      "editorCursor.foreground": "#9ec0ff",
      "editorWidget.background": "#171c27",
      "editorWidget.border": "#2c3446",
    },
  });
  monaco.editor.setTheme("workbench");
}

loader.init().then((monaco) => {
  registerDql(monaco);
  applyWorkbenchTheme(monaco);
});

type NavGroup = "workspace" | "query" | "ops" | "more";
type NavItem = {
  id: string;
  label: string;
  icon: string;
  group: NavGroup;
  cap?: string;
  stub?: boolean;
  dump?: boolean;
};

const NAV_GROUPS: { id: NavGroup; label: string }[] = [
  { id: "workspace", label: "Workspace" },
  { id: "query", label: "Query" },
  { id: "ops", label: "Operations" },
  { id: "more", label: "More" },
];

const DOCUMENTUM_NAV: NavItem[] = [
  { id: "browser", label: "Browse", icon: "folder", group: "workspace", cap: "BROWSE" },
  { id: "dql", label: "DQL", icon: "code", group: "query", cap: "DQL_SELECT" },
  { id: "jobs", label: "Jobs", icon: "clock", group: "ops", cap: "JOB_LIST" },
  { id: "dump", label: "Dump", icon: "inspect", group: "ops", dump: true },
  { id: "iapi", label: "IAPI", icon: "terminal", group: "more", cap: "IAPI" },
  { id: "scriptrunner", label: "ScriptRunner", icon: "code", group: "more", stub: true },
  { id: "rest-explorer", label: "REST explorer", icon: "search", group: "more", stub: true },
  { id: "dfs", label: "DFS", icon: "workspace", group: "more", stub: true },
  { id: "acl", label: "ACLs", icon: "inspect", group: "more", stub: true },
  { id: "users", label: "Users / groups", icon: "workspace", group: "more", stub: true },
  { id: "workflows", label: "Workflows", icon: "clock", group: "more", stub: true },
  { id: "otds-sso", label: "OTDS SSO", icon: "search", group: "more", stub: true },
];

const XECM_NAV: NavItem[] = [
  { id: "browser", label: "Browse", icon: "folder", group: "workspace", cap: "BROWSE" },
  { id: "workspaces", label: "Workspaces", icon: "workspace", group: "workspace", cap: "BUSINESS_WORKSPACE" },
  { id: "search", label: "Search", icon: "search", group: "query", cap: "CS_SEARCH" },
  { id: "jobs", label: "Jobs", icon: "clock", group: "ops", cap: "JOB_LIST" },
  { id: "dump", label: "Details", icon: "inspect", group: "ops", dump: true },
  { id: "cws", label: "CWS", icon: "code", group: "more", stub: true },
  { id: "ecmlink", label: "ECMLink create", icon: "workspace", group: "more", stub: true },
  { id: "users", label: "Users / groups", icon: "inspect", group: "more", stub: true },
  { id: "otds-sso", label: "OTDS SSO", icon: "search", group: "more", stub: true },
];

const DCTM_STUB_IDS = new Set(DOCUMENTUM_NAV.filter((n) => n.stub || n.id === "iapi").map((n) => n.id));
const XECM_STUB_IDS = new Set(XECM_NAV.filter((n) => n.stub).map((n) => n.id));

export function App() {
  const [profiles, setProfiles] = useState<ConnectionProfile[]>([]);
  const [profileId, setProfileId] = useState("");
  const [session, setSession] = useState<SessionView | null>(null);
  const [module, setModule] = useState("browser");
  const [log, setLog] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [dumps, setDumps] = useState<DumpTab[]>([]);
  const [activeDump, setActiveDump] = useState<string | null>(null);
  const [showProfile, setShowProfile] = useState(false);
  const [viewer, setViewer] = useState<{ id: string; name?: string } | null>(null);
  const [logOpen, setLogOpen] = useState(false);
  const [returnTo, setReturnTo] = useState("browser");
  const [seen, setSeen] = useState<Record<string, boolean>>({ browser: true });

  const caps = session?.capabilities ?? [];
  const has = (c: string) => caps.includes(c);

  const trace = (msg: string) => setLog((l) => [...l.slice(-200), `${new Date().toLocaleTimeString()} ${msg}`]);

  const refreshProfiles = async () => {
    const list = await api.profiles();
    setProfiles(list);
    if (!profileId && list[0]?.id) setProfileId(list[0].id!);
  };

  useEffect(() => {
    refreshProfiles().catch((e) => setError(String(e)));
  }, []);

  const connect = async (id?: string) => {
    const target = id ?? profileId;
    if (!target) return;
    setError("");
    try {
      const s = await api.connect(target);
      setProfileId(target);
      setSession(s);
      setDumps([]);
      setActiveDump(null);
      setViewer(null);
      setReturnTo("browser");
      setSeen({ browser: true });
      setModule("browser");
      trace(`Connected ${s.profileName} ${s.product} ${s.protocol} ${s.version}`);
    } catch (e) {
      setError(String(e));
    }
  };

  const openView = (id: string, name?: string) => {
    setViewer({ id, name });
    trace(`View ${name || id}`);
  };

  const openDump = async (id: string) => {
    if (!session) return;
    try {
      const dump = await api.dump(session.id, id);
      setDumps((tabs) => {
        const rest = tabs.filter((t) => t.id !== id);
        return [...rest, { id, dump }];
      });
      setActiveDump(id);
      if (module !== "dump") setReturnTo(module);
      setModule("dump");
      trace(`Dump ${id}`);
    } catch (e) {
      setError(String(e));
    }
  };

  const goModule = (id: string) => {
    setSeen((s) => (s[id] ? s : { ...s, [id]: true }));
    if (id === "dump" && module !== "dump") setReturnTo(module);
    setModule(id);
  };

  const goBack = () => {
    const target = returnTo || "browser";
    setSeen((s) => (s[target] ? s : { ...s, [target]: true }));
    setModule(target);
  };

  const closeDump = (id: string) => {
    const next = dumps.filter((t) => t.id !== id);
    setDumps(next);
    if (next.length === 0) {
      setActiveDump(null);
      goBack();
    } else if (activeDump === id) {
      setActiveDump(next[next.length - 1].id);
    }
  };

  useEffect(() => {
    setSeen((s) => (s[module] ? s : { ...s, [module]: true }));
  }, [module]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable)) return;
      if (viewer) {
        setViewer(null);
        return;
      }
      if (module === "dump") goBack();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [viewer, module, returnTo]);

  const platformNav = session?.product === "EXTENDED_ECM" ? XECM_NAV : DOCUMENTUM_NAV;
  const nav = useMemo(() => {
    if (!session) return [];
    return platformNav.filter((item) => {
      if (item.dump) return true;
      if (item.stub) return true;
      if (item.id === "iapi") return true;
      if (item.cap) return has(item.cap);
      return true;
    }).map((item) => ({
      ...item,
      stub: item.stub || (item.id === "iapi" && !has("IAPI")),
    }));
  }, [session, caps.join(",")]);
  const backLabel = platformNav.find((n) => n.id === returnTo)?.label ?? "Back";
  const dctmProfiles = profiles.filter((p) => p.product === "DOCUMENTUM");
  const xecmProfiles = profiles.filter((p) => p.product === "EXTENDED_ECM");
  const selectedProfile = profiles.find((p) => p.id === profileId);

  return (
    <div className="app">
      <div className="topbar">
        <span className="brand">
          <span className="brand-mark" aria-hidden />
          <span className="brand-text">
            ECM Tools
            <small>Developer workbench</small>
          </span>
        </span>
        <select value={profileId} onChange={(e) => setProfileId(e.target.value)}>
          <optgroup label="Documentum">
            {dctmProfiles.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </optgroup>
          <optgroup label="Extended ECM">
            {xecmProfiles.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </optgroup>
        </select>
        <button className="primary" onClick={() => connect()}>
          Connect
        </button>
        <button onClick={() => setShowProfile(true)}>Profiles…</button>
        {session && has("BROWSE") && (session.protocol === "MOCK_DFC" || session.protocol === "MOCK_OTCS") && (
          <button onClick={() => session && api.resetMock(session.id).then(() => trace("Mock reset"))}>
            Reset mock
          </button>
        )}
        <span className="badge">
          {session ? (
            <>
              <ProductLogo product={session.product} size={18} />
              <strong>{session.product === "EXTENDED_ECM" ? "Extended ECM" : "Documentum"}</strong>
              <span>
                {session.protocol} · {session.repository} · {session.version} · {session.userName}
              </span>
            </>
          ) : selectedProfile ? (
            <>
              <ProductLogo product={selectedProfile.product} size={18} />
              Ready: {selectedProfile.product === "EXTENDED_ECM" ? "Extended ECM" : "Documentum"} · {selectedProfile.name}
            </>
          ) : (
            "Select a Documentum or Extended ECM profile"
          )}
        </span>
      </div>
      {error && <div className="error-banner">{error}</div>}
      <div className="shell">
        <div className="nav">
          {session && (
            <div className="nav-section">
              <ProductLogo product={session.product} size={18} />
              <div>
                <div className="nav-product">{session.product === "EXTENDED_ECM" ? "Extended ECM" : "Documentum"}</div>
                <div className="nav-repo">{session.repository}</div>
              </div>
            </div>
          )}
          {NAV_GROUPS.map((group) => {
            const items = nav.filter((n) => n.group === group.id);
            if (items.length === 0) return null;
            return (
              <div key={group.id} className="nav-group">
                <div className="nav-group-label">{group.label}</div>
                {items.map((n) => (
                  <button
                    key={n.id}
                    className={`${module === n.id ? "active" : ""} ${n.stub ? "stub" : ""}`}
                    onClick={() => goModule(n.id)}
                  >
                    <span className="nav-ico">
                      <NavGlyph name={n.icon} />
                    </span>
                    <span className="nav-label">{n.label}</span>
                    {n.dump && dumps.length > 0 ? <span className="nav-count">{dumps.length}</span> : null}
                  </button>
                ))}
              </div>
            );
          })}
        </div>
        <div className="main">
          {!session && (
            <div className="panel landing-page">
              <header className="landing-hero">
                <p className="eyebrow">ECM Developer Tools</p>
                <h2>Connect to a repository</h2>
                <p className="lede">
                  Documentum and Extended ECM are separate products. Connect to one profile — the workbench only shows
                  that platform.
                </p>
              </header>
              <div className="landing">
                <div className="card dctm-card">
                  <ProductLockup product="DOCUMENTUM" />
                  <ul className="chips">
                    <li>Browse</li>
                    <li>DQL</li>
                    <li>Dump</li>
                    <li>Jobs</li>
                    <li>IAPI</li>
                  </ul>
                  <div className="card-actions">
                    {dctmProfiles.map((p) => (
                      <button key={p.id} className="primary" onClick={() => connect(p.id)}>
                        {p.name}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="card xecm-card">
                  <ProductLockup product="EXTENDED_ECM" />
                  <ul className="chips">
                    <li>Browse</li>
                    <li>Search</li>
                    <li>Workspaces</li>
                    <li>Categories</li>
                    <li>Agents</li>
                  </ul>
                  <div className="card-actions">
                    {xecmProfiles.map((p) => (
                      <button key={p.id} className="primary" onClick={() => connect(p.id)}>
                        {p.name}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}
          {session && seen.browser && (
            <div className="workspace" hidden={module !== "browser"}>
              <Browser session={session} onDump={openDump} onView={openView} onError={setError} trace={trace} />
            </div>
          )}
          {session?.product === "DOCUMENTUM" && seen.dql && (
            <div className="workspace" hidden={module !== "dql"}>
              <DqlStudio session={session} onDump={openDump} onError={setError} trace={trace} />
            </div>
          )}
          {session && seen.jobs && (
            <div className="workspace" hidden={module !== "jobs"}>
              <Jobs session={session} onDump={openDump} onView={openView} onError={setError} trace={trace} />
            </div>
          )}
          {session?.product === "EXTENDED_ECM" && seen.search && (
            <div className="workspace" hidden={module !== "search"}>
              <SearchPanel session={session} onDump={openDump} onError={setError} trace={trace} />
            </div>
          )}
          {session?.product === "EXTENDED_ECM" && seen.workspaces && (
            <div className="workspace" hidden={module !== "workspaces"}>
              <Workspaces session={session} onDump={openDump} onError={setError} />
            </div>
          )}
          {session && seen.dump && (
            <div className="workspace" hidden={module !== "dump"}>
              <DumpWorkspace
                session={session}
                dumps={dumps}
                active={activeDump}
                setActive={setActiveDump}
                setDumps={setDumps}
                onView={openView}
                onError={setError}
                onBack={goBack}
                onCloseTab={closeDump}
                backLabel={backLabel}
                trace={trace}
              />
            </div>
          )}
          {session?.product === "DOCUMENTUM" && module === "iapi" && has("IAPI") && (
            <IapiPanel session={session} onError={setError} trace={trace} />
          )}
          {session && module !== "browser" && module !== "dump" && (
            ((session.product === "DOCUMENTUM" && DCTM_STUB_IDS.has(module) && !(module === "iapi" && has("IAPI"))) ||
              (session.product === "EXTENDED_ECM" && XECM_STUB_IDS.has(module))) && (
              <StubPanel module={module} />
            )
          )}
        </div>
      </div>
      <div className="log">
        <button type="button" className="log-toggle" onClick={() => setLogOpen((o) => !o)}>
          {logOpen ? "Hide activity" : log[log.length - 1] || "Activity log"}
        </button>
        {logOpen && <div className="log-body">{log.join("\n")}</div>}
      </div>
      {session && viewer && (
        <DocumentViewer
          sessionId={session.id}
          objectId={viewer.id}
          fileName={viewer.name}
          onClose={() => setViewer(null)}
          onError={setError}
        />
      )}
      {showProfile && (
        <ProfileModal
          profiles={profiles}
          onClose={() => {
            setShowProfile(false);
            refreshProfiles();
          }}
        />
      )}
    </div>
  );
}

function DqlStudio({
  session,
  onDump,
  onError,
  trace,
}: {
  session: SessionView;
  onDump: (id: string) => void;
  onError: (s: string) => void;
  trace: (s: string) => void;
}) {
  const [dql, setDql] = useState("SELECT r_object_id, object_name, r_object_type FROM dm_document");
  const [result, setResult] = useState<GridResult | null>(null);
  const [history, setHistory] = useState<string[]>([]);
  const [types, setTypes] = useState<TypeInfo[]>([]);
  const [filter, setFilter] = useState("");
  const [queryName, setQueryName] = useState("");
  const [naming, setNaming] = useState(false);

  useEffect(() => {
    api.types(session.id)
      .then((t) => setTypes(t.types || []))
      .catch(() => undefined);
  }, [session.id]);

  const run = async () => {
    try {
      const res = await api.dql(session.id, dql);
      setResult(res);
      setHistory((h) => [dql, ...h.filter((x) => x !== dql)].slice(0, 40));
      trace(`DQL ${res.rowCount} rows ${res.elapsedMs}ms`);
    } catch (e) {
      onError(String(e));
    }
  };

  const onMount = useCallback(
    (editor: any, monaco: any) => {
      monaco.languages.registerCompletionItemProvider("dql", {
        triggerCharacters: [" ", ".", ","],
        provideCompletionItems: (model: any, position: any) => {
          const text = model.getValue().slice(0, model.getOffsetAt(position)).toUpperCase();
          const suggestions: any[] = [];
          const kw = [...DQL_KEYWORDS, ...DQL_FUNCTIONS].map((k) => ({
            label: k,
            kind: monaco.languages.CompletionItemKind.Keyword,
            insertText: k,
          }));
          if (text.includes("FROM") && !text.trim().endsWith("FROM")) {
            types.forEach((t) =>
              suggestions.push({
                label: t.name,
                kind: monaco.languages.CompletionItemKind.Class,
                insertText: t.name,
              })
            );
          }
          const fromMatch = /FROM\s+([A-Z0-9_]+)/i.exec(text);
          if (fromMatch) {
            const t = types.find((x) => x.name.toLowerCase() === fromMatch[1].toLowerCase());
            t?.attributes.forEach((a) =>
              suggestions.push({
                label: a,
                kind: monaco.languages.CompletionItemKind.Field,
                insertText: a,
              })
            );
          }
          return { suggestions: suggestions.length ? suggestions : kw };
        },
      });
    },
    [types]
  );

  const idIndex = result?.columns.findIndex((c) => c.toLowerCase() === "r_object_id") ?? -1;

  return (
    <div className="panel fill">
      <div className="page-toolbar">
        <button className="primary" onClick={run}>
          Run
        </button>
        {naming ? (
          <>
            <input
              placeholder="Query name"
              value={queryName}
              onChange={(e) => setQueryName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && queryName.trim()) {
                  api.saveQuery(queryName.trim(), dql, session.product);
                  setNaming(false);
                  setQueryName("");
                  trace(`Saved query ${queryName.trim()}`);
                }
                if (e.key === "Escape") setNaming(false);
              }}
            />
            <button
              className="primary"
              type="button"
              onClick={() => {
                if (!queryName.trim()) return;
                api.saveQuery(queryName.trim(), dql, session.product);
                setNaming(false);
                setQueryName("");
                trace(`Saved query ${queryName.trim()}`);
              }}
            >
              Save query
            </button>
            <button type="button" onClick={() => setNaming(false)}>
              Cancel
            </button>
          </>
        ) : (
          <button type="button" onClick={() => setNaming(true)}>
            Save query
          </button>
        )}
        <select onChange={(e) => e.target.value && setDql(e.target.value)} defaultValue="">
          <option value="">History…</option>
          {history.map((h) => (
            <option key={h} value={h}>
              {h.slice(0, 80)}
            </option>
          ))}
        </select>
        <input
          style={{ flex: 1, minWidth: 180 }}
          placeholder="Filter results"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        />
      </div>
      <div className="editor">
        <Editor
          height="168px"
          language="dql"
          theme="workbench"
          value={dql}
          onChange={(v) => setDql(v ?? "")}
          onMount={(editor, monaco) => {
            applyWorkbenchTheme(monaco);
            onMount(editor, monaco);
          }}
          options={{ minimap: { enabled: false }, fontSize: 13, padding: { top: 8, bottom: 8 } }}
        />
      </div>
      {result && (
        <ResultGrid
          result={result}
          filter={filter}
          onDump={onDump}
          onCell={(ri, ci, value) => {
            if (ci === idIndex || result.columns[ci]?.toLowerCase().includes("object_id")) onDump(value);
          }}
        />
      )}
    </div>
  );
}

function columnWidthCh(name: string, rows: string[][], index: number) {
  let max = name.length;
  for (let i = 0; i < Math.min(rows.length, 80); i++) {
    max = Math.max(max, (rows[i][index] || "").length);
  }
  return Math.min(64, Math.max(10, max + 2));
}

function ResultGrid({
  result,
  filter,
  onCell,
  onDump,
}: {
  result: GridResult;
  filter: string;
  onCell: (ri: number, ci: number, value: string) => void;
  onDump?: (id: string) => void;
}) {
  const [wrap, setWrap] = useState(false);
  const [sel, setSel] = useState<{ ri: number; ci: number } | null>(null);
  const [menu, setMenu] = useState<{ x: number; y: number; ri: number; ci: number } | null>(null);
  const rows = result.rows.filter((r) => !filter || r.some((c) => (c || "").toLowerCase().includes(filter.toLowerCase())));
  const widths = result.columns.map((c, i) => columnWidthCh(c, rows, i));
  const idIndex = objectIdColumn(result.columns);
  const selectedId = sel && idIndex >= 0 ? rows[sel.ri]?.[idIndex] : undefined;
  const selectedCell = sel ? rows[sel.ri]?.[sel.ci] : undefined;

  const actions: ObjectAction[] = [
    {
      id: "dump",
      label: "Dump object",
      primary: true,
      disabled: !selectedId,
      run: () => selectedId && onDump?.(selectedId),
    },
    {
      id: "copy-id",
      label: "Copy ID",
      disabled: !selectedId,
      run: () => selectedId && void navigator.clipboard.writeText(selectedId),
    },
    {
      id: "copy-cell",
      label: "Copy cell",
      disabled: selectedCell == null,
      run: () => selectedCell != null && void navigator.clipboard.writeText(selectedCell),
    },
  ];

  return (
    <div className="result-grid">
      <div className="result-toolbar">
        <span className="result-meta">
          {rows.length} rows · {result.columns.length} columns
          {result.elapsedMs != null ? ` · ${result.elapsedMs} ms` : ""}
        </span>
        <button type="button" className={wrap ? "primary" : ""} onClick={() => setWrap((w) => !w)}>
          {wrap ? "Clip cells" : "Wrap cells"}
        </button>
      </div>
      <ActionBar actions={actions} hint="Select a row, then Dump. Double-click an id still opens dump." />
      <div className={`result-scroll${wrap ? " wrap-cells" : ""}`}>
        <table>
          <thead>
            <tr>
              {result.columns.map((c, i) => (
                <th key={`${c}-${i}`} style={{ minWidth: `${widths[i]}ch` }} title={c}>
                  {c}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, ri) => (
              <tr key={ri} className={sel?.ri === ri ? "sel" : ""}>
                {row.map((cell, ci) => {
                  const idish =
                    result.columns[ci]?.toLowerCase().includes("object_id") ||
                    result.columns[ci]?.toLowerCase().endsWith("_id");
                  return (
                    <td
                      key={ci}
                      className={idish ? "cell-id" : undefined}
                      title={cell}
                      style={{ minWidth: `${widths[ci]}ch` }}
                      onClick={() => setSel({ ri, ci })}
                      onDoubleClick={() => onCell(ri, ci, cell)}
                      onContextMenu={(e) => {
                        e.preventDefault();
                        setSel({ ri, ci });
                        setMenu({ x: e.clientX, y: e.clientY, ri, ci });
                      }}
                    >
                      {cell}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          actions={[
            {
              id: "dump",
              label: "Dump object",
              disabled: idIndex < 0 || !rows[menu.ri]?.[idIndex],
              run: () => {
                const id = rows[menu.ri]?.[idIndex];
                if (id) onDump?.(id);
              },
            },
            {
              id: "copy-id",
              label: "Copy ID",
              disabled: idIndex < 0 || !rows[menu.ri]?.[idIndex],
              run: () => {
                const id = rows[menu.ri]?.[idIndex];
                if (id) void navigator.clipboard.writeText(id);
              },
            },
            {
              id: "copy-cell",
              label: "Copy cell",
              run: () => void navigator.clipboard.writeText(rows[menu.ri]?.[menu.ci] || ""),
            },
          ]}
          onClose={() => setMenu(null)}
        />
      )}
    </div>
  );
}

function jobStatusClass(status?: string) {
  switch ((status || "").toUpperCase()) {
    case "SUCCESS":
      return "success";
    case "FAILED":
      return "failed";
    case "RUNNING":
      return "running";
    case "INACTIVE":
      return "inactive";
    default:
      return "scheduled";
  }
}

function Jobs({
  session,
  onDump,
  onView,
  onError,
  trace,
}: {
  session: SessionView;
  onDump: (id: string) => void;
  onView: (id: string, name?: string) => void;
  onError: (s: string) => void;
  trace: (s: string) => void;
}) {
  const [jobs, setJobs] = useState<JobInfo[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<JobDetail | null>(null);
  const load = () =>
    api.jobs(session.id)
      .then((j) => setJobs(j.jobs || []))
      .catch((e) => onError(String(e)));
  const loadDetail = (id: string) => {
    setSelected(id);
    api.jobDetail(session.id, id)
      .then(setDetail)
      .catch((e) => onError(String(e)));
  };
  useEffect(() => {
    load();
  }, [session.id]);
  const xecm = session.product === "EXTENDED_ECM";
  return (
    <div className="panel fill">
      <div className="page-toolbar">
        <button
          onClick={() => {
            load();
            if (selected) loadDetail(selected);
          }}
        >
          Refresh
        </button>
        {xecm && (
          <span className="muted">Content Server scheduled agents (Notification, index, ECMLink sync, …)</span>
        )}
      </div>
      <div className="job-split">
        <div className="grid-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Status</th>
                <th>Last return</th>
                <th>Last run</th>
                <th>Next</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {jobs.map((j) => (
                <tr
                  key={j.id}
                  className={selected === j.id ? "sel" : ""}
                  onClick={() => loadDetail(j.id)}
                  onDoubleClick={() => onDump(j.id)}
                >
                  <td>{j.objectName}</td>
                  <td>
                    <span className={`pill ${jobStatusClass(j.status)}`}>{j.status || (j.inactive ? "INACTIVE" : "SCHEDULED")}</span>
                  </td>
                  <td>{j.lastReturn ?? ""}</td>
                  <td>{j.lastCompletionDate}</td>
                  <td>{j.nextInvocationDate}</td>
                  <td>
                    <button
                      onClick={async (e) => {
                        e.stopPropagation();
                        if (!confirm("Run job now?")) return;
                        try {
                          await api.runJob(session.id, j.id);
                          trace(`Run ${j.objectName}`);
                          load();
                          loadDetail(j.id);
                        } catch (err) {
                          onError(String(err));
                        }
                      }}
                    >
                      Run now
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="job-detail">
          {!detail && <div className="muted">Select a job to see status and reports.</div>}
          {detail && (
            <>
              <div className="row">
                <strong>{detail.info.objectName}</strong>
                <span className={`pill ${jobStatusClass(detail.info.status)}`}>{detail.info.status}</span>
                <button onClick={() => onDump(detail.info.id)}>Dump</button>
              </div>
              <div className="muted">{detail.info.currentStatus || "No status message"}</div>
              <div>{xecm ? "Thread" : "Method"}: {detail.info.methodName}</div>
              <div>Interval: {detail.info.runInterval}</div>
              <div>Last completion: {detail.info.lastCompletionDate || "—"}</div>
              <div>Next invocation: {detail.info.nextInvocationDate || "—"}</div>
              <div>
                Last return: {detail.info.lastReturn || "—"} · Inactive: {String(detail.info.inactive)} · Run now:{" "}
                {String(detail.info.runNow)}
              </div>
              <strong>Reports</strong>
              {detail.reports.length === 0 && (
                <div className="muted">
                  {xecm
                    ? "No agent log reports for this job."
                    : "No reports in /System/Sysadmin/Reports for this job."}
                </div>
              )}
              {detail.reports.length > 0 && (
                <table>
                  <thead>
                    <tr>
                      <th>Report</th>
                      <th>Created</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.reports.map((r) => (
                      <tr key={r.id}>
                        <td>{r.objectName}</td>
                        <td>{r.created}</td>
                        <td>
                          <button onClick={() => onView(r.id, r.objectName)}>View</button>
                          <button onClick={() => onDump(r.id)}>Dump</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function SearchPanel({
  session,
  onDump,
  onError,
  trace,
}: {
  session: SessionView;
  onDump: (id: string) => void;
  onError: (s: string) => void;
  trace: (s: string) => void;
}) {
  const [q, setQ] = useState("");
  const [result, setResult] = useState<GridResult | null>(null);
  return (
    <div className="panel fill">
      <div className="page-toolbar">
        <input style={{ flex: 1 }} placeholder="Search by name or node id" value={q} onChange={(e) => setQ(e.target.value)} />
        <button
          className="primary"
          onClick={async () => {
            try {
              const res = await api.search(session.id, q);
              setResult(res);
              trace(`Search ${res.rowCount}`);
            } catch (e) {
              onError(String(e));
            }
          }}
        >
          Search
        </button>
      </div>
      {result && <ResultGrid result={result} filter="" onDump={onDump} onCell={(_r, _c, v) => onDump(v)} />}
    </div>
  );
}

function Workspaces({
  session,
  onDump,
  onError,
}: {
  session: SessionView;
  onDump: (id: string) => void;
  onError: (s: string) => void;
}) {
  const [rows, setRows] = useState<BusinessWorkspace[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [menu, setMenu] = useState<{ x: number; y: number; id: string } | null>(null);
  useEffect(() => {
    api.workspaces(session.id).then(setRows).catch((e) => onError(String(e)));
  }, [session.id]);
  const current = rows.find((w) => w.id === selected);
  const actions: ObjectAction[] = [
    {
      id: "dump",
      label: "Details",
      primary: true,
      disabled: !current,
      run: () => current && onDump(current.id),
    },
    {
      id: "copy-id",
      label: "Copy ID",
      disabled: !current,
      run: () => current && void navigator.clipboard.writeText(current.id),
    },
  ];
  return (
    <div className="panel fill">
      <div className="muted">Create-via-ECMLink is stubbed. SAP-linked workspaces are read-mostly.</div>
      <ActionBar actions={actions} hint="Select a workspace, then Details." />
      <div className="grid-wrap">
        <table>
          <thead>
            <tr>
              <th>Id</th>
              <th>Name</th>
              <th>System</th>
              <th>BO type</th>
              <th>BO id</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((w) => (
              <tr
                key={w.id}
                className={selected === w.id ? "sel" : ""}
                onClick={() => setSelected(w.id)}
                onDoubleClick={() => onDump(w.id)}
                onContextMenu={(e) => {
                  e.preventDefault();
                  setSelected(w.id);
                  setMenu({ x: e.clientX, y: e.clientY, id: w.id });
                }}
              >
                <td>{w.id}</td>
                <td>{w.name}</td>
                <td>{w.extSystemId}</td>
                <td>{w.boType}</td>
                <td>{w.boId}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          actions={[
            { id: "dump", label: "Details", run: () => onDump(menu.id) },
            { id: "copy-id", label: "Copy ID", run: () => void navigator.clipboard.writeText(menu.id) },
          ]}
          onClose={() => setMenu(null)}
        />
      )}
    </div>
  );
}

function AttrSection({
  title,
  hint,
  attrs,
  system,
  onChange,
}: {
  title: string;
  hint: string;
  attrs: AttributeValue[];
  system?: boolean;
  onChange: (name: string, value: string) => void;
}) {
  return (
    <div className={`dump-section${system ? " system" : ""}`}>
      <div className="dump-section-head">
        {title}
        <span className={`pill ${system ? "inactive" : "scheduled"}`}>{attrs.length}</span>
        <span className="section-hint">{hint}</span>
      </div>
      <div className="grid-wrap dump-grid">
        {attrs.length === 0 ? (
          <div className="empty-attrs">None</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Attribute</th>
                <th>Value</th>
              </tr>
            </thead>
            <tbody>
              {attrs.map((a) => (
                <tr key={a.name}>
                  <td className="attr-name">
                    <code>{a.name}</code>
                    {a.repeating && <span className="pill inactive">rep</span>}
                    {a.readOnly && <span className="pill inactive">ro</span>}
                  </td>
                  <td>
                    <input
                      disabled={a.readOnly}
                      value={a.values.join(", ")}
                      onChange={(e) => onChange(a.name, e.target.value)}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function dumpHasContent(dump: ObjectDump) {
  const type = (dump.typeName || "").toLowerCase();
  if (type.includes("document")) return true;
  if (dump.extra?.subtype === "144") return true;
  const pages = dump.attributes?.find((a) => a.name === "r_page_cnt");
  if (pages?.values[0] && pages.values[0] !== "0") return true;
  const fmt = dump.attributes?.find((a) => a.name === "a_content_type");
  return !!(fmt && fmt.values[0]);
}

const OTCS_SYSTEM_ATTRS = new Set([
  "id",
  "type",
  "type_name",
  "parent_id",
  "volume_id",
  "original_id",
  "create_date",
  "create_user_id",
  "modify_date",
  "modify_user_id",
  "owner_user_id",
  "owner_group_id",
  "reserved",
  "reserved_user_id",
  "reserved_date",
  "size",
  "size_formatted",
  "mime_type",
  "file_type",
  "version_number",
  "container",
  "container_size",
  "guid",
  "template_id",
  "hidden",
  "favorite",
  "deleted",
  "cell_storage_id",
  "advanced_versioning",
]);

function isSystemAttribute(name: string, product?: Product, readOnly?: boolean) {
  const n = (name || "").toLowerCase();
  if (product === "EXTENDED_ECM") {
    if (OTCS_SYSTEM_ATTRS.has(n)) return true;
    if (n.startsWith("wnd_")) return true;
    if (n.endsWith("_multilingual")) return true;
    if (readOnly && n !== "name") return true;
    return false;
  }
  return /^(r_|i_|a_|_)/.test(n);
}

function partitionAttributes(dump: ObjectDump, product?: Product) {
  const custom: AttributeValue[] = [];
  const system: AttributeValue[] = [];
  for (const attr of dump.attributes || []) {
    if (isSystemAttribute(attr.name, product, attr.readOnly)) system.push(attr);
    else custom.push(attr);
  }
  return { custom, system };
}

function DumpWorkspace({
  session,
  dumps,
  active,
  setActive,
  setDumps,
  onView,
  onError,
  onBack,
  onCloseTab,
  backLabel,
  trace,
}: {
  session: SessionView | null;
  dumps: DumpTab[];
  active: string | null;
  setActive: (id: string) => void;
  setDumps: (fn: (d: DumpTab[]) => DumpTab[]) => void;
  onView: (id: string, name?: string) => void;
  onError: (s: string) => void;
  onBack: () => void;
  onCloseTab: (id: string) => void;
  backLabel: string;
  trace: (s: string) => void;
}) {
  const tab = dumps.find((d) => d.id === active) ?? dumps[dumps.length - 1];
  if (!tab) {
    return (
      <div className="panel muted">
        <div className="row">
          <button type="button" className="back" onClick={onBack}>
            ← {backLabel}
          </button>
        </div>
        {session?.product === "EXTENDED_ECM"
          ? "No node details open. Open a node from the browser, search, or workspaces."
          : "No dump tabs. Open an object from the repository browser or a DQL result."}
      </div>
    );
  }
  const dump = tab.dump;
  const xecm = session?.product === "EXTENDED_ECM";
  const { custom, system } = partitionAttributes(dump, session?.product);
  const onChangeAttr = (name: string, value: string) => {
    const next = {
      ...dump,
      attributes: dump.attributes.map((x) =>
        x.name === name ? { ...x, values: value.split(",").map((s) => s.trim()) } : x
      ),
    };
    setDumps((tabs) => tabs.map((t) => (t.id === dump.id ? { id: dump.id, dump: next } : t)));
  };
  return (
    <div className="panel fill">
      <div className="row dump-nav">
        <button type="button" className="back" onClick={onBack}>
          ← {backLabel}
        </button>
        <div className="tabs">
          {dumps.map((d) => (
            <button
              key={d.id}
              type="button"
              className={`dump-tab${d.id === tab.id ? " primary" : ""}`}
              onClick={() => setActive(d.id)}
            >
              {d.dump.objectName || d.id}
              <span
                className="tab-x"
                title="Close"
                onClick={(e) => {
                  e.stopPropagation();
                  onCloseTab(d.id);
                }}
              >
                ×
              </span>
            </button>
          ))}
        </div>
      </div>
      {xecm && dump.sapLinked && (
        <div className="warn">SAP-linked Business Workspace — mutations are blocked.</div>
      )}
      <div className="object-hero">
        <div>
          <div className="object-hero-name">{dump.objectName || dump.id}</div>
          <div className="object-hero-meta">
            <span className="pill scheduled">{dump.typeName}</span>
            <code>{dump.id}</code>
          </div>
        </div>
      </div>
      <div className="dump-sections">
        <AttrSection
          title="Custom"
          hint={xecm ? "Node properties and business fields" : "Type attributes (object_name, title, …)"}
          attrs={custom}
          onChange={onChangeAttr}
        />
        <AttrSection
          title="System"
          hint={xecm ? "Core CS node metadata" : "Repository internals (r_, i_, a_)"}
          attrs={system}
          system
          onChange={onChangeAttr}
        />
      </div>
      {xecm && dump.categories?.length > 0 && (
        <div className="dump-section wide">
          <div className="dump-section-head">
            Categories
            <span className="pill scheduled">{dump.categories.length}</span>
            <span className="section-hint">OTCS category attributes</span>
          </div>
          <div className="grid-wrap dump-grid">
            <table>
              <thead>
                <tr>
                  <th>Category</th>
                  <th>Attribute</th>
                  <th>Value</th>
                </tr>
              </thead>
              <tbody>
                {dump.categories.flatMap((c) =>
                  Object.entries(c.attributes || {}).map(([key, vals]) => (
                    <tr key={`${c.categoryId}-${key}`}>
                      <td>{c.categoryName}</td>
                      <td className="attr-name">
                        <code>{key}</code>
                      </td>
                      <td>{(vals || []).join(", ")}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
      <div className="row">
        <button
          className="primary"
          onClick={async () => {
            if (!session) return;
            if (dump.sapLinked && !confirm("This looks SAP-linked. Continue anyway?")) return;
            try {
              await api.saveDump(session.id, dump);
              trace(`Saved ${dump.id}`);
            } catch (e) {
              onError(String(e));
            }
          }}
        >
          Save
        </button>
        {session && dumpHasContent(dump) && (
          <>
            <button className="primary" onClick={() => onView(dump.id, dump.objectName)}>
              View content
            </button>
            <a href={api.contentUrl(session.id, dump.id, false)}>
              <button type="button">Download</button>
            </a>
          </>
        )}
      </div>
    </div>
  );
}

function IapiPanel({
  session,
  onError,
  trace,
}: {
  session: SessionView;
  onError: (s: string) => void;
  trace: (s: string) => void;
}) {
  const [cmd, setCmd] = useState("dump,c,0900000180000001");
  const [out, setOut] = useState("");
  return (
    <div className="panel">
      <div className="muted">Thin IAPI REPL (mock/live DFC). ScriptRunner JS chaining is stubbed.</div>
      <div className="row">
        <input style={{ flex: 1 }} value={cmd} onChange={(e) => setCmd(e.target.value)} />
        <button
          className="primary"
          onClick={async () => {
            try {
              const r = await api.iapi(session.id, cmd);
              setOut((r.ok ? "" : "ERROR\n") + (r.output || "") + (r.currentId ? `\ncurrent=${r.currentId}` : ""));
              trace(`IAPI ${cmd}`);
            } catch (e) {
              onError(String(e));
            }
          }}
        >
          Exec
        </button>
      </div>
      <textarea readOnly rows={16} value={out} />
    </div>
  );
}

function StubPanel({ module }: { module: string }) {
  const [info, setInfo] = useState<{ title: string; summary: string } | null>(null);
  useEffect(() => {
    api.stub(module).then(setInfo).catch(() => setInfo({ title: module, summary: "Stub" }));
  }, [module]);
  return (
    <div className="panel">
      <h3>{info?.title ?? module}</h3>
      <p>{info?.summary}</p>
      <p className="muted">Coming soon — SPI hook is in place. Consistency fixer is out of scope.</p>
    </div>
  );
}

function ProfileModal({ profiles, onClose }: { profiles: ConnectionProfile[]; onClose: () => void }) {
  const blankDctm: ConnectionProfile = {
    name: "New Documentum",
    product: "DOCUMENTUM",
    protocol: "MOCK_DFC",
    repository: "mock",
    username: "dmadmin",
    reportedVersion: "24.2",
    authMode: "PASSWORD",
  };
  const [draft, setDraft] = useState<ConnectionProfile>(blankDctm);
  const [secret, setSecret] = useState("");
  const dctm = draft.product === "DOCUMENTUM";
  const liveRest = draft.protocol === "DCTM_REST" || draft.protocol === "OTCS_REST";
  const liveDfc = draft.protocol === "LIVE_DFC";
  const mock = draft.protocol === "MOCK_DFC" || draft.protocol === "MOCK_OTCS";
  const stubProto = draft.protocol === "DFS" || draft.protocol === "CWS";
  const showOtds = liveRest;

  const setProduct = (product: ConnectionProfile["product"]) => {
    setDraft({
      ...draft,
      product,
      protocol: product === "DOCUMENTUM" ? "MOCK_DFC" : "MOCK_OTCS",
      restBaseUrl: "",
      cgiRoot: "",
      dfcLibDir: "",
      dfcPropertiesPath: "",
      repository: product === "DOCUMENTUM" ? "mock" : "mock-otcs",
      username: product === "DOCUMENTUM" ? "dmadmin" : "Admin",
      authMode: "PASSWORD",
    });
  };

  return (
    <div className="modal-bg" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Connection profiles</h3>
        <label className="field">
          Existing
          <select
            onChange={(e) => {
              const p = profiles.find((x) => x.id === e.target.value);
              if (p) setDraft(p);
              else setProduct(draft.product);
            }}
          >
            <option value="">— new —</option>
            <optgroup label="Documentum">
              {profiles
                .filter((p) => p.product === "DOCUMENTUM")
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
            </optgroup>
            <optgroup label="Extended ECM">
              {profiles
                .filter((p) => p.product === "EXTENDED_ECM")
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
            </optgroup>
          </select>
        </label>
        <label className="field">
          Name
          <input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
        </label>
        <label className="field">
          Platform
          <select value={draft.product} onChange={(e) => setProduct(e.target.value as ConnectionProfile["product"])}>
            <option value="DOCUMENTUM">Documentum</option>
            <option value="EXTENDED_ECM">Extended ECM (OTCS)</option>
          </select>
        </label>
        <label className="field">
          {dctm ? "Documentum connection" : "Extended ECM connection"}
          <select
            value={draft.protocol}
            onChange={(e) => setDraft({ ...draft, protocol: e.target.value as Protocol })}
          >
            {dctm ? (
              <>
                <option value="MOCK_DFC">Mock DFC (offline)</option>
                <option value="DCTM_REST">Documentum REST</option>
                <option value="LIVE_DFC">Live DFC</option>
                <option value="DFS">DFS (stub)</option>
              </>
            ) : (
              <>
                <option value="MOCK_OTCS">Mock OTCS (offline)</option>
                <option value="OTCS_REST">Content Server REST</option>
                <option value="CWS">CWS (stub)</option>
              </>
            )}
          </select>
        </label>
        {stubProto && (
          <p className="muted">
            {draft.protocol} is stubbed. Pick a mock or REST connection for this platform.
          </p>
        )}
        {!stubProto && (
          <>
            <label className="field">
              Username
              <input value={draft.username ?? ""} onChange={(e) => setDraft({ ...draft, username: e.target.value })} />
            </label>
            {!mock && (
              <label className="field">
                Password / token
                <input type="password" value={secret} onChange={(e) => setSecret(e.target.value)} />
              </label>
            )}
            {dctm && (
              <label className="field">
                Repository / docbase
                <input
                  value={draft.repository ?? ""}
                  onChange={(e) => setDraft({ ...draft, repository: e.target.value })}
                />
              </label>
            )}
            {draft.protocol === "DCTM_REST" && (
              <label className="field">
                Documentum REST base URL
                <input
                  placeholder="https://host:port/dctm-rest"
                  value={draft.restBaseUrl ?? ""}
                  onChange={(e) => setDraft({ ...draft, restBaseUrl: e.target.value })}
                />
              </label>
            )}
            {draft.protocol === "OTCS_REST" && (
              <label className="field">
                OTCS CGI root
                <input
                  placeholder="https://host/otcs/cs.exe"
                  value={draft.cgiRoot ?? ""}
                  onChange={(e) => setDraft({ ...draft, cgiRoot: e.target.value })}
                />
              </label>
            )}
            {liveDfc && (
              <>
                <label className="field">
                  DFC lib directory
                  <input
                    placeholder="C:\\Documentum\\shared"
                    value={draft.dfcLibDir ?? ""}
                    onChange={(e) => setDraft({ ...draft, dfcLibDir: e.target.value })}
                  />
                </label>
                <label className="field">
                  dfc.properties path
                  <input
                    value={draft.dfcPropertiesPath ?? ""}
                    onChange={(e) => setDraft({ ...draft, dfcPropertiesPath: e.target.value })}
                  />
                </label>
              </>
            )}
            {showOtds && (
              <>
                <label className="field">
                  Auth
                  <select
                    value={draft.authMode ?? "PASSWORD"}
                    onChange={(e) => setDraft({ ...draft, authMode: e.target.value })}
                  >
                    <option value="PASSWORD">{dctm ? "HTTP Basic" : "OTCS username / password"}</option>
                    <option value="OTDS_PASSWORD">OTDS password grant</option>
                    <option value="OTDS_BEARER">Stored OTDS bearer</option>
                  </select>
                </label>
                {(draft.authMode === "OTDS_PASSWORD" || draft.authMode === "OTDS_BEARER") && (
                  <label className="field">
                    OTDS URL
                    <input
                      placeholder="https://host/otdsws"
                      value={draft.otdsUrl ?? ""}
                      onChange={(e) => setDraft({ ...draft, otdsUrl: e.target.value })}
                    />
                  </label>
                )}
              </>
            )}
            {mock && (
              <label className="field">
                Reported version
                <input
                  value={draft.reportedVersion ?? "24.2"}
                  onChange={(e) => setDraft({ ...draft, reportedVersion: e.target.value })}
                />
              </label>
            )}
          </>
        )}
        <div className="row">
          <button
            className="primary"
            onClick={async () => {
              const cleaned: ConnectionProfile = {
                ...draft,
                restBaseUrl: dctm ? draft.restBaseUrl : undefined,
                dfcLibDir: dctm ? draft.dfcLibDir : undefined,
                dfcPropertiesPath: dctm ? draft.dfcPropertiesPath : undefined,
                cgiRoot: dctm ? undefined : draft.cgiRoot,
              };
              await api.saveProfile(cleaned, secret || undefined);
              onClose();
            }}
          >
            Save
          </button>
          <button onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}
