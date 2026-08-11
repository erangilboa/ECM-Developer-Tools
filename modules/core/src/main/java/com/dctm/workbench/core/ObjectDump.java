package com.dctm.workbench.core;

import java.util.List;
import java.util.Map;

public record ObjectDump(
        String id,
        String typeName,
        String objectName,
        List<AttributeValue> attributes,
        List<CategoryValue> categories,
        Map<String, String> extra,
        boolean sapLinked
) {
    public String attr(String name) {
        if (attributes == null) {
            return "";
        }
        return attributes.stream()
                .filter(a -> a.name().equalsIgnoreCase(name))
                .map(AttributeValue::first)
                .findFirst()
                .orElse("");
    }
}
