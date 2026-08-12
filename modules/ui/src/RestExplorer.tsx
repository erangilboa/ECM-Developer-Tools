import { useEffect, useState } from "react";
import { api } from "./api";
import { isMockProtocol, isRestProtocol } from "./capabilities";
import { recordExecution } from "./ExecutionHistoryDrawer";
import type { RestProxyResponse, SessionView } from "./types";

function restBaseFor(session: SessionView): string {
  if (session.restBaseUrl) return session.restBaseUrl;
  if (session.cgiRoot) return session.cgiRoot;
  if (session.product === "DOCUMENTUM") {
    return `mock://dctm-rest/repositories/${session.repository}`;
  }
  return "mock://otcs-rest/api/v2";
}

type Props = {
  session: SessionView;
  onError: (msg: string) => void;
  trace: (s: string) => void;
};

export function RestExplorer({ session, onError, trace }: Props) {
  const base = restBaseFor(session);
  const [method, setMethod] = useState("GET");
  const [path, setPath] = useState("");
  const [body, setBody] = useState("");
  const [response, setResponse] = useState<RestProxyResponse | null>(null);
  const [formatJson, setFormatJson] = useState(true);

  useEffect(() => {
    const sample =
      session.product === "DOCUMENTUM"
        ? `${base}/objects/0900000180000001`
        : `${base}/nodes/5100`;
    setPath(sample);
  }, [session.id, base, session.product]);

  const send = async () => {
    onError("");
    try {
      const res = await api.restProxy(session.id, { method, path, headers: {}, body });
      setResponse(res);
      trace(`REST ${method} ${res.status} ${res.elapsedMs}ms`);
      void recordExecution({
        kind: "REST",
        product: session.product,
        summary: `${method} ${res.status}`,
        requestText: `${method} ${path}`,
        responseSummary: `${res.status} · ${res.elapsedMs}ms · ${(res.body || "").length} bytes`,
        success: res.status >= 200 && res.status < 400,
        elapsedMs: res.elapsedMs,
      });
    } catch (e) {
      onError(String(e));
      void recordExecution({
        kind: "REST",
        product: session.product,
        summary: `${method} failed`,
        requestText: `${method} ${path}`,
        responseSummary: String(e),
        success: false,
      });
    }
  };

  const displayBody = () => {
    if (!response?.body) return "";
    if (!formatJson) return response.body;
    try {
      return JSON.stringify(JSON.parse(response.body), null, 2);
    } catch {
      return response.body;
    }
  };

  return (
    <div className="panel fill rest-explorer">
      <div className="page-toolbar">
        <select value={method} onChange={(e) => setMethod(e.target.value)}>
          {["GET", "POST", "PUT", "PATCH", "DELETE"].map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
        <input
          style={{ flex: 1, fontFamily: "var(--mono)" }}
          value={path}
          onChange={(e) => setPath(e.target.value)}
          placeholder="URL or path relative to connection base"
        />
        <button className="primary" type="button" onClick={() => void send()}>
          Send
        </button>
      </div>
      <div className="rest-split">
        <div className="rest-pane">
          <div className="pane-label">Request body</div>
          <textarea
            rows={10}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder='JSON body for POST/PUT/PATCH — e.g. {"dql":"SELECT r_object_id FROM dm_document"}'
          />
        </div>
        <div className="rest-pane">
          <div className="pane-label row">
            <span>Response</span>
            {response && (
              <>
                <span className={`pill ${response.status >= 400 ? "failed" : "success"}`}>{response.status}</span>
                <span className="muted">{response.elapsedMs} ms</span>
                <button type="button" className={formatJson ? "primary" : ""} onClick={() => setFormatJson((f) => !f)}>
                  {formatJson ? "Raw" : "Format JSON"}
                </button>
              </>
            )}
          </div>
          <textarea readOnly rows={16} value={displayBody()} />
          {response && (
            <details className="rest-headers">
              <summary>Response headers</summary>
              <pre>{JSON.stringify(response.headers, null, 2)}</pre>
            </details>
          )}
        </div>
      </div>
      <div className="muted rest-hint">
        {isRestProtocol(session.protocol)
          ? "Auth headers are applied from the active connection. Sensitive values are redacted in history."
          : isMockProtocol(session.protocol)
            ? "Mock mode: requests are simulated via the session proxy. Try GET .../objects/0900000180000001 (Documentum) or .../nodes/5100 (OTCS)."
            : "Requests are routed through the session REST proxy."}
      </div>
    </div>
  );
}
