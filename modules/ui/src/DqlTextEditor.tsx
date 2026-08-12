import { useEffect, useMemo, useRef, useState } from "react";
import { DQL_FUNCTIONS, DQL_KEYWORDS } from "./dqlLanguage";

type Props = {
  value: string;
  onChange: (value: string) => void;
  onRun?: () => void;
};

/** Plain DQL editor — no Monaco (avoids blank-page crashes on some hosts). */
export function DqlTextEditor({ value, onChange, onRun }: Props) {
  const areaRef = useRef<HTMLTextAreaElement>(null);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<string[]>([]);
  const [active, setActive] = useState(0);
  const [caret, setCaret] = useState(0);

  const catalog = useMemo(() => [...DQL_KEYWORDS, ...DQL_FUNCTIONS], []);

  const suggestAt = (text: string, pos: number, manual: boolean) => {
    const before = text.slice(0, pos);
    const m = /[A-Za-z_][A-Za-z0-9_]*$/.exec(before);
    const prefix = m ? m[0].toUpperCase() : "";

    let next: string[];
    if (prefix) {
      next = catalog.filter((k) => k.startsWith(prefix) && (manual || k !== prefix)).slice(0, manual ? 24 : 12);
      if (manual && next.length === 0) {
        next = catalog.filter((k) => k.startsWith(prefix)).slice(0, 24);
      }
    } else if (manual) {
      next = catalog.slice(0, 24);
    } else {
      setOpen(false);
      return;
    }

    setItems(next);
    setActive(0);
    setOpen(next.length > 0);
  };

  const insert = (word: string) => {
    const el = areaRef.current;
    if (!el) return;
    const pos = el.selectionStart;
    const before = value.slice(0, pos);
    const after = value.slice(pos);
    const m = /[A-Za-z_][A-Za-z0-9_]*$/.exec(before);
    const start = m ? pos - m[0].length : pos;
    const next = value.slice(0, start) + word + after;
    onChange(next);
    setOpen(false);
    requestAnimationFrame(() => {
      el.focus();
      const np = start + word.length;
      el.setSelectionRange(np, np);
      setCaret(np);
    });
  };

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (!areaRef.current?.parentElement?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);

  return (
    <div className="dql-text-wrap">
      <textarea
        ref={areaRef}
        className="dql-textarea"
        spellCheck={false}
        value={value}
        rows={8}
        onChange={(e) => {
          const v = e.target.value;
          const pos = e.target.selectionStart;
          onChange(v);
          setCaret(pos);
          suggestAt(v, pos, false);
        }}
        onClick={(e) => {
          const pos = e.currentTarget.selectionStart;
          setCaret(pos);
          suggestAt(value, pos, false);
        }}
        onKeyDown={(e) => {
          if ((e.ctrlKey || e.metaKey) && e.code === "Space") {
            e.preventDefault();
            const el = areaRef.current;
            if (!el) return;
            const pos = el.selectionStart;
            setCaret(pos);
            suggestAt(value, pos, true);
            return;
          }
          if (open && items.length) {
            if (e.key === "ArrowDown") {
              e.preventDefault();
              setActive((i) => (i + 1) % items.length);
              return;
            }
            if (e.key === "ArrowUp") {
              e.preventDefault();
              setActive((i) => (i - 1 + items.length) % items.length);
              return;
            }
            if (e.key === "Enter" || e.key === "Tab") {
              e.preventDefault();
              insert(items[active] || items[0]);
              return;
            }
            if (e.key === "Escape") {
              setOpen(false);
              return;
            }
          }
          if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
            e.preventDefault();
            onRun?.();
          }
        }}
      />
      {open && items.length > 0 && (
        <ul className="dql-suggest" style={{ top: suggestTop(areaRef.current, caret) }}>
          {items.map((item, i) => (
            <li key={item}>
              <button
                type="button"
                className={i === active ? "active" : ""}
                onMouseDown={(e) => {
                  e.preventDefault();
                  insert(item);
                }}
              >
                {item}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function suggestTop(el: HTMLTextAreaElement | null, _caret: number): number {
  if (!el) return 40;
  return Math.min(el.clientHeight - 8, 36 + (el.scrollTop > 0 ? 8 : 28));
}
