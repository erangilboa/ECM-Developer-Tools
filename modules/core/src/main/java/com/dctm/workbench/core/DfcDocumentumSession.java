package com.dctm.workbench.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DfcDocumentumSession implements DocumentumSession {

    private final DfcBridge bridge;
    private ServerInfo info;

    public DfcDocumentumSession(DfcBridge bridge, ServerInfo info) {
        this.bridge = bridge;
        this.info = info;
    }

    @Override
    public Product product() {
        return Product.DOCUMENTUM;
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
        DqlResult result = bridge.query(
                "SELECT r_object_id, object_name, r_object_type FROM dm_cabinet",
                QueryMode.READ);
        return toNodes(result, true);
    }

    @Override
    public FolderContents listChildren(String id, BrowseFilter filter) {
        ObjectDump parent = bridge.getObject(id);
        DqlResult result = bridge.query(
                "SELECT r_object_id, object_name, r_object_type FROM dm_sysobject WHERE folder('" + id + "')",
                QueryMode.READ);
        List<BrowseNode> nodes = toNodes(result, false).stream()
                .filter(n -> filter == null || filter.matches(n))
                .toList();
        return new FolderContents(id, parent.objectName(), nodes);
    }

    @Override
    public ObjectDump dump(String id) {
        require(Capability.OBJECT_READ, "Object dump requires OBJECT_READ");
        return bridge.getObject(id);
    }

    @Override
    public void saveDump(ObjectDump dump) {
        require(Capability.OBJECT_UPDATE, "Save requires OBJECT_UPDATE (DFC mock or live DFC)");
        bridge.saveObject(dump);
    }

    @Override
    public SearchResult search(SearchRequest request) {
        require(Capability.DQL_SELECT, "Search on Documentum uses DQL");
        DqlResult dql = executeDql(DqlRequest.select(request.query()));
        return new SearchResult(dql.columns(), dql.rows(), dql.rowCount());
    }

    @Override
    public ContentPayload getContent(String id) {
        require(Capability.CONTENT_GET, "Content download requires CONTENT_GET");
        byte[] bytes = bridge.getContent(id);
        ObjectDump dump = bridge.getObject(id);
        String mime = ContentTypes.guess(dump.objectName(), dump.attr("a_content_type"));
        return new ContentPayload(dump.objectName(), mime, bytes == null ? new byte[0] : bytes);
    }

    @Override
    public void close() {
        bridge.disconnect();
    }

    @Override
    public DqlResult executeDql(DqlRequest request) {
        QueryMode mode = request.mode();
        String dql = request.dql() == null ? "" : request.dql().trim();
        boolean mutating = looksMutating(dql);
        if (mutating) {
            require(Capability.DQL_EXECUTE, "Non-SELECT DQL requires DFC (mock or live). Documentum REST is SELECT-only.");
            mode = QueryMode.EXEC;
        } else {
            require(Capability.DQL_SELECT, "DQL SELECT requires DQL_SELECT");
        }
        return bridge.query(dql, mode);
    }

    @Override
    public IapiResult iapi(String command) {
        require(Capability.IAPI, "IAPI requires DFC (mock or live)");
        return bridge.iapi(command);
    }

    @Override
    public JobList listJobs(JobFilter filter) {
        require(Capability.JOB_LIST, "Job list requires JOB_LIST");
        DqlResult result = bridge.query(
                "SELECT r_object_id, object_name, method_name, is_inactive, run_now, a_last_completion, a_next_invocation, run_interval, a_last_return, a_current_status FROM dm_job",
                QueryMode.READ);
        List<JobInfo> jobs = new ArrayList<>();
        for (List<String> row : result.rows()) {
            JobInfo job = JobSupport.fromRow(result, row);
            if (filter != null && filter.nameContains() != null && !filter.nameContains().isBlank()) {
                if (job.objectName() == null || !job.objectName().toLowerCase(Locale.ROOT)
                        .contains(filter.nameContains().toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }
            if (filter != null && filter.inactive() != null && job.inactive() != filter.inactive()) {
                continue;
            }
            jobs.add(job);
        }
        return new JobList(jobs);
    }

    @Override
    public void runJob(String jobId) {
        require(Capability.JOB_RUN, "Run job requires JOB_RUN");
        bridge.iapi("apply,c," + jobId + ",RUN_NOW");
    }

    @Override
    public TypeDictionary types() {
        require(Capability.TYPE_DICTIONARY, "Type dictionary requires TYPE_DICTIONARY");
        return bridge.types();
    }

    @Override
    public void checkout(String id) {
        require(Capability.CHECKOUT, "Checkout requires CHECKOUT");
        bridge.checkout(id);
    }

    @Override
    public void checkin(String id) {
        require(Capability.CHECKOUT, "Checkin requires CHECKOUT");
        bridge.checkin(id);
    }

    @Override
    public void resetMock() {
        bridge.reset();
    }

    private static boolean looksMutating(String dql) {
        String head = dql.stripLeading().toUpperCase(Locale.ROOT);
        return head.startsWith("UPDATE") || head.startsWith("DELETE") || head.startsWith("CREATE")
                || head.startsWith("INSERT") || head.startsWith("ALTER") || head.startsWith("GRANT")
                || head.startsWith("EXECUTE") || head.startsWith("BEGIN");
    }

    private static List<BrowseNode> toNodes(DqlResult result, boolean forceFolder) {
        List<BrowseNode> nodes = new ArrayList<>();
        for (List<String> row : result.rows()) {
            String id = col(row, 0, result, "r_object_id");
            String name = col(row, 0, result, "object_name");
            String type = col(row, 0, result, "r_object_type");
            boolean folder = forceFolder || "dm_folder".equals(type) || "dm_cabinet".equals(type);
            int subtype = folder ? 0 : 9;
            nodes.add(new BrowseNode(id, name, type, subtype, folder, type));
        }
        return nodes;
    }

    private static String col(List<String> row, int ignored, DqlResult result, String name) {
        int i = index(result, name);
        if (i < 0 || i >= row.size()) {
            return "";
        }
        return row.get(i);
    }

    private static int index(DqlResult result, String name) {
        for (int i = 0; i < result.columns().size(); i++) {
            if (result.columns().get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}
