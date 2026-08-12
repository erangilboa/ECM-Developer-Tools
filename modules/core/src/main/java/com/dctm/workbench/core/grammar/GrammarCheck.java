package com.dctm.workbench.core.grammar;

import java.util.List;
import java.util.Locale;

public final class GrammarCheck {

    private GrammarCheck() {
    }

    public static List<GrammarIssue> check(String language, String text) {
        String lang = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        return switch (lang) {
            case "dql" -> dql(text);
            case "iapi", "api" -> iapi(text);
            default -> List.of(GrammarIssue.error(0, 1, text == null ? "" : text,
                    "Unknown language '" + language + "' (use dql or iapi)"));
        };
    }

    public static List<GrammarIssue> dql(String text) {
        return DqlParser.parse(text);
    }

    public static List<GrammarIssue> iapi(String text) {
        return IapiParser.parse(text);
    }
}
