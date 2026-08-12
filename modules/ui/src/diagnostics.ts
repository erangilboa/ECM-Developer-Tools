import type { SessionView } from "./types";

export type DiagnosticContext = {
  session?: SessionView | null;
  error?: string;
  lastOperation?: string;
  userAgent?: string;
};

export function buildDiagnosticBundle(ctx: DiagnosticContext): string {
  const lines: string[] = [
    "ECM-Dev-Workbench diagnostic bundle",
    `timestamp: ${new Date().toISOString()}`,
    `userAgent: ${ctx.userAgent || (typeof navigator !== "undefined" ? navigator.userAgent : "")}`,
  ];
  if (ctx.session) {
    lines.push(
      `sessionId: ${ctx.session.id}`,
      `profile: ${ctx.session.profileName}`,
      `product: ${ctx.session.product}`,
      `protocol: ${ctx.session.protocol}`,
      `repository: ${ctx.session.repository}`,
      `version: ${ctx.session.version}`,
      `user: ${ctx.session.userName}`,
      `authMode: ${ctx.session.authMode || "PASSWORD"}`,
      `restBaseUrl: ${ctx.session.restBaseUrl || ""}`,
      `cgiRoot: ${ctx.session.cgiRoot || ""}`,
      `capabilities: ${(ctx.session.capabilities || []).join(", ")}`
    );
  }
  if (ctx.lastOperation) {
    lines.push(`lastOperation: ${redact(ctx.lastOperation)}`);
  }
  if (ctx.error) {
    lines.push(`error: ${redact(ctx.error)}`);
  }
  return lines.join("\n");
}

function redact(text: string): string {
  return text.replace(
    /(password|secret|token|ticket|bearer|authorization)\s*[:=]\s*\S+/gi,
    "$1=[REDACTED]"
  );
}
