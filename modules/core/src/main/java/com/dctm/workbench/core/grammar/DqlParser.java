package com.dctm.workbench.core.grammar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Structural DQL checker for editor diagnostics. Incomplete prefixes while typing
 * are warnings or silent; closed syntax mistakes are errors.
 */
final class DqlParser {

    private static final Set<String> CLAUSE = Set.of(
            "WHERE", "GROUP", "HAVING", "ORDER", "UNION", "ENABLE", "WITH", "OBJECTS", "SET");

    private static final Set<String> CMP_OPS_WORDS = Set.of("LIKE", "IN", "IS", "BETWEEN");

    private final String source;
    private final List<DqlToken> tokens;
    private final List<GrammarIssue> issues = new ArrayList<>();
    private int p;

    private DqlParser(String source, List<DqlToken> tokens) {
        this.source = source;
        this.tokens = tokens;
    }

    static List<GrammarIssue> parse(String source) {
        List<DqlToken> tokens = DqlLexer.tokenize(source);
        DqlParser parser = new DqlParser(source == null ? "" : source, tokens);
        parser.scanUnknown();
        if (parser.skipTriviaEmpty()) {
            return parser.issues;
        }
        parser.script();
        return parser.issues;
    }

    private void scanUnknown() {
        for (DqlToken t : tokens) {
            if (t.type() == DqlTokenType.UNKNOWN) {
                if (t.text().startsWith("'")) {
                    error(t, "Unterminated string literal");
                } else {
                    error(t, "Unexpected character '" + t.text() + "'");
                }
            }
        }
    }

    private boolean skipTriviaEmpty() {
        return peek().isEof() && issues.isEmpty();
    }

    private void script() {
        statement();
        while (!peek().isEof()) {
            if (peek().keyword("SELECT") || peek().keyword("UPDATE") || peek().keyword("DELETE")
                    || peek().keyword("CREATE") || peek().keyword("INSERT") || peek().keyword("EXECUTE")) {
                statement();
            } else if (peek().type() == DqlTokenType.UNKNOWN) {
                advance();
            } else {
                error(peek(), "Unexpected token '" + peek().text() + "' after statement");
                advance();
                recover();
            }
        }
    }

    private void statement() {
        DqlToken start = peek();
        if (start.isEof()) {
            return;
        }
        if (start.keyword("SELECT")) {
            select();
            return;
        }
        if (start.keyword("UPDATE")) {
            update();
            return;
        }
        if (start.keyword("DELETE")) {
            delete();
            return;
        }
        if (start.keyword("CREATE") || start.keyword("INSERT") || start.keyword("EXECUTE")
                || start.keyword("ALTER") || start.keyword("DROP") || start.keyword("GRANT")
                || start.keyword("REVOKE") || start.keyword("BEGIN") || start.keyword("REGISTER")) {
            advance();
            skipToNextStatement();
            return;
        }
        if (start.type() == DqlTokenType.IDENT && isPrefixOf(start.text(),
                "SELECT", "UPDATE", "DELETE", "CREATE", "INSERT", "EXECUTE", "ALTER", "DROP")) {
            advance();
            return;
        }
        error(start, "DQL statement must start with SELECT, UPDATE, DELETE, or CREATE");
        recover();
    }

    private void select() {
        expectKeyword("SELECT");
        if (peek().keyword("DISTINCT") || peek().keyword("ALL")) {
            advance();
        }
        if (peek().isEof()) {
            return;
        }
        selectList();
        if (peek().isEof()) {
            return;
        }
        if (!expectKeywordOrPrefix("FROM")) {
            return;
        }
        if (peek().isEof()) {
            return;
        }
        if (!typeName()) {
            return;
        }
        optionalAlias();
        if (peek().keyword("WHERE")) {
            advance();
            if (!peek().isEof()) {
                orExpr();
            }
        }
        if (peek().keyword("GROUP")) {
            advance();
            if (!expectKeywordOrPrefix("BY")) {
                return;
            }
            identList();
        }
        if (peek().keyword("HAVING")) {
            advance();
            if (!peek().isEof()) {
                orExpr();
            }
        }
        if (peek().keyword("UNION")) {
            advance();
            if (peek().keyword("ALL")) {
                advance();
            }
            if (peek().keyword("SELECT")) {
                select();
                return;
            }
            if (isPrefixOf(peek().text(), "SELECT")) {
                advance();
                return;
            }
            if (!peek().isEof()) {
                error(peek(), "Expected SELECT after UNION");
            }
            return;
        }
        if (peek().keyword("ORDER")) {
            advance();
            if (!expectKeywordOrPrefix("BY")) {
                return;
            }
            orderList();
        }
        if (peek().keyword("ENABLE")) {
            advance();
            enableClause();
        }
        if (peek().keyword("WITH")) {
            advance();
            skipBalanced();
        }
    }

    private void selectList() {
        if (peek().is(DqlTokenType.STAR)) {
            advance();
            return;
        }
        if (peek().keyword("FROM") || clauseOrJoin(peek())) {
            error(peek(), "Expected select list before " + peek().upper());
            return;
        }
        selectItem();
        while (peek().is(DqlTokenType.COMMA)) {
            advance();
            if (peek().isEof()) {
                return;
            }
            selectItem();
        }
    }

    private void selectItem() {
        expr();
        if (peek().keyword("AS")) {
            advance();
            if (peek().is(DqlTokenType.IDENT)) {
                advance();
            }
        } else if (peek().is(DqlTokenType.IDENT) && !clauseOrJoin(peek()) && !peek().keyword("FROM")) {
            advance();
        }
    }

    private void update() {
        expectKeyword("UPDATE");
        if (peek().isEof()) {
            return;
        }
        if (!typeName()) {
            return;
        }
        optionalAlias();
        if (peek().keyword("OBJECTS")) {
            advance();
        } else if (isPrefixOf(peek().text(), "OBJECTS")) {
            advance();
            return;
        }
        if (!expectKeywordOrPrefix("SET")) {
            return;
        }
        assignment();
        while (peek().is(DqlTokenType.COMMA)) {
            advance();
            if (peek().isEof()) {
                return;
            }
            assignment();
        }
        if (peek().keyword("WHERE")) {
            advance();
            if (!peek().isEof()) {
                orExpr();
            }
        }
    }

    private void delete() {
        expectKeyword("DELETE");
        if (peek().isEof()) {
            return;
        }
        if (!typeName()) {
            return;
        }
        optionalAlias();
        if (peek().keyword("OBJECTS")) {
            advance();
        } else if (isPrefixOf(peek().text(), "OBJECTS")) {
            advance();
            return;
        }
        if (peek().keyword("WHERE")) {
            advance();
            if (!peek().isEof()) {
                orExpr();
            }
        }
    }

    private void assignment() {
        if (!peek().is(DqlTokenType.IDENT)) {
            if (!peek().isEof()) {
                error(peek(), "Expected attribute name in SET");
            }
            return;
        }
        advance();
        if (peek().isEof()) {
            return;
        }
        if (!peek().is(DqlTokenType.EQ)) {
            error(peek(), "Expected '=' in SET assignment");
            return;
        }
        advance();
        if (peek().isEof()) {
            return;
        }
        value();
    }

    private boolean typeName() {
        if (peek().is(DqlTokenType.IDENT)) {
            advance();
            return true;
        }
        if (!peek().isEof()) {
            error(peek(), "Expected type name");
        }
        return false;
    }

    private void optionalAlias() {
        if (peek().is(DqlTokenType.IDENT) && !clauseOrJoin(peek()) && !peek().keyword("SET")) {
            advance();
        }
    }

    private void orExpr() {
        andExpr();
        while (peek().keyword("OR")) {
            advance();
            if (peek().isEof()) {
                return;
            }
            andExpr();
        }
    }

    private void andExpr() {
        notExpr();
        while (peek().keyword("AND")) {
            advance();
            if (peek().isEof()) {
                return;
            }
            notExpr();
        }
    }

    private void notExpr() {
        if (peek().keyword("NOT")) {
            advance();
            if (peek().isEof()) {
                return;
            }
        }
        predicate();
    }

    private void predicate() {
        if (peek().is(DqlTokenType.LPAREN)) {
            advance();
            if (peek().isEof()) {
                return;
            }
            orExpr();
            expectClose();
            return;
        }
        if (peek().keyword("FOLDER") || peek().keyword("CABINET")) {
            functionCall();
            return;
        }
        if (peek().keyword("TYPE") || peek().keyword("ANY")) {
            advance();
            if (peek().is(DqlTokenType.LPAREN)) {
                functionCallRest();
            }
            return;
        }
        expr();
        if (peek().isEof()) {
            return;
        }
        if (isCmpOp(peek()) || CMP_OPS_WORDS.contains(peek().upper())) {
            comparisonTail();
            return;
        }
        if (peek().keyword("AND") || peek().keyword("OR") || peek().is(DqlTokenType.RPAREN)
                || clauseOrJoin(peek()) || peek().isEof()) {
            return;
        }
        error(peek(), "Expected comparison operator, LIKE, IN, or IS");
    }

    private void comparisonTail() {
        DqlToken op = peek();
        if (op.keyword("NOT") && look(1).keyword("LIKE")) {
            advance();
            advance();
            value();
            return;
        }
        if (op.keyword("LIKE")) {
            advance();
            if (!peek().isEof()) {
                value();
            }
            return;
        }
        if (op.keyword("IN")) {
            advance();
            if (peek().is(DqlTokenType.LPAREN) || peek().keyword("SELECT")) {
                if (peek().keyword("SELECT")) {
                    select();
                } else {
                    parenListOrSubquery();
                }
            } else if (!peek().isEof()) {
                error(peek(), "Expected '(' after IN");
            }
            return;
        }
        if (op.keyword("IS")) {
            advance();
            if (peek().keyword("NOT")) {
                advance();
            }
            if (peek().keyword("NULL")) {
                advance();
            } else if (isPrefixOf(peek().text(), "NULL")) {
                advance();
            } else if (!peek().isEof()) {
                error(peek(), "Expected NULL after IS");
            }
            return;
        }
        if (op.keyword("BETWEEN")) {
            advance();
            value();
            if (peek().keyword("AND")) {
                advance();
                if (!peek().isEof()) {
                    value();
                }
            }
            return;
        }
        if (isCmpOp(op)) {
            advance();
            if (!peek().isEof()) {
                value();
            }
        }
    }

    private void parenListOrSubquery() {
        if (!peek().is(DqlTokenType.LPAREN)) {
            return;
        }
        advance();
        if (peek().keyword("SELECT")) {
            select();
            expectClose();
            return;
        }
        if (!peek().isEof() && !peek().is(DqlTokenType.RPAREN)) {
            value();
            while (peek().is(DqlTokenType.COMMA)) {
                advance();
                if (peek().isEof()) {
                    return;
                }
                value();
            }
        }
        expectClose();
    }

    private void expr() {
        if (peek().is(DqlTokenType.STAR)) {
            advance();
            return;
        }
        if (peek().is(DqlTokenType.IDENT) && look(1).is(DqlTokenType.LPAREN)) {
            functionCall();
            return;
        }
        if (peek().is(DqlTokenType.IDENT)) {
            advance();
            while (peek().is(DqlTokenType.DOT)) {
                advance();
                if (peek().is(DqlTokenType.IDENT) || peek().is(DqlTokenType.STAR)) {
                    advance();
                } else if (!peek().isEof()) {
                    error(peek(), "Expected identifier after '.'");
                    return;
                }
            }
            return;
        }
        if (peek().is(DqlTokenType.NUMBER) || peek().is(DqlTokenType.STRING)) {
            advance();
            return;
        }
        if (peek().is(DqlTokenType.LPAREN)) {
            advance();
            if (peek().isEof()) {
                return;
            }
            if (peek().keyword("SELECT")) {
                select();
            } else {
                expr();
            }
            expectClose();
            return;
        }
        if (!peek().isEof() && peek().type() != DqlTokenType.UNKNOWN) {
            error(peek(), "Expected expression");
            advance();
        }
    }

    private void value() {
        if (peek().keyword("NULL") || peek().keyword("TRUE") || peek().keyword("FALSE")
                || peek().keyword("DATE") || peek().keyword("DATETIME") || peek().keyword("USER")
                || peek().keyword("TODAY") || peek().keyword("NOW") || peek().keyword("YESTERDAY")) {
            if (look(1).is(DqlTokenType.LPAREN)) {
                functionCall();
            } else {
                advance();
            }
            return;
        }
        expr();
    }

    private void functionCall() {
        if (!peek().is(DqlTokenType.IDENT)) {
            return;
        }
        advance();
        functionCallRest();
    }

    private void functionCallRest() {
        if (!peek().is(DqlTokenType.LPAREN)) {
            if (!peek().isEof()) {
                error(peek(), "Expected '('");
            }
            return;
        }
        advance();
        if (!peek().is(DqlTokenType.RPAREN) && !peek().isEof()) {
            if (peek().is(DqlTokenType.STAR)) {
                advance();
            } else {
                value();
                while (peek().is(DqlTokenType.COMMA)) {
                    advance();
                    if (peek().isEof()) {
                        return;
                    }
                    if (peek().keyword("DESCEND") || peek().keyword("ASCEND")) {
                        advance();
                    } else {
                        value();
                    }
                }
                if (peek().keyword("DESCEND") || peek().keyword("ASCEND")) {
                    advance();
                }
            }
        }
        expectClose();
    }

    private void identList() {
        if (peek().is(DqlTokenType.IDENT)) {
            advance();
        } else if (!peek().isEof()) {
            error(peek(), "Expected identifier");
            return;
        }
        while (peek().is(DqlTokenType.COMMA)) {
            advance();
            if (peek().isEof()) {
                return;
            }
            if (peek().is(DqlTokenType.IDENT)) {
                advance();
            } else {
                error(peek(), "Expected identifier");
                return;
            }
        }
    }

    private void orderList() {
        orderItem();
        while (peek().is(DqlTokenType.COMMA)) {
            advance();
            if (peek().isEof()) {
                return;
            }
            orderItem();
        }
    }

    private void orderItem() {
        expr();
        if (peek().keyword("ASC") || peek().keyword("DESC")) {
            advance();
        }
    }

    private void enableClause() {
        if (!peek().is(DqlTokenType.LPAREN)) {
            if (!peek().isEof()) {
                error(peek(), "Expected '(' after ENABLE");
            }
            return;
        }
        skipBalanced();
    }

    private void skipBalanced() {
        if (!peek().is(DqlTokenType.LPAREN)) {
            while (!peek().isEof() && !clauseOrJoin(peek())) {
                if (peek().is(DqlTokenType.LPAREN)) {
                    skipBalanced();
                } else {
                    advance();
                }
            }
            return;
        }
        int depth = 0;
        do {
            if (peek().is(DqlTokenType.LPAREN)) {
                depth++;
            } else if (peek().is(DqlTokenType.RPAREN)) {
                depth--;
            }
            if (peek().isEof()) {
                return;
            }
            advance();
        } while (depth > 0 && !peek().isEof());
    }

    private void skipToNextStatement() {
        while (!peek().isEof()
                && !peek().keyword("SELECT")
                && !peek().keyword("UPDATE")
                && !peek().keyword("DELETE")
                && !peek().keyword("CREATE")) {
            if (peek().is(DqlTokenType.LPAREN)) {
                skipBalanced();
            } else {
                advance();
            }
        }
    }

    private void recover() {
        while (!peek().isEof()
                && !peek().keyword("SELECT")
                && !peek().keyword("UPDATE")
                && !peek().keyword("DELETE")
                && !peek().keyword("CREATE")) {
            advance();
        }
    }

    private void expectClose() {
        if (peek().is(DqlTokenType.RPAREN)) {
            advance();
            return;
        }
        if (!peek().isEof()) {
            error(peek(), "Expected ')'");
        }
    }

    private boolean expectKeywordOrPrefix(String word) {
        if (peek().keyword(word)) {
            advance();
            return true;
        }
        if (peek().isEof()) {
            return false;
        }
        if (isPrefixOf(peek().text(), word)) {
            advance();
            return false;
        }
        error(peek(), "Expected " + word);
        return false;
    }

    private void expectKeyword(String word) {
        if (peek().keyword(word)) {
            advance();
        }
    }

    private boolean clauseOrJoin(DqlToken t) {
        return t.type() == DqlTokenType.IDENT && CLAUSE.contains(t.upper());
    }

    private boolean isCmpOp(DqlToken t) {
        return t.type() == DqlTokenType.EQ
                || t.type() == DqlTokenType.NE
                || t.type() == DqlTokenType.LT
                || t.type() == DqlTokenType.GT
                || t.type() == DqlTokenType.LE
                || t.type() == DqlTokenType.GE;
    }

    private DqlToken peek() {
        return tokens.get(p);
    }

    private DqlToken look(int ahead) {
        int i = Math.min(p + ahead, tokens.size() - 1);
        return tokens.get(i);
    }

    private void advance() {
        if (p < tokens.size() - 1) {
            p++;
        }
    }

    private void error(DqlToken t, String message) {
        issues.add(GrammarIssue.error(t.offset(), t.length() == 0 ? 1 : t.length(), source, message));
    }

    private static boolean isPrefixOf(String text, String... words) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String u = text.toUpperCase(Locale.ROOT);
        for (String w : words) {
            if (w.startsWith(u) && u.length() < w.length()) {
                return true;
            }
        }
        return false;
    }
}
