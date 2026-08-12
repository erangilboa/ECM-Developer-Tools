package com.dctm.workbench.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticRedactorTest {

    @Test
    void redactsSensitiveHeadersAndText() {
        Map<String, String> headers = DiagnosticRedactor.redactHeaders(Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer secret-token",
                "OTCSTicket", "abc123"
        ));
        assertThat(headers.get("Authorization")).isEqualTo("[REDACTED]");
        assertThat(headers.get("OTCSTicket")).isEqualTo("[REDACTED]");
        assertThat(headers.get("Content-Type")).isEqualTo("application/json");

        String text = DiagnosticRedactor.redactText("password=abc123 and token: xyz");
        assertThat(text).contains("password=[REDACTED]").contains("token=[REDACTED]");
    }
}
