package com.dctm.workbench.core;

public record BusinessWorkspace(
        String id,
        String name,
        String templateId,
        String extSystemId,
        String boType,
        String boId,
        String parentId
) {
}
