package com.dctm.workbench.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class DiagnosticRedactor {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "otcsticket", "otdsticket", "cookie", "set-cookie", "x-csrf-token"
    );
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(password|secret|token|ticket|bearer)\\s*[:=]\\s*\\S+");

    private DiagnosticRedactor() {
    }

    public static Map<String, String> redactHeaders(Map<String, String> headers) {
        if (headers == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((k, v) -> {
            if (k != null && SENSITIVE_HEADERS.contains(k.toLowerCase())) {
                out.put(k, "[REDACTED]");
            } else {
                out.put(k, v);
            }
        });
        return out;
    }

    public static String redactText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return SECRET_PATTERN.matcher(text).replaceAll("$1=[REDACTED]");
    }
}
