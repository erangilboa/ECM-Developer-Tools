import { useEffect, useState } from "react";
import { api } from "./api";

export function DocumentViewer({
  sessionId,
  objectId,
  fileName,
  onClose,
  onError,
}: {
  sessionId: string;
  objectId: string;
  fileName?: string;
  onClose: () => void;
  onError: (s: string) => void;
}) {
  const url = api.contentUrl(sessionId, objectId, true);
  const downloadUrl = api.contentUrl(sessionId, objectId, false);
  const [kind, setKind] = useState<"loading" | "text" | "image" | "pdf" | "other" | "error">("loading");
  const [text, setText] = useState("");
  const [title, setTitle] = useState(fileName || objectId);

  useEffect(() => {
    let cancelled = false;
    setKind("loading");
    fetch(url)
      .then(async (res) => {
        if (!res.ok) {
          throw new Error(res.statusText || `HTTP ${res.status}`);
        }
        const mime = (res.headers.get("content-type") || "").toLowerCase();
        const cd = res.headers.get("content-disposition") || "";
        const match = /filename="([^"]+)"/.exec(cd);
        if (match && !cancelled) {
          setTitle(match[1]);
        }
        if (mime.startsWith("text/") || mime.includes("json") || mime.includes("xml")) {
          const body = await res.text();
          if (!cancelled) {
            setText(body);
            setKind("text");
          }
        } else if (mime.startsWith("image/")) {
          if (!cancelled) setKind("image");
        } else if (mime.includes("pdf")) {
          if (!cancelled) setKind("pdf");
        } else if (!cancelled) {
          setKind("other");
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setKind("error");
          onError(String(e));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [url]);

  return (
    <div className="modal-bg" onClick={onClose}>
      <div className="modal viewer" onClick={(e) => e.stopPropagation()}>
        <div className="row">
          <strong>{title}</strong>
          <span className="muted">{objectId}</span>
          <span style={{ flex: 1 }} />
          <a href={downloadUrl}>
            <button type="button">Download</button>
          </a>
          <button type="button" onClick={onClose}>
            Close
          </button>
        </div>
        <div className="viewer-body">
          {kind === "loading" && <div className="muted">Loading…</div>}
          {kind === "text" && <pre>{text}</pre>}
          {kind === "image" && <img src={url} alt={title} />}
          {kind === "pdf" && <iframe title={title} src={url} />}
          {kind === "other" && (
            <div className="muted">
              This format cannot be previewed in the browser.{" "}
              <a href={downloadUrl}>Download instead</a>
            </div>
          )}
          {kind === "error" && <div className="error">Could not load content.</div>}
        </div>
      </div>
    </div>
  );
}
