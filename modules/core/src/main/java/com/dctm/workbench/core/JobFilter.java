package com.dctm.workbench.core;

public record JobFilter(String nameContains, Boolean inactive) {
    public static JobFilter none() {
        return new JobFilter(null, null);
    }
}
