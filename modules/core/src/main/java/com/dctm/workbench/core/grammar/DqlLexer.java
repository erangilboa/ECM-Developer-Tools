package com.dctm.workbench.core.grammar;

import java.util.ArrayList;
import java.util.List;

final class DqlLexer {

    private final String source;
    private final List<DqlToken> tokens = new ArrayList<>();
    private int i;
    private int line = 1;
    private int column = 1;

    private DqlLexer(String source) {
        this.source = source == null ? "" : source;
    }

    static List<DqlToken> tokenize(String source) {
        DqlLexer lexer = new DqlLexer(source);
        lexer.scan();
        return lexer.tokens;
    }

    private void scan() {
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
                continue;
            }
            if (c == '\n') {
                advance();
                line++;
                column = 1;
                continue;
            }
            if (c == '-' && peek(1) == '-') {
                skipLineComment();
                continue;
            }
            if (c == '/' && peek(1) == '*') {
                skipBlockComment();
                continue;
            }
            int start = i;
            int startLine = line;
            int startCol = column;
            if (c == '\'') {
                string(start, startLine, startCol);
                continue;
            }
            if (isIdentStart(c)) {
                while (i < source.length() && isIdentPart(source.charAt(i))) {
                    advance();
                }
                emit(DqlTokenType.IDENT, start, startLine, startCol);
                continue;
            }
            if (isDigit(c) || (c == '.' && isDigit(peek(1)))) {
                number(start, startLine, startCol);
                continue;
            }
            if (c == '<' && peek(1) == '>') {
                advance();
                advance();
                emit(DqlTokenType.NE, start, startLine, startCol);
                continue;
            }
            if (c == '!' && peek(1) == '=') {
                advance();
                advance();
                emit(DqlTokenType.NE, start, startLine, startCol);
                continue;
            }
            if (c == '<' && peek(1) == '=') {
                advance();
                advance();
                emit(DqlTokenType.LE, start, startLine, startCol);
                continue;
            }
            if (c == '>' && peek(1) == '=') {
                advance();
                advance();
                emit(DqlTokenType.GE, start, startLine, startCol);
                continue;
            }
            switch (c) {
                case '*' -> {
                    advance();
                    emit(DqlTokenType.STAR, start, startLine, startCol);
                }
                case ',' -> {
                    advance();
                    emit(DqlTokenType.COMMA, start, startLine, startCol);
                }
                case '.' -> {
                    advance();
                    emit(DqlTokenType.DOT, start, startLine, startCol);
                }
                case '(' -> {
                    advance();
                    emit(DqlTokenType.LPAREN, start, startLine, startCol);
                }
                case ')' -> {
                    advance();
                    emit(DqlTokenType.RPAREN, start, startLine, startCol);
                }
                case '=' -> {
                    advance();
                    emit(DqlTokenType.EQ, start, startLine, startCol);
                }
                case '<' -> {
                    advance();
                    emit(DqlTokenType.LT, start, startLine, startCol);
                }
                case '>' -> {
                    advance();
                    emit(DqlTokenType.GT, start, startLine, startCol);
                }
                default -> {
                    advance();
                    emit(DqlTokenType.UNKNOWN, start, startLine, startCol);
                }
            }
        }
        tokens.add(new DqlToken(DqlTokenType.EOF, "", i, 0, line, column));
    }

    private void string(int start, int startLine, int startCol) {
        advance();
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\'') {
                if (peek(1) == '\'') {
                    advance();
                    advance();
                    continue;
                }
                advance();
                emit(DqlTokenType.STRING, start, startLine, startCol);
                return;
            }
            if (c == '\n') {
                advance();
                line++;
                column = 1;
                continue;
            }
            advance();
        }
        emit(DqlTokenType.UNKNOWN, start, startLine, startCol);
    }

    private void number(int start, int startLine, int startCol) {
        while (i < source.length() && isDigit(source.charAt(i))) {
            advance();
        }
        if (i < source.length() && source.charAt(i) == '.' && isDigit(peek(1))) {
            advance();
            while (i < source.length() && isDigit(source.charAt(i))) {
                advance();
            }
        }
        emit(DqlTokenType.NUMBER, start, startLine, startCol);
    }

    private void skipLineComment() {
        while (i < source.length() && source.charAt(i) != '\n') {
            advance();
        }
    }

    private void skipBlockComment() {
        advance();
        advance();
        while (i < source.length()) {
            if (source.charAt(i) == '*' && peek(1) == '/') {
                advance();
                advance();
                return;
            }
            if (source.charAt(i) == '\n') {
                advance();
                line++;
                column = 1;
            } else {
                advance();
            }
        }
    }

    private void emit(DqlTokenType type, int start, int startLine, int startCol) {
        tokens.add(new DqlToken(type, source.substring(start, i), start, i - start, startLine, startCol));
    }

    private void advance() {
        i++;
        column++;
    }

    private char peek(int ahead) {
        int j = i + ahead;
        return j < source.length() ? source.charAt(j) : '\0';
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
