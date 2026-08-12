package com.dctm.workbench.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class OtcsRepositorySession implements OtcsSession {

    private final OtcsBridge bridge;
    private final ServerInfo info;

    public OtcsRepositorySession(OtcsBridge bridge, ServerInfo info) {
        this.bridge = bridge;
        this.info = info;
    }

    public OtcsBridge bridge() {
        return bridge;
    }

    @Override
    public Product product() {
        return Product.EXTENDED_ECM;
    }

    @Override
    public ServerInfo serverInfo() {
        return info;
    }

    @Override
    public Set<Capability> capabilities() {
        return info.capabilities();
    }

    @Override
    public List<BrowseNode> listRoots() {
        require(Capability.BROWSE, "Browse requires BROWSE");
        return bridge.volumes();
    }

    @Override
    public FolderContents listChildren(String id, BrowseFilter filter) {
        require(Capability.BROWSE, "Browse requires BROWSE");
        return bridge.children(parseId(id), filter == null ? BrowseFilter.none() : filter);
    }

    @Override
    public ObjectDump dump(String id) {
        require(Capability.OBJECT_READ, "Dump requires OBJECT_READ");
        try {
            return bridge.node(parseId(id));
        } catch (SessionException e) {
            JobDetail job = bridge.getJob(id);
            return agentDump(job);
        }
    }

    @Override
    public void saveDump(ObjectDump dump) {
        require(Capability.OBJECT_UPDATE, "Save requires OBJECT_UPDATE");
        if (dump.sapLinked()) {
            throw new SessionException("Refusing to mutate SAP-linked Business Workspace without ECMLink. "
                    + "ext_system_id is set on this node.");
        }
        bridge.updateNode(dump);
    }

    @Override
    public SearchResult search(SearchRequest request) {
        require(Capability.CS_SEARCH, "Search requires CS_SEARCH (OTCS). DQL is Documentum-only.");
        return bridge.search(request);
    }

    @Override
    public ContentPayload getContent(String id) {
        require(Capability.CONTENT_GET, "Content requires CONTENT_GET");
        ObjectDump dump = bridge.node(parseId(id));
        byte[] bytes = bridge.getContent(parseId(id));
        String mime = ContentTypes.guess(dump.objectName(), dump.attr("a_content_type"));
        return new ContentPayload(dump.objectName(), mime, bytes == null ? new byte[0] : bytes);
    }

    @Override
    public void close() {
        bridge.disconnect();
    }

    @Override
    public List<BusinessWorkspace> listBusinessWorkspaces() {
        require(Capability.BUSINESS_WORKSPACE, "Business Workspaces require BUSINESS_WORKSPACE");
        return bridge.workspaces();
    }

    @Override
    public BusinessWorkspace getWorkspace(String id) {
        require(Capability.BUSINESS_WORKSPACE, "Business Workspaces require BUSINESS_WORKSPACE");
        return bridge.workspace(parseId(id));
    }

    @Override
    public JobList listJobs(JobFilter filter) {
        require(Capability.JOB_LIST, "Job list requires JOB_LIST");
        return bridge.listJobs(filter == null ? JobFilter.none() : filter);
    }

    @Override
    public JobDetail getJob(String jobId) {
        require(Capability.JOB_LIST, "Job detail requires JOB_LIST");
        return bridge.getJob(jobId);
    }

    @Override
    public void runJob(String jobId) {
        require(Capability.JOB_RUN, "Run job requires JOB_RUN");
        bridge.runJob(jobId);
    }

    @Override
    public void resetMock() {
        bridge.reset();
    }

    private static ObjectDump agentDump(JobDetail job) {
        JobInfo info = job.info();
        List<AttributeValue> attrs = List.of(
                new AttributeValue("id", "string", false, List.of(info.id()), true),
                new AttributeValue("name", "string", false, List.of(nullToEmpty(info.objectName())), false),
                new AttributeValue("thread", "string", false, List.of(nullToEmpty(info.methodName())), false),
                new AttributeValue("enabled", "string", false, List.of(info.inactive() ? "false" : "true"), false),
                new AttributeValue("status", "string", false, List.of(nullToEmpty(info.status())), true),
                new AttributeValue("interval", "string", false, List.of(nullToEmpty(info.runInterval())), false),
                new AttributeValue("last_run", "string", false, List.of(nullToEmpty(info.lastCompletionDate())), true),
                new AttributeValue("next_run", "string", false, List.of(nullToEmpty(info.nextInvocationDate())), true),
                new AttributeValue("last_return", "string", false, List.of(nullToEmpty(info.lastReturn())), true),
                new AttributeValue("current_status", "string", false, List.of(nullToEmpty(info.currentStatus())), true)
        );
        return new ObjectDump(info.id(), "Scheduled Agent", info.objectName(), attrs, List.of(),
                Map.of("subtype", "agent"), false);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new SessionException("OTCS node id must be numeric, got: " + id);
        }
    }
}
