package com.dctm.workbench.core;

import java.util.List;

public record DqlResult(
        List<String> columns,
        List<List<String>> rows,
        int rowCount,
        String query,
        long elapsedMs
) {
}
