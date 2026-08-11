package com.dctm.workbench.core;

import java.util.List;

public record AttributeValue(
        String name,
        String dataType,
        boolean repeating,
        List<String> values,
        boolean readOnly
) {
    public String first() {
        return values == null || values.isEmpty() ? "" : values.get(0);
    }
}
