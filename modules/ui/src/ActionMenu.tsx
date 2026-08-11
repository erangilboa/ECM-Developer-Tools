import { useEffect, useRef } from "react";

export type ObjectAction = {
  id: string;
  label: string;
  primary?: boolean;
  disabled?: boolean;
  run: () => void;
};

export function ActionBar({ actions, hint }: { actions: ObjectAction[]; hint?: string }) {
  return (
    <div className="row action-bar">
      {actions.map((a) => (
        <button
          key={a.id}
          type="button"
          className={a.primary ? "primary" : undefined}
          disabled={a.disabled}
          onClick={a.run}
        >
          {a.label}
        </button>
      ))}
      {hint && <span className="muted">{hint}</span>}
    </div>
  );
}

export function ContextMenu({
  x,
  y,
  actions,
  onClose,
}: {
  x: number;
  y: number;
  actions: ObjectAction[];
  onClose: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const onDown = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) onClose();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("mousedown", onDown);
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("mousedown", onDown);
      window.removeEventListener("keydown", onKey);
    };
  }, [onClose]);

  const left = Math.min(x, Math.max(8, window.innerWidth - 220));
  const top = Math.min(y, Math.max(8, window.innerHeight - 16 - actions.length * 34));
  return (
    <div ref={ref} className="ctx-menu" style={{ left, top }} role="menu">
      {actions.map((a) => (
        <button
          key={a.id}
          type="button"
          role="menuitem"
          disabled={a.disabled}
          onClick={() => {
            onClose();
            if (!a.disabled) a.run();
          }}
        >
          {a.label}
        </button>
      ))}
    </div>
  );
}

export function objectIdColumn(columns: string[]) {
  const lower = columns.map((c) => c.toLowerCase());
  const exact = lower.findIndex((c) => c === "r_object_id" || c === "id");
  if (exact >= 0) return exact;
  const objectId = lower.findIndex((c) => c.includes("object_id"));
  if (objectId >= 0) return objectId;
  return lower.findIndex((c) => c.endsWith("_id"));
}
