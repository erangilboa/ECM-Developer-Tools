package com.dctm.workbench.core;

import java.util.Map;

public record RestProxyResponse(
        int status,
        Map<String, String> headers,
        String body,
        long elapsedMs,
        String url
) {
}
