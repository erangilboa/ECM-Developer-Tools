import { useCallback, useEffect, useRef, useState } from "react";
import { ActionBar, ContextMenu, type ObjectAction } from "./ActionMenu";
import { api } from "./api";
import type { BrowseNode, SessionView } from "./types";

const ROOT = "__root__";

export function Browser({
  session,
  onDump,
  onView,
  onError,
  trace,
}: {
  session: SessionView;
  onDump: (id: string) => void;
  onView: (id: string, name?: string) => void;
  onError: (s: string) => void;
  trace: (s: string) => void;
}) {
  const [roots, setRoots] = useState<BrowseNode[]>([]);
  const [path, setPath] = useState<BrowseNode[]>([]);
  const [contents, setContents] = useState<BrowseNode[]>([]);
  const [filter, setFilter] = useState("");
  const [selected, setSelected] = useState<BrowseNode | null>(null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [treeKids, setTreeKids] = useState<Record<string, BrowseNode[]>>({});
  const [loadingId, setLoadingId] = useState<string | null>(null);
  const [menu, setMenu] = useState<{ x: number; y: number; node: BrowseNode } | null>(null);
  const kidsRef = useRef<Record<string, BrowseNode[]>>({});
  const parentsRef = useRef<Record<string, BrowseNode>>({});

  const rootLabel = session.product === "EXTENDED_ECM" ? "Volumes" : "Cabinets";

  const rememberParents = (parent: BrowseNode | null, kids: BrowseNode[]) => {
    for (const k of kids) {
      if (parent) parentsRef.current[k.id] = parent;
      else delete parentsRef.current[k.id];
    }
  };

  const pathOf = (node: BrowseNode) => {
    const chain: BrowseNode[] = [node];
    const seen = new Set([node.id]);
    let cur = node;
    while (parentsRef.current[cur.id] && !seen.has(parentsRef.current[cur.id].id)) {
      cur = parentsRef.current[cur.id];
      seen.add(cur.id);
      chain.unshift(cur);
    }
    return chain;
  };

  const loadChildren = async (node: BrowseNode) => {
    const cached = kidsRef.current[node.id];
    if (cached) return cached;
    setLoadingId(node.id);
    try {
      const folder = await api.children(session.id, node.id);
      kidsRef.current[node.id] = folder.children;
      rememberParents(node, folder.children);
      setTreeKids((m) => ({ ...m, [node.id]: folder.children }));
      return folder.children;
    } finally {
      setLoadingId(null);
    }
  };

  useEffect(() => {
    kidsRef.current = {};
    parentsRef.current = {};
    api
      .roots(session.id)
      .then((r) => {
        kidsRef.current[ROOT] = r;
        setRoots(r);
        setTreeKids({ [ROOT]: r });
        setPath([]);
        setContents(r);
        setSelected(null);
        setExpanded(new Set());
      })
      .catch((e) => onError(String(e)));
  }, [session.id]);

  const showFolder = useCallback(
    async (node: BrowseNode | null) => {
      try {
        if (!node) {
          setPath([]);
          setContents(roots);
          setSelected(null);
          return;
        }
        const kids = await loadChildren(node);
        setPath(pathOf(node));
        setContents(kids);
        setSelected(node);
        setExpanded((s) => new Set(s).add(node.id));
        trace(`Browse ${node.name}`);
      } catch (e) {
        onError(String(e));
      }
    },
    [roots, session.id]
  );

  const toggleExpand = async (node: BrowseNode) => {
    if (expanded.has(node.id)) {
      setExpanded((s) => {
        const n = new Set(s);
        n.delete(node.id);
        return n;
      });
      return;
    }
    try {
      await loadChildren(node);
      setExpanded((s) => new Set(s).add(node.id));
    } catch (e) {
      onError(String(e));
    }
  };

  const openNode = async (node: BrowseNode) => {
    if (node.folder) await showFolder(node);
    else onDump(node.id);
  };

  const download = (node: BrowseNode) => {
    const a = document.createElement("a");
    a.href = api.contentUrl(session.id, node.id, false);
    a.download = node.name || node.id;
    a.click();
  };

  const actionsFor = (node: BrowseNode | null): ObjectAction[] => {
    const disabled = !node;
    const acts: ObjectAction[] = [];
    if (!node || node.folder) {
      acts.push({
        id: "open",
        label: "Open folder",
        primary: true,
        disabled,
        run: () => node && void showFolder(node),
      });
    }
    acts.push(
      {
        id: "dump",
        label: session.product === "EXTENDED_ECM" ? "Details" : "Dump",
        primary: !!node && !node.folder,
        disabled,
        run: () => node && onDump(node.id),
      },
      {
        id: "view",
        label: "View content",
        disabled: disabled || !!node?.folder,
        run: () => node && onView(node.id, node.name),
      },
      {
        id: "download",
        label: "Download",
        disabled: disabled || !!node?.folder,
        run: () => node && download(node),
      },
      {
        id: "copy-id",
        label: "Copy ID",
        disabled,
        run: () => node && void navigator.clipboard.writeText(node.id),
      },
      {
        id: "copy-name",
        label: "Copy name",
        disabled,
        run: () => node && void navigator.clipboard.writeText(node.name),
      }
    );
    return acts;
  };

  const goUp = () => {
    if (path.length === 0) return;
    const parent = path.length === 1 ? null : path[path.length - 2];
    showFolder(parent);
  };

  const visible = contents.filter((n) => !filter || n.name.toLowerCase().includes(filter.toLowerCase()));
  const selectedFolderId = path[path.length - 1]?.id;

  return (
    <div className="panel fill">
      <div className="page-toolbar">
        <button type="button" className="back" onClick={goUp} disabled={path.length === 0}>
          ← Up
        </button>
        <nav className="crumbs" aria-label="Folder path">
          <button type="button" className={path.length === 0 ? "here" : ""} onClick={() => showFolder(null)}>
            {rootLabel}
          </button>
          {path.map((p, i) => (
            <span key={p.id}>
              <span className="crumb-sep">/</span>
              <button type="button" className={i === path.length - 1 ? "here" : ""} onClick={() => showFolder(p)}>
                {p.name}
              </button>
            </span>
          ))}
        </nav>
        <input placeholder="Filter name" value={filter} onChange={(e) => setFilter(e.target.value)} />
      </div>
      <ActionBar
        actions={actionsFor(selected)}
        hint="Select a row, then use an action. Right-click opens the same menu."
      />
      <div className="browse-split">
        <div className="tree" role="tree" aria-label={rootLabel}>
          {(treeKids[ROOT] || roots).filter((n) => n.folder).length === 0 && (
            <div className="muted tree-empty">No folders</div>
          )}
          {(treeKids[ROOT] || roots)
            .filter((n) => n.folder)
            .map((n) => (
              <TreeRow
                key={n.id}
                node={n}
                depth={0}
                expanded={expanded}
                treeKids={treeKids}
                loadingId={loadingId}
                selectedId={selectedFolderId}
                onToggle={toggleExpand}
                onSelect={(node) => showFolder(node)}
              />
            ))}
        </div>
        <div className="grid-wrap">
          <table>
            <thead>
              <tr>
                <th>{session.idLabel ?? "id"}</th>
                <th>Name</th>
                <th>Type</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((n) => (
                <tr
                  key={n.id}
                  className={selected?.id === n.id ? "sel" : ""}
                  onClick={() => setSelected(n)}
                  onDoubleClick={() => openNode(n)}
                  onContextMenu={(e) => {
                    e.preventDefault();
                    setSelected(n);
                    setMenu({ x: e.clientX, y: e.clientY, node: n });
                  }}
                >
                  <td className="cell-id">{n.id}</td>
                  <td>
                    <span className="name-cell">
                      {n.folder ? <FolderGlyph /> : <DocGlyph />}
                      {n.name}
                    </span>
                  </td>
                  <td>{n.typeName}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          actions={actionsFor(menu.node)}
          onClose={() => setMenu(null)}
        />
      )}
    </div>
  );
}

function TreeRow({
  node,
  depth,
  expanded,
  treeKids,
  loadingId,
  selectedId,
  onToggle,
  onSelect,
}: {
  node: BrowseNode;
  depth: number;
  expanded: Set<string>;
  treeKids: Record<string, BrowseNode[]>;
  loadingId: string | null;
  selectedId?: string;
  onToggle: (node: BrowseNode) => void;
  onSelect: (node: BrowseNode) => void;
}) {
  const open = expanded.has(node.id);
  const kids = (treeKids[node.id] || []).filter((n) => n.folder);
  return (
    <div>
      <div
        className={`tree-row${selectedId === node.id ? " sel" : ""}`}
        style={{ paddingLeft: 6 + depth * 14 }}
        role="treeitem"
        aria-expanded={open}
        aria-selected={selectedId === node.id}
      >
        <button
          type="button"
          className="tree-twist"
          aria-label={open ? "Collapse" : "Expand"}
          onClick={(e) => {
            e.stopPropagation();
            onToggle(node);
          }}
        >
          {open ? "▾" : "▸"}
        </button>
        <button type="button" className="tree-label" onClick={() => onSelect(node)} onDoubleClick={() => onSelect(node)}>
          <FolderGlyph />
          {node.name}
        </button>
      </div>
      {open && loadingId === node.id && !treeKids[node.id] && (
        <div className="muted tree-empty" style={{ paddingLeft: 28 + depth * 14 }}>
          Loading…
        </div>
      )}
      {open &&
        kids.map((c) => (
          <TreeRow
            key={c.id}
            node={c}
            depth={depth + 1}
            expanded={expanded}
            treeKids={treeKids}
            loadingId={loadingId}
            selectedId={selectedId}
            onToggle={onToggle}
            onSelect={onSelect}
          />
        ))}
    </div>
  );
}

function FolderGlyph() {
  return (
    <svg className="glyph" viewBox="0 0 16 16" width="14" height="14" aria-hidden>
      <path
        fill="currentColor"
        d="M2 3.5A1.5 1.5 0 0 1 3.5 2h3.2c.4 0 .77.2 1 .53L8.2 3.2H12.5A1.5 1.5 0 0 1 14 4.7v7.8A1.5 1.5 0 0 1 12.5 14h-9A1.5 1.5 0 0 1 2 12.5v-9Z"
        opacity="0.85"
      />
    </svg>
  );
}

function DocGlyph() {
  return (
    <svg className="glyph" viewBox="0 0 16 16" width="14" height="14" aria-hidden>
      <path
        fill="currentColor"
        d="M4 2.5A1.5 1.5 0 0 1 5.5 1h4.2L13 4.3V13.5A1.5 1.5 0 0 1 11.5 15h-6A1.5 1.5 0 0 1 4 13.5v-11Z"
        opacity="0.8"
      />
    </svg>
  );
}
