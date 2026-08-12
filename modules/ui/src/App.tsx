import { useCallback, useEffect, useMemo, useState } from "react";
import { ActionBar, ContextMenu, objectIdColumn, type ObjectAction } from "./ActionMenu";
import { api } from "./api";
import { aclPeekDql, entityLinkForAttribute, iapiTemplates } from "./entityLinks";
import { grammarSummary } from "./grammarMarkers";
import { QuickOpen, useQuickOpenShortcut } from "./QuickOpen";
import { useIdleGrammarCheck } from "./useIdleGrammarCheck";
import { Browser } from "./Browser";
import { ErrorPanel } from "./ErrorPanel";
import { ExecutionHistoryDrawer, recordExecution } from "./ExecutionHistoryDrawer";
import { RestExplorer } from "./RestExplorer";
import { DqlTextEditor } from "./DqlTextEditor";
import { ModuleErrorBoundary } from "./ModuleErrorBoundary";
import { NavGlyph } from "./NavGlyph";
import { ProductLockup, ProductLogo } from "./ProductLogo";
import { SessionStrip } from "./SessionStrip";
import {
  documentumFeatureChips,
  hasCap,
  isDfcProtocol,
  isMockProtocol,
  isMutatingDql,
  isRestProtocol,
  unavailableReason,
  xecmFeatureChips,
} from "./capabilities";
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
  SavedQuery,
  SessionView,
  TypeInfo,
} from "./types";

type NavGroup = "workspace" | "query" | "ops" | "more";
type NavItem = {
  id: string;
  label: string;
  icon: string;
  group: NavGroup;
  /** Capability required to use this module. */
  cap?: string;
  /** Needs MOCK_DFC or LIVE_DFC. */
  requiresDfc?: boolean;
  /** Needs DCTM_REST or OTCS_REST. */
  requiresRest?: boolean;
  stub?: boolean;
  dump?: boolean;
};

type ResolvedNavItem = NavItem & { disabled: boolean; reason?: string };

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
  { id: "dump", label: "Dump", icon: "inspect", group: "ops", dump: true, cap: "OBJECT_READ" },
  { id: "iapi", label: "IAPI", icon: "terminal", group: "more", cap: "IAPI", requiresDfc: true },
  { id: "scriptrunner", label: "ScriptRunner", icon: "code", group: "more", stub: true, requiresDfc: true, cap: "IAPI" },
  { id: "rest-explorer", label: "REST explorer", icon: "search", group: "more" },
  { id: "dfs", label: "DFS", icon: "workspace", group: "more", stub: true, cap: "DFS_INVOKE" },
  // MVP Phase 3 uses DQL peek queries only, so gate behind DQL_SELECT.
  { id: "acl", label: "ACLs", icon: "inspect", group: "more", cap: "DQL_SELECT", requiresDfc: true },
  { id: "users", label: "Users / groups", icon: "workspace", group: "more", cap: "DQL_SELECT", requiresDfc: true },
  { id: "workflows", label: "Workflows", icon: "clock", group: "more", cap: "DQL_SELECT", requiresDfc: true },
  { id: "otds-sso", label: "OTDS SSO", icon: "search", group: "more", stub: true, cap: "OTDS_AUTH" },
];

const XECM_NAV: NavItem[] = [
  { id: "browser", label: "Browse", icon: "folder", group: "workspace", cap: "BROWSE" },
  { id: "workspaces", label: "Workspaces", icon: "workspace", group: "workspace", cap: "BUSINESS_WORKSPACE" },
  { id: "search", label: "Search", icon: "search", group: "query", cap: "CS_SEARCH" },
  { id: "jobs", label: "Jobs", icon: "clock", group: "ops", cap: "JOB_LIST" },
  { id: "dump", label: "Details", icon: "inspect", group: "ops", dump: true, cap: "OBJECT_READ" },
  { id: "rest-explorer", label: "REST explorer", icon: "search", group: "more" },
  { id: "cws", label: "CWS", icon: "code", group: "more", stub: true, cap: "CWS_INVOKE" },
  { id: "ecmlink", label: "ECMLink create", icon: "workspace", group: "more", stub: true, cap: "BUSINESS_WORKSPACE" },
  { id: "users", label: "Users / groups", icon: "inspect", group: "more", stub: true, cap: "USER_ADMIN" },
  { id: "otds-sso", label: "OTDS SSO", icon: "search", group: "more", stub: true, cap: "OTDS_AUTH" },
];

function resolveNavItem(session: SessionView, item: NavItem): ResolvedNavItem {
  const reasons: string[] = [];
  if (item.requiresDfc && !isDfcProtocol(session.protocol)) {
    reasons.push(unavailableReason(item.cap || "IAPI", session.protocol));
  }
  if (item.requiresRest && !isRestProtocol(session.protocol)) {
    reasons.push(`Needs a REST connection (current: ${session.protocol})`);
  }
  if (item.cap && !hasCap(session, item.cap)) {
    reasons.push(unavailableReason(item.cap, session.protocol));
  }
  if (item.dump && !hasCap(session, "OBJECT_READ") && !hasCap(session, "BROWSE")) {
    reasons.push(unavailableReason("OBJECT_READ", session.protocol));
  }
  const disabled = reasons.length > 0;
  return {
    ...item,
    disabled,
    reason: reasons[0],
    stub: !!item.stub || disabled,
  };
}

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
  const [quickOpen, setQuickOpen] = useState(false);
  const [recentDumpIds, setRecentDumpIds] = useState<string[]>([]);
  const [recentQueries, setRecentQueries] = useState<string[]>([]);
  const [queryToLoad, setQueryToLoad] = useState<string | null>(null);
  const [iapiCmd, setIapiCmd] = useState("dump,c,0900000180000001");
  const [iapiHistory, setIapiHistory] = useState<string[]>([]);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [lastOperation, setLastOperation] = useState("");

  const caps = session?.capabilities ?? [];
  const has = (c: string) => hasCap(session, c);

  const trace = (msg: string) => setLog((l) => [...l.slice(-200), `${new Date().toLocaleTimeString()} ${msg}`]);

  const openQuickOpen = useCallback(() => setQuickOpen(true), []);
  useQuickOpenShortcut(openQuickOpen, !!session);

  const recordRecentDump = (id: string) => {
    setRecentDumpIds((ids) => [id, ...ids.filter((x) => x !== id)].slice(0, 20));
  };

  const openIapi = (command: string) => {
    setIapiCmd(command);
    setIapiHistory((h) => [command, ...h.filter((x) => x !== command)].slice(0, 40));
    setSeen((s) => (s.iapi ? s : { ...s, iapi: true }));
    setModule("iapi");
    trace(`IAPI ${command}`);
  };

  const refreshQueryHistory = async () => {
    try {
      const entries = await api.queryHistory();
      setRecentQueries(entries.map((e) => e.text));
    } catch {
      // non-fatal
    }
  };

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
      setRecentDumpIds([]);
      void refreshQueryHistory();
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
      recordRecentDump(id);
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
    if (session) void refreshQueryHistory();
  }, [session?.id]);

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
  const nav = useMemo((): ResolvedNavItem[] => {
    if (!session) return [];
    return platformNav.map((item) => resolveNavItem(session, item));
  }, [session, caps.join(",")]);
  const activeNav = nav.find((n) => n.id === module);
  const backLabel = platformNav.find((n) => n.id === returnTo)?.label ?? "Back";
  const dctmProfiles = profiles.filter((p) => p.product === "DOCUMENTUM");
  const xecmProfiles = profiles.filter((p) => p.product === "EXTENDED_ECM");
  const selectedProfile = profiles.find((p) => p.id === profileId);
  const moduleUnavailable = !!session && !!activeNav?.disabled;

  return (
    <ModuleErrorBoundary name="Workbench">
    <div className="app">
      <div className="topbar">
        <span className="brand">
          <img className="brand-mark" src="/logo.png" width={32} height={32} alt="" />
          <span className="brand-text">
            ECM-Dev-Workbench
            <small>Documentum · xECM</small>
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
        {session && (
          <button
            type="button"
            onClick={() => {
              if (session) void api.close(session.id).catch(() => undefined);
              setSession(null);
              setDumps([]);
              setActiveDump(null);
              setViewer(null);
              setSeen({ browser: true });
              setModule("browser");
              setRecentDumpIds([]);
              trace("Disconnected");
            }}
          >
            Disconnect
          </button>
        )}
        <button onClick={() => setShowProfile(true)}>Profiles…</button>
        {session && (
          <button type="button" onClick={() => setHistoryOpen(true)}>
            History
          </button>
        )}
        {session && (
          <button type="button" onClick={openQuickOpen} title="Quick Open (Ctrl+K or Ctrl+P)">
            Quick Open
          </button>
        )}
        {session && has("BROWSE") && isMockProtocol(session.protocol) && (
          <button onClick={() => session && api.resetMock(session.id).then(() => trace("Mock reset"))}>
            Reset mock
          </button>
        )}
        {!session && (
          <span className="badge">
            {selectedProfile ? (
              <>
                <ProductLogo product={selectedProfile.product} size={18} />
                Ready: {selectedProfile.product === "EXTENDED_ECM" ? "Extended ECM" : "Documentum"} · {selectedProfile.name}
                <span className="cap-pill">
                  {isDfcProtocol(selectedProfile.protocol)
                    ? "DFC"
                    : isRestProtocol(selectedProfile.protocol)
                      ? "REST"
                      : selectedProfile.protocol}
                </span>
              </>
            ) : (
              "Select a Documentum or Extended ECM profile"
            )}
          </span>
        )}
        {session && <SessionStrip session={session} />}
      </div>
      {error && (
        <ErrorPanel error={error} session={session} lastOperation={lastOperation} onDismiss={() => setError("")} />
      )}
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
                    type="button"
                    className={`${module === n.id ? "active" : ""} ${n.disabled ? "disabled" : ""} ${n.stub && !n.disabled ? "stub" : ""}`}
                    title={n.disabled ? n.reason : n.stub ? "Coming soon" : undefined}
                    aria-disabled={n.disabled}
                    onClick={() => goModule(n.id)}
                  >
                    <span className="nav-ico">
                      <NavGlyph name={n.icon} />
                    </span>
                    <span className="nav-label">{n.label}</span>
                    {n.disabled ? <span className="nav-lock">off</span> : null}
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
                <img className="landing-logo" src="/logo.png" width={88} height={88} alt="ECM-Dev-Workbench" />
                <p className="eyebrow">ECM-Dev-Workbench</p>
                <h2>Connect to a repository</h2>
                <p className="lede">
                  Documentum and Extended ECM are separate products. Connect to one profile — the workbench only shows
                  that platform.
                </p>
              </header>
              <div className="landing">
                <div className="card dctm-card">
                  <ProductLockup product="DOCUMENTUM" />
                  <FeatureChipList
                    chips={documentumFeatureChips(
                      (dctmProfiles.find((p) => p.id === profileId) || dctmProfiles[0])?.protocol || "MOCK_DFC"
                    )}
                  />
                  <div className="card-actions">
                    {dctmProfiles.map((p) => (
                      <button key={p.id} className="primary" onClick={() => connect(p.id)}>
                        {p.name}
                        <small className="btn-proto">{isDfcProtocol(p.protocol) ? "DFC" : isRestProtocol(p.protocol) ? "REST" : p.protocol}</small>
                      </button>
                    ))}
                  </div>
                </div>
                <div className="card xecm-card">
                  <ProductLockup product="EXTENDED_ECM" />
                  <FeatureChipList
                    chips={xecmFeatureChips(
                      (xecmProfiles.find((p) => p.id === profileId) || xecmProfiles[0])?.protocol || "MOCK_OTCS"
                    )}
                  />
                  <div className="card-actions">
                    {xecmProfiles.map((p) => (
                      <button key={p.id} className="primary" onClick={() => connect(p.id)}>
                        {p.name}
                        <small className="btn-proto">{isRestProtocol(p.protocol) ? "REST" : isMockProtocol(p.protocol) ? "mock" : p.protocol}</small>
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}
          {session && moduleUnavailable && activeNav && (
            <UnavailablePanel
              title={activeNav.label}
              reason={activeNav.reason || unavailableReason(activeNav.cap || activeNav.label, session.protocol)}
              protocol={session.protocol}
              capabilities={session.capabilities}
            />
          )}
          {session && (seen.browser || module === "browser") && (
            <div className="workspace" hidden={module !== "browser"}>
              <Browser session={session} onDump={openDump} onView={openView} onError={setError} trace={trace} />
            </div>
          )}
          {session?.product === "DOCUMENTUM" && has("DQL_SELECT") && (seen.dql || module === "dql") && (
            <div className="workspace" hidden={module !== "dql"}>
              <ModuleErrorBoundary name="DQL">
                <DqlStudio
                  session={session}
                  onDump={openDump}
                  onError={setError}
                  trace={trace}
                  queryToLoad={queryToLoad}
                  onQueryLoaded={() => setQueryToLoad(null)}
                  onHistoryChange={refreshQueryHistory}
                  onOpenIapi={has("IAPI") ? openIapi : undefined}
                />
              </ModuleErrorBoundary>
            </div>
          )}
          {session && has("JOB_LIST") && (seen.jobs || module === "jobs") && (
            <div className="workspace" hidden={module !== "jobs"}>
              <Jobs session={session} onDump={openDump} onView={openView} onError={setError} trace={trace} />
            </div>
          )}
          {session?.product === "EXTENDED_ECM" && has("CS_SEARCH") && (seen.search || module === "search") && (
            <div className="workspace" hidden={module !== "search"}>
              <SearchPanel session={session} onDump={openDump} onError={setError} trace={trace} />
            </div>
          )}
          {session?.product === "EXTENDED_ECM" &&
            has("BUSINESS_WORKSPACE") &&
            (seen.workspaces || module === "workspaces") && (
              <div className="workspace" hidden={module !== "workspaces"}>
                <Workspaces session={session} onDump={openDump} onError={setError} />
              </div>
            )}
          {session && (seen.dump || module === "dump") && (
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
                onOpenDump={openDump}
                onOpenIapi={has("IAPI") ? openIapi : undefined}
                onLoadQuery={(q) => {
                  setQueryToLoad(q);
                  goModule("dql");
                }}
              />
            </div>
          )}
          {session && (seen["rest-explorer"] || module === "rest-explorer") && (
            <div className="workspace" hidden={module !== "rest-explorer"}>
              <ModuleErrorBoundary name="REST Explorer">
                <RestExplorer session={session} onError={setError} trace={trace} />
              </ModuleErrorBoundary>
            </div>
          )}
          {session?.product === "DOCUMENTUM" && (seen.iapi || module === "iapi") && has("IAPI") && !moduleUnavailable && (
            <div className="workspace" hidden={module !== "iapi"}>
              <ModuleErrorBoundary name="IAPI">
                <IapiPanel
                  session={session}
                  cmd={iapiCmd}
                  setCmd={setIapiCmd}
                  history={iapiHistory}
                  onError={setError}
                  trace={trace}
                  onOpenDump={openDump}
                />
              </ModuleErrorBoundary>
            </div>
          )}
          {session?.product === "DOCUMENTUM" && has("DQL_SELECT") && (seen.acl || module === "acl") && (
            <div className="workspace" hidden={module !== "acl"}>
              <ModuleErrorBoundary name="ACLs">
                <AclPanel
                  session={session}
                  onDump={openDump}
                  onError={setError}
                  trace={trace}
                  canIapi={has("IAPI")}
                  onOpenIapi={has("IAPI") ? openIapi : undefined}
                />
              </ModuleErrorBoundary>
            </div>
          )}
          {session?.product === "DOCUMENTUM" && has("DQL_SELECT") && (seen.users || module === "users") && (
            <div className="workspace" hidden={module !== "users"}>
              <ModuleErrorBoundary name="Users / Groups">
                <UsersGroupsPanel
                  session={session}
                  onDump={openDump}
                  onError={setError}
                  trace={trace}
                  canIapi={has("IAPI")}
                  onOpenIapi={has("IAPI") ? openIapi : undefined}
                />
              </ModuleErrorBoundary>
            </div>
          )}
          {session?.product === "DOCUMENTUM" && has("DQL_SELECT") && (seen.workflows || module === "workflows") && (
            <div className="workspace" hidden={module !== "workflows"}>
              <ModuleErrorBoundary name="Workflows">
                <WorkflowsPanel
                  session={session}
                  onDump={openDump}
                  onError={setError}
                  trace={trace}
                  canIapi={has("IAPI")}
                  onOpenIapi={has("IAPI") ? openIapi : undefined}
                />
              </ModuleErrorBoundary>
            </div>
          )}
          {session &&
            !moduleUnavailable &&
            activeNav?.stub &&
            !activeNav.disabled &&
            module !== "browser" &&
            module !== "dump" &&
            module !== "dql" &&
            module !== "jobs" &&
            module !== "search" &&
            module !== "workspaces" &&
            module !== "rest-explorer" &&
            !(module === "iapi" && has("IAPI")) && <StubPanel module={module} />}
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
      {session && (
        <ExecutionHistoryDrawer
          open={historyOpen}
          onClose={() => setHistoryOpen(false)}
          product={session.product}
          onRerun={(entry) => {
            if (entry.kind === "DQL") {
              setQueryToLoad(entry.requestText);
              goModule("dql");
            } else if (entry.kind === "IAPI") {
              openIapi(entry.requestText);
            } else if (entry.kind === "REST") {
              goModule("rest-explorer");
            } else if (entry.kind === "SEARCH") {
              goModule("search");
            }
            setHistoryOpen(false);
          }}
        />
      )}
      {session && (
        <QuickOpen
          open={quickOpen}
          onClose={() => setQuickOpen(false)}
          session={session}
          recentDumpIds={recentDumpIds}
          recentQueries={recentQueries}
          modules={nav.filter((n) => !n.disabled).map((n) => ({ id: n.id, label: n.label, disabled: n.disabled }))}
          onOpenDump={openDump}
          onGoModule={goModule}
          onLoadQuery={(text) => {
            setQueryToLoad(text);
            goModule(session.product === "EXTENDED_ECM" ? "search" : "dql");
          }}
          onOpenIapi={has("IAPI") ? openIapi : undefined}
        />
      )}
    </div>
    </ModuleErrorBoundary>
  );
}

function DqlStudio({
  session,
  onDump,
  onError,
  trace,
  queryToLoad,
  onQueryLoaded,
  onHistoryChange,
  onOpenIapi,
}: {
  session: SessionView;
  onDump: (id: string) => void;
  onError: (s: string) => void;
  trace: (s: string) => void;
  queryToLoad?: string | null;
  onQueryLoaded?: () => void;
  onHistoryChange?: () => void;
  onOpenIapi?: (command: string) => void;
}) {
  const [dql, setDql] = useState("SELECT r_object_id, object_name, r_object_type FROM dm_document");
  const [result, setResult] = useState<GridResult | null>(null);
  const [history, setHistory] = useState<string[]>([]);
  const [saved, setSaved] = useState<SavedQuery[]>([]);
  const [filter, setFilter] = useState("");
  const [queryName, setQueryName] = useState("");
  const [naming, setNaming] = useState(false);
  const { issues: grammarIssues, idle: grammarIdle, checkNow: checkGrammarNow } = useIdleGrammarCheck("dql", dql);
  const grammar = grammarSummary(grammarIssues);
  const canExecute = hasCap(session, "DQL_EXECUTE");
  const mutating = isMutatingDql(dql);
  const runBlocked = mutating && !canExecute;
  const canIapi = hasCap(session, "IAPI");

  useEffect(() => {
    api.queries().then(setSaved).catch(() => setSaved([]));
    api.queryHistory(session.product).then((entries) => setHistory(entries.map((e) => e.text))).catch(() => setHistory([]));
  }, [session.id, session.product]);

  useEffect(() => {
    if (queryToLoad) {
      setDql(queryToLoad);
      onQueryLoaded?.();
    }
  }, [queryToLoad, onQueryLoaded]);

  const run = async () => {
    if (runBlocked) {
      onError(unavailableReason("DQL_EXECUTE", session.protocol));
      return;
    }
    await checkGrammarNow();
    try {
      const res = await api.dql(session.id, dql);
      setResult(res);
      setHistory((h) => [dql, ...h.filter((x) => x !== dql)].slice(0, 40));
      void api.appendQueryHistory(dql, session.product).then(() => onHistoryChange?.());
      trace(`DQL ${res.rowCount} rows ${res.elapsedMs}ms`);
      void recordExecution({
        kind: "DQL",
        product: session.product,
        summary: `${res.rowCount} rows`,
        requestText: dql,
        responseSummary: `${res.rowCount} rows · ${res.elapsedMs}ms`,
        success: true,
        elapsedMs: res.elapsedMs,
      });
    } catch (e) {
      onError(String(e));
      void recordExecution({
        kind: "DQL",
        product: session.product,
        summary: "DQL failed",
        requestText: dql,
        responseSummary: String(e),
        success: false,
      });
    }
  };

  const idIndex = result?.columns.findIndex((c) => c.toLowerCase() === "r_object_id") ?? -1;
  const productSaved = saved.filter((q) => q.product === session.product);

  return (
    <div className="panel fill">
      <div className="page-toolbar">
        <button
          className="primary"
          onClick={run}
          disabled={runBlocked}
          title={runBlocked ? unavailableReason("DQL_EXECUTE", session.protocol) : "Ctrl+Enter"}
        >
          Run
        </button>
        <span
          className={`grammar-status ${grammarIdle ? grammar.kind : "pending"}`}
          title={grammarIdle ? grammarIssues.map((i) => i.message).join("\n") : "Grammar check runs after you pause typing"}
        >
          {grammarIdle ? grammar.text : "…"}
        </span>
        {!canExecute && (
          <span className="cap-note" title={unavailableReason("DQL_EXECUTE", session.protocol)}>
            SELECT only — EXECUTE needs DFC
          </span>
        )}
        {canExecute && isDfcProtocol(session.protocol) && (
          <span className="cap-note ok">DFC — SELECT + EXECUTE</span>
        )}
        {isRestProtocol(session.protocol) && <span className="cap-note">REST — SELECT only</span>}
        {naming ? (
          <>
            <input
              placeholder="Query name"
              value={queryName}
              onChange={(e) => setQueryName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && queryName.trim()) {
                  api.saveQuery(queryName.trim(), dql, session.product).then((q) => {
                    setSaved((s) => [...s.filter((x) => x.id !== q.id), q]);
                    setNaming(false);
                    setQueryName("");
                    trace(`Saved query ${queryName.trim()}`);
                  });
                }
                if (e.key === "Escape") setNaming(false);
              }}
            />
            <button
              className="primary"
              type="button"
              onClick={() => {
                if (!queryName.trim()) return;
                api.saveQuery(queryName.trim(), dql, session.product).then((q) => {
                  setSaved((s) => [...s.filter((x) => x.id !== q.id), q]);
                  setNaming(false);
                  setQueryName("");
                  trace(`Saved query ${queryName.trim()}`);
                });
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
        <select
          onChange={(e) => {
            const id = e.target.value;
            if (!id) return;
            const q = productSaved.find((x) => x.id === id);
            if (q) setDql(q.text);
            e.target.value = "";
          }}
          defaultValue=""
        >
          <option value="">Library…</option>
          {productSaved.map((q) => (
            <option key={q.id} value={q.id}>
              {q.name}
            </option>
          ))}
        </select>
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
        <DqlTextEditor value={dql} onChange={setDql} onRun={run} />
      </div>
      {result ? (
        <ResultGrid
          result={result}
          filter={filter}
          onDump={onDump}
          canIapi={canIapi}
          onOpenIapi={onOpenIapi}
          onCell={(ri, ci, value) => {
            if (ci === idIndex || result.columns[ci]?.toLowerCase().includes("object_id")) onDump(value);
          }}
        />
      ) : (
        <div className="empty-results muted">Run a query to see results here.</div>
      )}
    </div>
  );
}

function AclPanel({
  session,
  onDump,
  onError,
  trace,
  canIapi,
  onOpenIapi,
}: {
  session: SessionView;
  onDump: (id: string) => void;
  onError: (s: string) => void;
  trace: (s: string) => void;
  canIapi?: boolean;
  onOpenIapi?: (command: string) => void;
}) {
  const [aclName, setAclName] = useState("");
  const [domain, setDomain] = useState("docbase");
  const [filter, setFilter] = useState("");
  const [result, setResult] = useState<GridResult | null>(null);

  const run = async () => {
    const name = aclName.trim();
    const dom = domain.trim() || "docbase";
    if (!name) return;
    const q = aclPeekDql(name, dom);
    try {
      const res = await api.dql(session.id, q);
      setResult(res);
      trace(`ACL peek ${res.rowCount} rows ${res.elapsedMs}ms`);
      void recordExecution({
        kind: "DQL",
        product: session.product,
        summary: `${res.rowCount} rows`,
        requestText: q,
        responseSummary: `${res.rowCount} rows · ${res.elapsedMs}ms`,
        success: true,
        elapsedMs: res.elapsedMs,
      });
      void api.appendQueryHistory(q, session.product).catch(() => undefined);
    } catch (e) {
      onError(String(e));
      void recordExecution({
        kind: "DQL",
        product: session.product,
        summary: "ACL peek failed",
        requestText: q,
        responseSummary: String(e),
        success: false,
      });
    }
  };

  const idIndex = result?.columns.findIndex((c) => c.toLowerCase() === "r_object_id") ?? -1;

  return (
    <div className="panel fill">
      <div className="page-toolbar">
        <button type="button" className="primary" onClick={run} disabled={!aclName.trim()}>
          Peek
        </button>
        <input
          style={{ flex: 1, minWidth: 200 }}
          placeholder="ACL name"
          value={aclName}
          onChange={(e) => setAclName(e.target.value)}
        />
        <input style={{ width: 170 }} placeholder="Domain" value={domain} onChange={(e) => setDomain(e.target.value)} />
        <input
          style={{ flex: 1, minWidth: 180 }}
          placeholder="Filter results"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        />
      </div>
      {result ? (
        <ResultGrid
          result={result}
          filter={filter}
          canIapi={canIapi}
          onOpenIapi={onOpenIapi}
          onDump={onDump}
          onCell={(ri, ci, value) => {
            if (ci === idIndex || result.columns[ci]?.toLowerCase().includes("object_id")) onDump(value);
          }}
        />
      ) : (
        <div className="empty-results muted">Enter an ACL name and click Peek.</div>
      )}
    </div>
  );
}

function UsersGroupsPanel({
  session,
  onDump,
  onError,
  trace,
  canIapi,
  onOpenIapi,
}: {
  session: SessionView;
  onDump: (id: string) => void;
  onError: (s: string) => void;
  trace: (s: string) => void;
  canIapi?: boolean;
  onOpenIapi?: (command: string) => void;
}) {
  const [kind, setKind] = useState<"user" | "group">("user");
  const [name, setName] = useState("");
  const [filter, setFilter] = useState("");
  const [result, setResult] = useState<GridResult | null>(null);

  const run = async () => {
    const n = name.trim();
    if (!n) return;
    const dql =
      kind === "user"
        ? `SELECT r_object_id, object_name FROM dm_user WHERE object_name = '${n.replace(/'/g, "''")}'`
        : `SELECT r_object_id, object_name FROM dm_group WHERE object_name = '${n.replace(/'/g, "''")}'`;
    try {
      const res = await api.dql(session.id, dql);
      setResult(res);
      trace(`${kind} peek ${res.rowCount} rows ${res.elapsedMs}ms`);
      void recordExecution({
        kind: "DQL",
        product: session.product,
        summary: `${res.rowCount} rows`,
        requestText: dql,
        responseSummary: `${res.rowCount} rows · ${res.elapsedMs}ms`,
        success: true,
        elapsedMs: res.elapsedMs,
      });
      void api.appendQueryHistory(dql, session.product).catch(() => undefined);
    } catch (e) {
      onError(String(e));
      void recordExecution({
        kind: "DQL",
        product: session.product,
        summary: `${kind} peek failed`,
        requestText: dql,
        responseSummary: String(e),
        success: false,
      });
    }
  };

  const idIndex = result?.columns.findIndex((c) => c.toLowerCase() === "r_object_id") ?? -1;

  return (
    <div className="panel fill">
      <div className="page-toolbar">
        <select value={kind} onChange={(e) => setKind(e.target.value as "user" | "group")} style={{ minWidth: 170 }}>
          <option value="user">User</option>
          <option value="group">Group</option>
        </select>
        <button type="button" className="primary" onClick={run} disabled={!name.trim()}>
          Peek
        </button>
        <input
          style={{ flex: 1, minWidth: 200 }}
          placeholder={kind === "user" ? "User name" : "Group name"}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <input
          style={{ flex: 1, minWidth: 180 }}
          placeholder="Filter results"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        />
      </div>
      {result ? (
        <ResultGrid
          result={result}
          filter={filter}
          canIapi={canIapi}
          onOpenIapi={onOpenIapi}
          onDump={onDump}
          onCell={(ri, ci, value) => {
            if (ci === idIndex || result.columns[ci]?.toLowerCase().includes("object_id")) onDump(value);
          }}
        />
      ) : (
        <div className="empty-results muted">Enter a {kind} name and click Peek.</div>
      )}
    </div>
  );
}

function WorkflowsPanel({
  session,
  onDump,
  onError,
  trace,
  canIapi,
  onOpenIapi,
}: {
  session: SessionView;
  onDump: (id: string) => void;
  onError: (s: string) => void;
  trace: (s: string) => void;
  canIapi?: boolean;
  onOpenIapi?: (command: string) => void;
}) {
  const [name, setName] = useState("");
  const [filter, setFilter] = useState("");
  const [result, setResult] = useState<GridResult | null>(null);

  const run = async () => {
    const n = name.trim();
    if (!n) return;
    const dql = `SELECT r_object_id, object_name FROM dm_activity WHERE object_name = '${n.replace(/'/g, "''")}'`;
    try {
      const res = await api.dql(session.id, dql);
      setResult(res);
      trace(`workflow peek ${res.rowCount} rows ${res.elapsedMs}ms`);
      void recordExecution({
        kind: "DQL",
        product: session.product,
        summary: `${res.rowCount} rows`,
        requestText: dql,
        responseSummary: `${res.rowCount} rows · ${res.elapsedMs}ms`,
        success: true,
        elapsedMs: res.elapsedMs,
      });
      void api.appendQueryHistory(dql, session.product).catch(() => undefined);
    } catch (e) {
      onError(String(e));
      void recordExecution({
        kind: "DQL",
        product: session.product,
        summary: "workflow peek failed",
        requestText: dql,
        responseSummary: String(e),
        success: false,
      });
    }
  };

  const idIndex = result?.columns.findIndex((c) => c.toLowerCase() === "r_object_id") ?? -1;

  return (
    <div className="panel fill">
      <div className="page-toolbar">
        <button type="button" className="primary" onClick={run} disabled={!name.trim()}>
          Peek
        </button>
        <input style={{ flex: 1, minWidth: 200 }} placeholder="Workflow name" value={name} onChange={(e) => setName(e.target.value)} />
        <input
          style={{ flex: 1, minWidth: 180 }}
          placeholder="Filter results"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        />
      </div>
      {result ? (
        <ResultGrid
          result={result}
          filter={filter}
          canIapi={canIapi}
          onOpenIapi={onOpenIapi}
          onDump={onDump}
          onCell={(ri, ci, value) => {
            if (ci === idIndex || result.columns[ci]?.toLowerCase().includes("object_id")) onDump(value);
          }}
        />
      ) : (
        <div className="empty-results muted">Enter a workflow name and click Peek.</div>
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
  canIapi,
  onOpenIapi,
}: {
  result: GridResult;
  filter: string;
  onCell: (ri: number, ci: number, value: string) => void;
  onDump?: (id: string) => void;
  canIapi?: boolean;
  onOpenIapi?: (command: string) => void;
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
    ...(canIapi && selectedId && onOpenIapi
      ? iapiTemplates(selectedId).map((t) => ({
          id: `iapi-${t.label}`,
          label: `IAPI ${t.label}`,
          run: () => onOpenIapi(t.command),
        }))
      : []),
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
            ...(canIapi && onOpenIapi && idIndex >= 0 && rows[menu.ri]?.[idIndex]
              ? iapiTemplates(rows[menu.ri][idIndex]).map((t) => ({
                  id: `iapi-${t.label}`,
                  label: `IAPI ${t.label}`,
                  run: () => onOpenIapi(t.command),
                }))
              : []),
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
  const canRunJobs = hasCap(session, "JOB_RUN");
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
        {!canRunJobs && (
          <span className="cap-note" title={unavailableReason("JOB_RUN", session.protocol)}>
            Run now disabled on this connection
          </span>
        )}
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
                      disabled={!canRunJobs}
                      title={canRunJobs ? undefined : unavailableReason("JOB_RUN", session.protocol)}
                      onClick={async (e) => {
                        e.stopPropagation();
                        if (!canRunJobs) return;
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
              trace(`Search ${res.rowCount} · ${res.elapsedMs}ms`);
              void recordExecution({
                kind: "SEARCH",
                product: session.product,
                summary: `${res.rowCount} rows`,
                requestText: q,
                responseSummary: `${res.rowCount} rows · ${res.elapsedMs}ms`,
                success: true,
                elapsedMs: res.elapsedMs,
              });
            } catch (e) {
              onError(String(e));
              void recordExecution({
                kind: "SEARCH",
                product: session.product,
                summary: "Search failed",
                requestText: q,
                responseSummary: String(e),
                success: false,
              });
            }
          }}
        >
          Search
        </button>
      </div>
      {result ? (
        <ResultGrid result={result} filter="" onDump={onDump} onCell={(_r, _c, v) => onDump(v)} />
      ) : (
        <div className="empty-results muted">Enter a name or node id and click Search.</div>
      )}
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
  editable = true,
  product,
  onChange,
  onOpenDump,
  onOpenIapi,
  onLoadQuery,
}: {
  title: string;
  hint: string;
  attrs: AttributeValue[];
  system?: boolean;
  editable?: boolean;
  product?: Product;
  onChange: (name: string, value: string) => void;
  onOpenDump?: (id: string) => void;
  onOpenIapi?: (command: string) => void;
  onLoadQuery?: (text: string) => void;
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
                    <AttrValueCell
                      attr={a}
                      product={product}
                      editable={editable && !a.readOnly}
                      onChange={onChange}
                      onOpenDump={onOpenDump}
                      onOpenIapi={onOpenIapi}
                      onLoadQuery={onLoadQuery}
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

function AttrValueCell({
  attr,
  product,
  editable,
  onChange,
  onOpenDump,
  onOpenIapi,
  onLoadQuery,
}: {
  attr: AttributeValue;
  product?: Product;
  editable: boolean;
  onChange: (name: string, value: string) => void;
  onOpenDump?: (id: string) => void;
  onOpenIapi?: (command: string) => void;
  onLoadQuery?: (text: string) => void;
}) {
  const raw = attr.values.join(", ");
  const link = entityLinkForAttribute(attr.name, raw, product);

  if (link.kind === "object" && onOpenDump) {
    return (
      <span className="entity-links">
        <button type="button" className="entity-link" onClick={() => onOpenDump(link.value)} title="Open dump">
          {raw}
        </button>
        {onOpenIapi &&
          iapiTemplates(link.value)
            .slice(0, 2)
            .map((t) => (
              <button key={t.label} type="button" className="entity-link subtle" onClick={() => onOpenIapi(t.command)}>
                {t.label}
              </button>
            ))}
      </span>
    );
  }

  if (link.kind === "acl" && onLoadQuery && product === "DOCUMENTUM") {
    return (
      <span className="entity-links">
        <code>{raw}</code>
        <button
          type="button"
          className="entity-link subtle"
          onClick={() => onLoadQuery(aclPeekDql(link.value))}
          title="Peek ACL via DQL"
        >
          DQL peek
        </button>
      </span>
    );
  }

  if (link.kind === "type") {
    return <code className="entity-type">{raw}</code>;
  }

  return (
    <input disabled={!editable} value={raw} onChange={(e) => onChange(attr.name, e.target.value)} />
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
  onOpenDump,
  onOpenIapi,
  onLoadQuery,
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
  onOpenDump?: (id: string) => void;
  onOpenIapi?: (command: string) => void;
  onLoadQuery?: (text: string) => void;
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
  const canSave = hasCap(session, "OBJECT_UPDATE");
  const canContent = hasCap(session, "CONTENT_GET");
  const { custom, system } = partitionAttributes(dump, session?.product);
  const onChangeAttr = (name: string, value: string) => {
    if (!canSave) return;
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
            <button type="button" className="entity-link" onClick={() => onOpenDump?.(dump.id)} title="Refresh dump">
              <code>{dump.id}</code>
            </button>
          </div>
        </div>
      </div>
      <div className="dump-sections">
        <AttrSection
          title="Custom"
          hint={xecm ? "Node properties and business fields" : "Type attributes (object_name, title, …)"}
          attrs={custom}
          editable={canSave}
          product={session?.product}
          onChange={onChangeAttr}
          onOpenDump={onOpenDump}
          onOpenIapi={onOpenIapi}
          onLoadQuery={onLoadQuery}
        />
        <AttrSection
          title="System"
          hint={xecm ? "Core CS node metadata" : "Repository internals (r_, i_, a_)"}
          attrs={system}
          system
          editable={canSave}
          product={session?.product}
          onChange={onChangeAttr}
          onOpenDump={onOpenDump}
          onOpenIapi={onOpenIapi}
          onLoadQuery={onLoadQuery}
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
          disabled={!canSave}
          title={
            !canSave
              ? unavailableReason("OBJECT_UPDATE", session?.protocol)
              : undefined
          }
          onClick={async () => {
            if (!session || !canSave) return;
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
        {session && canContent && dumpHasContent(dump) && (
          <>
            <button className="primary" onClick={() => onView(dump.id, dump.objectName)}>
              View content
            </button>
            <a href={api.contentUrl(session.id, dump.id, false)}>
              <button type="button">Download</button>
            </a>
          </>
        )}
        {!canSave && (
          <span className="cap-note">{unavailableReason("OBJECT_UPDATE", session?.protocol)}</span>
        )}
        {onOpenIapi && (
          <>
            {iapiTemplates(dump.id).map((t) => (
              <button key={t.label} type="button" onClick={() => onOpenIapi(t.command)}>
                IAPI {t.label}
              </button>
            ))}
          </>
        )}
      </div>
    </div>
  );
}

function IapiPanel({
  session,
  cmd,
  setCmd,
  history,
  onError,
  trace,
  onOpenDump,
}: {
  session: SessionView;
  cmd: string;
  setCmd: (s: string) => void;
  history: string[];
  onError: (s: string) => void;
  trace: (s: string) => void;
  onOpenDump?: (id: string) => void;
}) {
  const [out, setOut] = useState("");
  const [currentId, setCurrentId] = useState("");
  const { issues: grammarIssues, idle: grammarIdle, checkNow: checkGrammarNow } = useIdleGrammarCheck("iapi", cmd);
  const grammar = grammarSummary(grammarIssues);

  const exec = async () => {
    await checkGrammarNow();
    try {
      const r = await api.iapi(session.id, cmd);
      setOut((r.ok ? "" : "ERROR\n") + (r.output || ""));
      setCurrentId(r.currentId || "");
      trace(`IAPI ${cmd} · ${r.elapsedMs}ms`);
      void recordExecution({
        kind: "IAPI",
        product: session.product,
        summary: r.ok ? "OK" : "ERROR",
        requestText: cmd,
        responseSummary: `${r.ok ? "OK" : "ERROR"} · ${r.elapsedMs}ms`,
        success: r.ok,
        elapsedMs: r.elapsedMs,
      });
    } catch (e) {
      onError(String(e));
      void recordExecution({
        kind: "IAPI",
        product: session.product,
        summary: "IAPI failed",
        requestText: cmd,
        responseSummary: String(e),
        success: false,
      });
    }
  };

  return (
    <div className="panel fill">
      <div className="page-toolbar">
        <button className="primary" type="button" onClick={exec} title="Enter">
          Exec
        </button>
        <span
          className={`grammar-status ${grammarIdle ? grammar.kind : "pending"}`}
          title={grammarIdle ? grammarIssues.map((i) => i.message).join("\n") : "Grammar check runs after you pause typing"}
        >
          {grammarIdle ? grammar.text : "…"}
        </span>
        <select onChange={(e) => e.target.value && setCmd(e.target.value)} defaultValue="">
          <option value="">History…</option>
          {history.map((h) => (
            <option key={h} value={h}>
              {h.slice(0, 80)}
            </option>
          ))}
        </select>
        <span className="muted">IAPI method,session[,args] · Enter to exec</span>
      </div>
      {out && (
        <div className="result-meta muted" style={{ padding: "0 12px" }}>
          Output
        </div>
      )}
      <div className="row">
        <input
          style={{ flex: 1, fontFamily: "var(--mono)" }}
          value={cmd}
          onChange={(e) => setCmd(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              void exec();
            }
          }}
        />
      </div>
      <div className="iapi-output">
        {currentId && onOpenDump && (
          <div className="row">
            <span className="muted">current=</span>
            <button type="button" className="entity-link" onClick={() => onOpenDump(currentId)}>
              {currentId}
            </button>
          </div>
        )}
        <textarea readOnly rows={16} value={out} />
      </div>
    </div>
  );
}

function FeatureChipList({ chips }: { chips: { id: string; label: string; enabled: boolean; reason?: string }[] }) {
  return (
    <ul className="chips">
      {chips.map((c) => (
        <li key={c.id} className={c.enabled ? "on" : "off"} title={c.reason || (c.enabled ? "Available" : "Unavailable")}>
          {c.label}
        </li>
      ))}
    </ul>
  );
}

function UnavailablePanel({
  title,
  reason,
  protocol,
  capabilities,
}: {
  title: string;
  reason: string;
  protocol: Protocol;
  capabilities: string[];
}) {
  const needsDfc = /DFC|IAPI|EXECUTE|ACL|checkout/i.test(reason);
  const needsRest = /REST|OTDS/i.test(reason);
  return (
    <div className="panel unavailable-panel">
      <h3>{title}</h3>
      <p className="warn">{reason}</p>
      <p className="muted">
        Connected with <strong>{protocol}</strong>
        {isDfcProtocol(protocol) ? " (DFC)" : isRestProtocol(protocol) ? " (REST)" : ""}.
      </p>
      {needsDfc && (
        <p>
          Use a <strong>Mock DFC</strong> or <strong>Live DFC</strong> profile to enable DFC-only features (IAPI, DQL
          EXECUTE, checkout, ACLs).
        </p>
      )}
      {needsRest && (
        <p>
          Use a <strong>Documentum REST</strong> or <strong>OTCS REST</strong> profile to enable REST-only features.
        </p>
      )}
      <div className="cap-list">
        <span className="muted">Session capabilities:</span>
        {capabilities.length === 0 && <span className="cap-note">none</span>}
        {capabilities.map((c) => (
          <span key={c} className="cap-pill">
            {c}
          </span>
        ))}
      </div>
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
        <div className="profile-caps">
          <div className="muted">Features for this connection</div>
          <FeatureChipList
            chips={dctm ? documentumFeatureChips(draft.protocol) : xecmFeatureChips(draft.protocol)}
          />
          {isRestProtocol(draft.protocol) && dctm && (
            <p className="cap-note">IAPI and DQL EXECUTE stay off until you switch to Mock/Live DFC.</p>
          )}
          {isDfcProtocol(draft.protocol) && (
            <p className="cap-note ok">DFC enables IAPI, mutating DQL, and checkout.</p>
          )}
          {liveDfc && !draft.dfcLibDir && (
            <p className="warn">Live DFC needs a local DFC lib directory with OpenText JARs.</p>
          )}
        </div>
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
