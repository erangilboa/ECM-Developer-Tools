import { useCallback, useEffect, useRef, useState } from "react";
import { api, type GrammarIssue } from "./api";

/** Check grammar only after the user stops typing — avoids distracting inline feedback. */
export function useIdleGrammarCheck(language: "dql" | "iapi", text: string, idleMs = 1200) {
  const [issues, setIssues] = useState<GrammarIssue[]>([]);
  const [idle, setIdle] = useState(true);
  const timerRef = useRef(0);

  const checkNow = useCallback(async () => {
    try {
      setIssues(await api.checkGrammar(language, text));
    } catch {
      setIssues([]);
    }
    setIdle(true);
  }, [language, text]);

  useEffect(() => {
    setIdle(false);
    window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      void checkNow();
    }, idleMs);
    return () => window.clearTimeout(timerRef.current);
  }, [text, idleMs, checkNow]);

  return { issues, idle, checkNow };
}
