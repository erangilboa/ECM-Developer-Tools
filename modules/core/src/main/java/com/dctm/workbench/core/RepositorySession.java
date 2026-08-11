package com.dctm.workbench.core;

import java.util.List;
import java.util.Set;

public interface RepositorySession {

    Product product();

    ServerInfo serverInfo();

    Set<Capability> capabilities();

    List<BrowseNode> listRoots();

    FolderContents listChildren(String id, BrowseFilter filter);

    ObjectDump dump(String id);

    void saveDump(ObjectDump dump);

    SearchResult search(SearchRequest request);

    ContentPayload getContent(String id);

    void close();

    default boolean supports(Capability capability) {
        return capabilities().contains(capability);
    }

    default void require(Capability capability, String message) {
        if (!supports(capability)) {
            throw new UnsupportedCapabilityException(capability, message);
        }
    }
}
