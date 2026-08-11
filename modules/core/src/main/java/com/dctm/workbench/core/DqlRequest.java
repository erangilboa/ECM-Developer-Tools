package com.dctm.workbench.core;

public record DqlRequest(String dql, QueryMode mode, int maxRows) {
    public DqlRequest {
        if (mode == null) {
            mode = QueryMode.READ;
        }
        if (maxRows <= 0) {
            maxRows = 500;
        }
    }

    public static DqlRequest select(String dql) {
        return new DqlRequest(dql, QueryMode.READ, 500);
    }
}
