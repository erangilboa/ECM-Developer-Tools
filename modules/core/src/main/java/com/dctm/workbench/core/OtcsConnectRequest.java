package com.dctm.workbench.core;

public record OtcsConnectRequest(
        String cgiRoot,
        String username,
        char[] password,
        String otdsUrl,
        AuthMode authMode,
        String bearerToken,
        String reportedVersion
) {
}
