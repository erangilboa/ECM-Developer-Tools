package com.dctm.workbench.core;

public record IapiResult(boolean ok, String output, String currentId, long elapsedMs) {
    public IapiResult(boolean ok, String output, String currentId) {
        this(ok, output, currentId, 0L);
    }

    public static IapiResult ok(String output, String currentId) {
        return new IapiResult(true, output, currentId, 0L);
    }

    public static IapiResult ok(String output, String currentId, long elapsedMs) {
        return new IapiResult(true, output, currentId, elapsedMs);
    }

    public static IapiResult error(String output, String currentId) {
        return new IapiResult(false, output, currentId, 0L);
    }

    public static IapiResult error(String output, String currentId, long elapsedMs) {
        return new IapiResult(false, output, currentId, elapsedMs);
    }
}
