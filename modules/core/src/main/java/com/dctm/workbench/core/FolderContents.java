package com.dctm.workbench.core;

import java.util.List;

public record FolderContents(String parentId, String parentName, List<BrowseNode> children) {
}
