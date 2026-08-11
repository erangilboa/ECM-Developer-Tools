package com.dctm.workbench.core;

public record DfcConnectRequest(
        String repository,
        String username,
        char[] password,
        String dfcLibDir,
        String dfcPropertiesPath,
        String reportedVersion
) {
}
