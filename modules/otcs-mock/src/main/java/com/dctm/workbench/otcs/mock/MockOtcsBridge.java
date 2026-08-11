package com.dctm.workbench.otcs.mock;

import com.dctm.workbench.core.BrowseFilter;
import com.dctm.workbench.core.BrowseNode;
import com.dctm.workbench.core.BusinessWorkspace;
import com.dctm.workbench.core.CapabilitySets;
import com.dctm.workbench.core.FolderContents;
import com.dctm.workbench.core.JobDetail;
import com.dctm.workbench.core.JobFilter;
import com.dctm.workbench.core.JobList;
import com.dctm.workbench.core.ObjectDump;
import com.dctm.workbench.core.OtcsBridge;
import com.dctm.workbench.core.OtcsConnectRequest;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.SearchRequest;
import com.dctm.workbench.core.SearchResult;
import com.dctm.workbench.core.ServerInfo;

import java.util.List;

public class MockOtcsBridge implements OtcsBridge {

    private final FakeOtcs otcs = FakeOtcs.fromClasspath();
    private ServerInfo info;

    @Override
    public ServerInfo connect(OtcsConnectRequest request) {
        String version = request.reportedVersion() == null || request.reportedVersion().isBlank()
                ? otcs.version() : request.reportedVersion();
        String user = request.username() == null ? "Admin" : request.username();
        info = ServerInfo.otcs(Protocol.MOCK_OTCS, "mock-otcs", version, user, CapabilitySets.mockOtcs());
        return info;
    }

    @Override
    public List<BrowseNode> volumes() {
        return otcs.volumes();
    }

    @Override
    public FolderContents children(long nodeId, BrowseFilter filter) {
        return otcs.children(nodeId, filter);
    }

    @Override
    public ObjectDump node(long nodeId) {
        return otcs.dump(nodeId);
    }

    @Override
    public void updateNode(ObjectDump dump) {
        otcs.update(dump);
    }

    @Override
    public SearchResult search(SearchRequest request) {
        return otcs.search(request);
    }

    @Override
    public List<BusinessWorkspace> workspaces() {
        return otcs.workspaces();
    }

    @Override
    public BusinessWorkspace workspace(long nodeId) {
        return otcs.workspace(nodeId);
    }

    @Override
    public byte[] getContent(long nodeId) {
        return otcs.content(nodeId);
    }

    @Override
    public JobList listJobs(JobFilter filter) {
        return otcs.listJobs(filter);
    }

    @Override
    public JobDetail getJob(String jobId) {
        return otcs.getJob(jobId);
    }

    @Override
    public void runJob(String jobId) {
        otcs.runJob(jobId);
    }

    @Override
    public void reset() {
        otcs.reset();
    }

    @Override
    public void disconnect() {
        // in-memory
    }
}
