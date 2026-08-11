package com.dctm.workbench.core;

public record IapiResult(boolean ok, String output, String currentId) {
    public static IapiResult ok(String output, String currentId) {
        return new IapiResult(true, output, currentId);
    }

    public static IapiResult error(String output, String currentId) {
        return new IapiResult(false, output, currentId);
    }
}
