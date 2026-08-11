package com.dctm.workbench.core;

public record SearchRequest(String query, int limit) {
    public SearchRequest {
        if (limit <= 0) {
            limit = 100;
        }
    }
}
