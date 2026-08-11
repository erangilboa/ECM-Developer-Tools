package com.dctm.workbench.core;

import java.util.List;

public interface OtcsBridge {

    ServerInfo connect(OtcsConnectRequest request);

    List<BrowseNode> volumes();

    FolderContents children(long nodeId, BrowseFilter filter);

    ObjectDump node(long nodeId);

    void updateNode(ObjectDump dump);

    SearchResult search(SearchRequest request);

    List<BusinessWorkspace> workspaces();

    BusinessWorkspace workspace(long nodeId);

    byte[] getContent(long nodeId);

    JobList listJobs(JobFilter filter);

    JobDetail getJob(String jobId);

    void runJob(String jobId);

    void reset();

    void disconnect();
}
