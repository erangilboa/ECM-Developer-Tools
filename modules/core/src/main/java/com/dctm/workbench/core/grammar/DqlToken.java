package com.dctm.workbench.core.grammar;

public record DqlToken(DqlTokenType type, String text, int offset, int length, int line, int column) {

    public String upper() {
        return text.toUpperCase(java.util.Locale.ROOT);
    }

    public boolean isEof() {
        return type == DqlTokenType.EOF;
    }

    public boolean is(DqlTokenType t) {
        return type == t;
    }

    public boolean keyword(String word) {
        return type == DqlTokenType.IDENT && upper().equals(word);
    }
}
