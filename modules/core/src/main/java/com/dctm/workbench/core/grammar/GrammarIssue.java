package com.dctm.workbench.core.grammar;

public record GrammarIssue(
        int offset,
        int length,
        int line,
        int column,
        String message,
        Severity severity
) {
    public enum Severity {
        ERROR,
        WARNING
    }

    static GrammarIssue error(int offset, int length, String source, String message) {
        int[] lc = lineCol(source, offset);
        return new GrammarIssue(offset, Math.max(1, length), lc[0], lc[1], message, Severity.ERROR);
    }

    static GrammarIssue warning(int offset, int length, String source, String message) {
        int[] lc = lineCol(source, offset);
        return new GrammarIssue(offset, Math.max(1, length), lc[0], lc[1], message, Severity.WARNING);
    }

    static int[] lineCol(String source, int offset) {
        int line = 1;
        int col = 1;
        int n = Math.min(Math.max(offset, 0), source.length());
        for (int i = 0; i < n; i++) {
            if (source.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new int[] {line, col};
    }
}
