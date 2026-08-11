package com.dctm.workbench.core;

import java.util.List;
import java.util.Map;

public record CategoryValue(
        String categoryId,
        String categoryName,
        Map<String, List<String>> attributes
) {
}
