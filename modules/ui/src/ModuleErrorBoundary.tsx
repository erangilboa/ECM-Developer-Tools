import { Component, type ErrorInfo, type ReactNode } from "react";

type Props = { name?: string; children: ReactNode; fallback?: ReactNode };
type State = { error: Error | null };

/** Keeps the shell visible when a module throws. Retry is explicit only. */
export class ModuleErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(`Module crashed: ${this.props.name || "unknown"}`, error, info.componentStack);
  }

  render() {
    if (!this.state.error) {
      return this.props.children;
    }
    if (this.props.fallback) {
      return this.props.fallback;
    }
    return (
      <div className="panel unavailable-panel">
        <h3>{this.props.name || "Module"} failed to open</h3>
        <p className="warn">{this.state.error.message || String(this.state.error)}</p>
        <p className="muted">The rest of the workbench is still running. Try Browse, then open this module again.</p>
        <button type="button" className="primary" onClick={() => this.setState({ error: null })}>
          Retry
        </button>
      </div>
    );
  }
}
