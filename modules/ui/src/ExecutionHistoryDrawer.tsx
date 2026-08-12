import { useEffect, useState } from "react";
import { api } from "./api";
import type { ExecutionHistoryEntry, Product } from "./types";

type Props = {
  open: boolean;
  onClose: () => void;
  product?: Product;
  onRerun?: (entry: ExecutionHistoryEntry) => void;
};

export function ExecutionHistoryDrawer({ open, onClose, product, onRerun }: Props) {
  const [entries, setEntries] = useState<ExecutionHistoryEntry[]>([]);

  useEffect(() => {
    if (!open) return;
    api.executionHistory(product).then(setEntries).catch(() => setEntries([]));
  }, [open, product]);

  if (!open) return null;

  return (
    <div className="history-drawer-backdrop" onMouseDown={onClose}>
      <div className="history-drawer" onMouseDown={(e) => e.stopPropagation()}>
        <div className="history-drawer-head">
          <strong>Execution history</strong>
          <button type="button" className="tab-x" onClick={onClose}>
            ×
          </button>
        </div>
        <div className="history-list">
          {entries.length === 0 && <div className="muted">No executions recorded yet.</div>}
          {entries.map((e) => (
            <div key={e.id} className={`history-item${e.success ? "" : " failed"}`}>
              <div className="history-item-head">
                <span className="pill scheduled">{e.kind}</span>
                <span className="history-summary">{e.summary}</span>
                {e.elapsedMs != null ? <span className="muted">{e.elapsedMs} ms</span> : null}
              </div>
              <pre className="history-request">{e.requestText}</pre>
              {e.responseSummary && <div className="muted history-response">{e.responseSummary}</div>}
              {onRerun && (
                <button type="button" onClick={() => onRerun(e)}>
                  Rerun
                </button>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export async function recordExecution(
  entry: Omit<ExecutionHistoryEntry, "id" | "lastUsed">
): Promise<void> {
  try {
    await api.appendExecutionHistory(entry);
  } catch {
    // non-fatal
  }
}
