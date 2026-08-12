import { loader } from "@monaco-editor/react";
import * as monaco from "monaco-editor/esm/vs/editor/edcore.main";
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import "monaco-editor/min/vs/editor/editor.main.css";
import "monaco-editor/esm/vs/editor/contrib/suggest/browser/media/suggest.css";
import "monaco-editor/esm/vs/base/browser/ui/list/list.css";
import { registerDql } from "./dqlLanguage";
import { registerIapi } from "./iapiLanguage";

self.MonacoEnvironment = {
  getWorker: () => new EditorWorker(),
};

loader.config({ monaco });

function applyWorkbenchTheme() {
  monaco.editor.defineTheme("workbench", {
    base: "vs-dark",
    inherit: true,
    rules: [],
    colors: {
      "editor.background": "#12161f",
      "editor.foreground": "#e7ebf4",
      "editorLineNumber.foreground": "#5a6274",
      "editor.lineHighlightBackground": "#1a2030",
      "editor.selectionBackground": "#4f8dff55",
      "editorCursor.foreground": "#9ec0ff",
      "editorWidget.background": "#171c27",
      "editorWidget.border": "#2c3446",
    },
  });
  monaco.editor.setTheme("workbench");
}

/** Languages + theme must exist before any Editor mounts (avoids blank-page crashes). */
try {
  registerDql(monaco);
  registerIapi(monaco);
  applyWorkbenchTheme();
} catch (e) {
  console.error("Monaco language/theme init failed", e);
}

loader.init().then(() => {
  try {
    registerDql(monaco);
    registerIapi(monaco);
    applyWorkbenchTheme();
  } catch (e) {
    console.error("Monaco loader init failed", e);
  }
});

/**
 * Full-viewport host for overflow widgets.
 * Do NOT use 0×0 — Monaco's suggest CSS uses width/height: 100% and flex: 0 1 auto,
 * which collapses the popup when the host has no box.
 */
export function monacoOverflowHost(): HTMLElement {
  let host = document.getElementById("monaco-overflow-host");
  if (!host) {
    host = document.createElement("div");
    host.id = "monaco-overflow-host";
    host.className = "monaco-editor monaco-overflow-host";
    document.body.appendChild(host);
  }
  return host;
}

export { applyWorkbenchTheme, monaco };
