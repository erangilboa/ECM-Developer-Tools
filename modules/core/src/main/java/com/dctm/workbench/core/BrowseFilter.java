package com.dctm.workbench.core;

public record BrowseFilter(String nameContains, Integer subtype) {
    public static BrowseFilter none() {
        return new BrowseFilter(null, null);
    }

    public boolean matches(BrowseNode node) {
        if (nameContains != null && !nameContains.isBlank()) {
            if (node.name() == null || !node.name().toLowerCase().contains(nameContains.toLowerCase())) {
                return false;
            }
        }
        if (subtype != null && node.subtype() != subtype) {
            return false;
        }
        return true;
    }
}
