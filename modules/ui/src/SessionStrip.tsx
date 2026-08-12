import { useEffect, useRef, useState } from "react";
import type { SessionView } from "./types";

type Panel = "closed" | "details" | "caps";

type Props = {
  session: SessionView;
};

export function SessionStrip({ session }: Props) {
  const [panel, setPanel] = useState<Panel>("closed");
  const rootRef = useRef<HTMLDivElement>(null);
  const connected = session.connectedAt ? new Date(session.connectedAt).toLocaleString() : "—";
  const caps = [...(session.capabilities || [])].sort();

  useEffect(() => {
    if (panel === "closed") return;
    const onDoc = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setPanel("closed");
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setPanel("closed");
    };
    document.addEventListener("mousedown", onDoc);
    window.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      window.removeEventListener("keydown", onKey);
    };
  }, [panel]);

  const toggle = (next: Panel) => setPanel((p) => (p === next ? "closed" : next));

  return (
    <div className="session-strip" ref={rootRef}>
      <div className="session-strip-inner">
        <button
          type="button"
          className="session-strip-btn"
          onClick={() => toggle("details")}
          title="Session details"
          aria-expanded={panel === "details"}
        >
          <span>{session.userName}</span>
          <span className="sep">·</span>
          <span>{session.repository}</span>
          <span className="sep">·</span>
          <span>{session.version}</span>
          <span className="cap-pill">{session.protocol}</span>
        </button>
        <button
          type="button"
          className="cap-pill cap-pill-btn"
          onClick={(e) => {
            e.stopPropagation();
            toggle("caps");
          }}
          title="View capabilities"
          aria-expanded={panel === "caps"}
        >
          {caps.length} caps
        </button>
      </div>
      {panel === "details" && (
        <div className="session-popover">
          <div className="session-popover-head">
            <strong>{session.profileName}</strong>
            <button type="button" className="tab-x" onClick={() => setPanel("closed")}>
              ×
            </button>
          </div>
          <dl className="session-dl">
            <dt>Product</dt>
            <dd>{session.product === "EXTENDED_ECM" ? "Extended ECM" : "Documentum"}</dd>
            <dt>Protocol</dt>
            <dd>{session.protocol}</dd>
            <dt>Repository</dt>
            <dd>{session.repository}</dd>
            <dt>Version</dt>
            <dd>{session.version}</dd>
            <dt>User</dt>
            <dd>{session.userName}</dd>
            <dt>Auth</dt>
            <dd>{session.authMode || "PASSWORD"}</dd>
            {session.restBaseUrl && (
              <>
                <dt>REST base</dt>
                <dd className="mono">{session.restBaseUrl}</dd>
              </>
            )}
            {session.cgiRoot && (
              <>
                <dt>CGI root</dt>
                <dd className="mono">{session.cgiRoot}</dd>
              </>
            )}
            <dt>Connected</dt>
            <dd>{connected}</dd>
            <dt>Capabilities</dt>
            <dd>
              <button type="button" className="caps-link" onClick={() => setPanel("caps")}>
                {caps.length} enabled — view list
              </button>
            </dd>
          </dl>
        </div>
      )}
      {panel === "caps" && (
        <div className="session-popover session-caps-popover">
          <div className="session-popover-head">
            <strong>Capabilities ({caps.length})</strong>
            <button type="button" className="tab-x" onClick={() => setPanel("closed")}>
              ×
            </button>
          </div>
          <p className="muted caps-popover-hint">
            Operations available on this connection. Modules and actions are gated by these capabilities.
          </p>
          <div className="caps-chip-grid">
            {caps.map((cap) => (
              <span key={cap} className="caps-chip" title={cap}>
                {cap}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
