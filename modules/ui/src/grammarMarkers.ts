import type { GrammarIssue } from "./api";

export function grammarSummary(issues: GrammarIssue[]): { text: string; kind: "ok" | "warn" | "error" } {
  const errors = issues.filter((i) => i.severity === "ERROR").length;
  const warnings = issues.filter((i) => i.severity === "WARNING").length;
  if (errors === 0 && warnings === 0) {
    return { text: "Grammar OK", kind: "ok" };
  }
  const parts: string[] = [];
  if (errors) {
    parts.push(`${errors} error${errors === 1 ? "" : "s"}`);
  }
  if (warnings) {
    parts.push(`${warnings} warning${warnings === 1 ? "" : "s"}`);
  }
  return { text: parts.join(", "), kind: errors ? "error" : "warn" };
}
