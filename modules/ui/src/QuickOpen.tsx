import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "./api";
import type { SessionView } from "./types";

export type QuickOpenItem = {
  id: string;
  label: string;
  detail?: string;
  group: string;
  run: () => void | Promise<void>;
};

type Props = {
  open: boolean;
  onClose: () => void;
  session: SessionView | null;
  recentDumpIds: string[];
  recentQueries: string[];
  modules: { id: string; label: string; disabled?: boolean }[];
  onOpenDump: (id: string) => void | Promise<void>;
  onGoModule: (id: string) => void;
  onLoadQuery?: (text: string) => void;
  onOpenIapi?: (command: string) => void;
};

export function QuickOpen({
  open,
  onClose,
  session,
  recentDumpIds,
  recentQueries,
  modules,
  onOpenDump,
  onGoModule,
  onLoadQuery,
  onOpenIapi,
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const [resolving, setResolving] = useState(false);

  useEffect(() => {
    if (open) {
      setQuery("");
      setActive(0);
      requestAnimationFrame(() => inputRef.current?.focus());
    }
  }, [open]);

  const staticItems = useMemo((): QuickOpenItem[] => {
    if (!session) return [];
    const items: QuickOpenItem[] = [];

    for (const m of modules) {
      if (m.disabled) continue;
      items.push({
        id: `module-${m.id}`,
        label: `Open ${m.label}`,
        group: "Modules",
        run: () => onGoModule(m.id),
      });
    }

    for (const id of recentDumpIds.slice(0, 8)) {
      items.push({
        id: `recent-${id}`,
        label: session.product === "EXTENDED_ECM" ? `Node ${id}` : `Object ${id}`,
        detail: "Recent",
        group: "Recent",
        run: () => onOpenDump(id),
      });
    }

    for (const q of recentQueries.slice(0, 8)) {
      items.push({
        id: `query-${q.slice(0, 40)}`,
        label: q.length > 72 ? `${q.slice(0, 72)}…` : q,
        detail: "Query history",
        group: "Queries",
        run: () => {
          onLoadQuery?.(q);
          onGoModule(session.product === "EXTENDED_ECM" ? "search" : "dql");
        },
      });
    }

    return items;
  }, [session, modules, recentDumpIds, recentQueries, onGoModule, onOpenDump, onLoadQuery]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return staticItems;
    return staticItems.filter(
      (item) => item.label.toLowerCase().includes(q) || item.detail?.toLowerCase().includes(q)
    );
  }, [query, staticItems]);

  const runResolve = async () => {
    if (!session) return;
    const text = query.trim();
    if (!text) return;
    setResolving(true);
    try {
      const r = await api.resolve(session.id, text);
      if (r.action === "DUMP" && r.id) {
        await onOpenDump(r.id);
        onClose();
        return;
      }
      if (r.kind === "UNKNOWN") {
        const local = filtered[active];
        if (local) {
          await local.run();
          onClose();
        }
        return;
      }
    } finally {
      setResolving(false);
    }
  };

  const runActive = async () => {
    const text = query.trim();
    if (text && session) {
      const looksLikeId =
        /^[0-9a-fA-F]{16}$/.test(text) ||
        /^\d{4,}$/.test(text) ||
        text.includes("/objects/") ||
        text.includes("/nodes/");
      if (looksLikeId) {
        await runResolve();
        return;
      }
    }
    const item = filtered[active];
    if (item) {
      await item.run();
      onClose();
    }
  };

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="quick-open-backdrop" onMouseDown={onClose}>
      <div className="quick-open" onMouseDown={(e) => e.stopPropagation()}>
        <input
          ref={inputRef}
          className="quick-open-input"
          placeholder={
            session
              ? `Open ${session.idLabel}, module, or paste URL…`
              : "Connect to a repository first"
          }
          value={query}
          disabled={!session}
          onChange={(e) => {
            setQuery(e.target.value);
            setActive(0);
          }}
          onKeyDown={(e) => {
            if (e.key === "ArrowDown") {
              e.preventDefault();
              setActive((i) => Math.min(i + 1, Math.max(0, filtered.length - 1)));
            } else if (e.key === "ArrowUp") {
              e.preventDefault();
              setActive((i) => Math.max(0, i - 1));
            } else if (e.key === "Enter") {
              e.preventDefault();
              void runActive();
            }
          }}
        />
        <div className="quick-open-list">
          {filtered.length === 0 && (
            <div className="quick-open-empty muted">
              {resolving ? "Resolving…" : query.trim() ? "No matches — Enter to resolve pasted id/URL" : "Type to filter"}
            </div>
          )}
          {filtered.map((item, i) => (
            <button
              key={item.id}
              type="button"
              className={`quick-open-item${i === active ? " active" : ""}`}
              onMouseEnter={() => setActive(i)}
              onClick={() => {
                void item.run().then(onClose);
              }}
            >
              <span className="quick-open-label">{item.label}</span>
              <span className="quick-open-detail">{item.detail || item.group}</span>
            </button>
          ))}
        </div>
        <div className="quick-open-hint muted">
          ↑↓ navigate · Enter open · Esc close · Ctrl+K / Ctrl+P anytime
        </div>
      </div>
    </div>
  );
}

export function useQuickOpenShortcut(onOpen: () => void, enabled = true) {
  useEffect(() => {
    if (!enabled) return;
    const onKey = (e: KeyboardEvent) => {
      if (!(e.ctrlKey || e.metaKey) || e.altKey || e.shiftKey) return;
      const isQuickOpen = e.code === "KeyP" || e.code === "KeyK";
      if (!isQuickOpen) return;
      // Capture phase + always preventDefault so the browser print dialog never wins.
      e.preventDefault();
      e.stopPropagation();
      onOpen();
    };
    document.addEventListener("keydown", onKey, { capture: true });
    return () => document.removeEventListener("keydown", onKey, { capture: true });
  }, [onOpen, enabled]);
}
