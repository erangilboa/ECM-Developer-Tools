package com.dctm.workbench.core;

import java.util.List;

public record SearchResult(List<String> columns, List<List<String>> rows, int rowCount) {
}
