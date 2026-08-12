package com.dctm.workbench.core;

import java.util.Map;

public record RestProxyRequest(
        String method,
        String path,
        Map<String, String> headers,
        String body
) {
}
