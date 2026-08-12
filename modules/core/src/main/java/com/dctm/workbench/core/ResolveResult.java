package com.dctm.workbench.core;

public record ResolveResult(
        String kind,
        String id,
        String label,
        String action
) {
    public static final String KIND_OBJECT = "OBJECT";
    public static final String KIND_CHRONICLE = "CHRONICLE";
    public static final String KIND_NODE = "NODE";
    public static final String KIND_UNKNOWN = "UNKNOWN";

    public static final String ACTION_DUMP = "DUMP";
}
