import { buildDiagnosticBundle } from "./diagnostics";
import type { SessionView } from "./types";

type Props = {
  error: string;
  session: SessionView | null;
  lastOperation?: string;
  onDismiss: () => void;
};

export function ErrorPanel({ error, session, lastOperation, onDismiss }: Props) {
  const copyBundle = async () => {
    const bundle = buildDiagnosticBundle({ session, error, lastOperation });
    await navigator.clipboard.writeText(bundle);
  };

  return (
    <div className="error-panel">
      <div className="error-panel-head">
        <strong>Error</strong>
        <div className="row">
          <button type="button" onClick={() => void copyBundle()}>
            Copy diagnostic bundle
          </button>
          <button type="button" className="tab-x" onClick={onDismiss} title="Dismiss">
            ×
          </button>
        </div>
      </div>
      <pre className="error-panel-body">{error}</pre>
      <div className="muted error-panel-hint">
        Bundle includes session metadata only — passwords and tokens are redacted.
      </div>
    </div>
  );
}
