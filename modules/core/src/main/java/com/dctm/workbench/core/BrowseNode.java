package com.dctm.workbench.core;

public record BrowseNode(
        String id,
        String name,
        String typeName,
        int subtype,
        boolean folder,
        String iconHint
) {
}
