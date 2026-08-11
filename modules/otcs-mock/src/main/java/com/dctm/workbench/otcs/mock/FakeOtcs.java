package com.dctm.workbench.otcs.mock;

import com.dctm.workbench.core.AttributeValue;
import com.dctm.workbench.core.BrowseFilter;
import com.dctm.workbench.core.ClasspathFixtures;
import com.dctm.workbench.core.BrowseNode;
import com.dctm.workbench.core.BusinessWorkspace;
import com.dctm.workbench.core.CategoryValue;
import com.dctm.workbench.core.FolderContents;
import com.dctm.workbench.core.JobDetail;
import com.dctm.workbench.core.JobFilter;
import com.dctm.workbench.core.JobInfo;
import com.dctm.workbench.core.JobList;
import com.dctm.workbench.core.JobReport;
import com.dctm.workbench.core.ObjectDump;
import com.dctm.workbench.core.SearchRequest;
import com.dctm.workbench.core.SearchResult;
import com.dctm.workbench.core.SessionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class FakeOtcs {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Long, FakeNode> nodes = new LinkedHashMap<>();
    private final Map<Long, FakeAgent> agents = new LinkedHashMap<>();
    private final AtomicLong reportSeq = new AtomicLong(9200);
    private String version = "24.2";

    public static FakeOtcs fromClasspath() {
        FakeOtcs otcs = new FakeOtcs();
        otcs.loadFromClasspath();
        return otcs;
    }

    public void loadFromClasspath() {
        try {
            byte[] json = ClasspathFixtures.read(FakeOtcs.class, "/fixtures/otcs.json",
                    "otcs-mock/src/main/resources/fixtures/otcs.json",
                    "../otcs-mock/src/main/resources/fixtures/otcs.json",
                    "src/main/resources/fixtures/otcs.json");
            load(mapper.readTree(json));
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("Failed to load mock OTCS: " + e.getMessage(), e);
        }
    }

    public synchronized void reset() {
        nodes.clear();
        agents.clear();
        reportSeq.set(9200);
        loadFromClasspath();
    }

    public String version() {
        return version;
    }

    public List<BrowseNode> volumes() {
        return nodes.values().stream()
                .filter(FakeNode::isVolume)
                .map(this::toBrowse)
                .toList();
    }

    public FolderContents children(long nodeId, BrowseFilter filter) {
        FakeNode parent = require(nodeId);
        List<BrowseNode> children = nodes.values().stream()
                .filter(n -> n.getParentId() == nodeId)
                .map(this::toBrowse)
                .filter(filter::matches)
                .toList();
        return new FolderContents(String.valueOf(nodeId), parent.getName(), children);
    }

    public ObjectDump dump(long nodeId) {
        FakeAgent agent = agents.get(nodeId);
        if (agent != null) {
            return agent.toDump();
        }
        FakeNode node = require(nodeId);
        List<AttributeValue> attrs = new ArrayList<>();
        attrs.add(attr("id", String.valueOf(node.getId()), true));
        attrs.add(attr("name", node.getName(), false));
        attrs.add(attr("type", String.valueOf(node.getType()), true));
        attrs.add(attr("type_name", node.typeName(), true));
        attrs.add(attr("parent_id", String.valueOf(node.getParentId()), true));
        attrs.add(attr("ext_system_id", node.getExtSystemId(), false));
        attrs.add(attr("bo_type", node.getBoType(), false));
        attrs.add(attr("bo_id", node.getBoId(), false));
        attrs.add(attr("template_id", node.getTemplateId(), true));
        node.getProperties().forEach((k, v) -> attrs.add(attr(k, v, false)));
        boolean sap = node.getExtSystemId() != null && !node.getExtSystemId().isBlank();
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("subtype", String.valueOf(node.getType()));
        return new ObjectDump(String.valueOf(node.getId()), node.typeName(), node.getName(), attrs,
                new ArrayList<>(node.getCategories()), extra, sap);
    }

    public void update(ObjectDump dump) {
        FakeNode node = require(Long.parseLong(dump.id()));
        if (dump.sapLinked() || (node.getExtSystemId() != null && !node.getExtSystemId().isBlank())) {
            throw new SessionException("Refusing to mutate SAP-linked Business Workspace (ext_system_id="
                    + node.getExtSystemId() + ")");
        }
        if (dump.attributes() != null) {
            dump.attributes().stream()
                    .filter(a -> "name".equals(a.name()) && !a.readOnly())
                    .findFirst()
                    .ifPresent(a -> node.setName(a.first()));
        }
        if (dump.categories() != null) {
            node.getCategories().clear();
            node.getCategories().addAll(dump.categories());
        }
    }

    public SearchResult search(SearchRequest request) {
        String q = request.query() == null ? "" : request.query().trim();
        List<List<String>> rows = new ArrayList<>();
        for (FakeNode node : nodes.values()) {
            boolean match = q.isBlank()
                    || node.getName().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))
                    || String.valueOf(node.getId()).equals(q);
            if (match) {
                rows.add(List.of(String.valueOf(node.getId()), node.getName(), node.typeName(),
                        String.valueOf(node.getType())));
            }
            if (rows.size() >= request.limit()) {
                break;
            }
        }
        return new SearchResult(List.of("id", "name", "type_name", "type"), rows, rows.size());
    }

    public List<BusinessWorkspace> workspaces() {
        return nodes.values().stream()
                .filter(n -> n.getType() == 848)
                .map(this::toWorkspace)
                .toList();
    }

    public BusinessWorkspace workspace(long id) {
        FakeNode node = require(id);
        if (node.getType() != 848) {
            throw new SessionException("Node " + id + " is not a Business Workspace");
        }
        return toWorkspace(node);
    }

    public byte[] content(long id) {
        return require(id).getContent();
    }

    public JobList listJobs(JobFilter filter) {
        List<JobInfo> jobs = new ArrayList<>();
        String q = filter == null || filter.nameContains() == null ? "" : filter.nameContains().toLowerCase(Locale.ROOT);
        for (FakeAgent agent : agents.values()) {
            JobInfo info = agent.toInfo();
            if (!q.isBlank() && (info.objectName() == null
                    || !info.objectName().toLowerCase(Locale.ROOT).contains(q))) {
                continue;
            }
            if (filter != null && filter.inactive() != null && info.inactive() != filter.inactive()) {
                continue;
            }
            jobs.add(info);
        }
        return new JobList(jobs);
    }

    public JobDetail getJob(String jobId) {
        FakeAgent agent = requireAgent(jobId);
        List<JobReport> reports = new ArrayList<>();
        for (Long reportId : agent.getReportIds()) {
            FakeNode node = nodes.get(reportId);
            if (node == null) {
                continue;
            }
            reports.add(new JobReport(
                    String.valueOf(node.getId()),
                    node.getName(),
                    node.getProperties().getOrDefault("create_date", ""),
                    "crtext",
                    agent.getName()
            ));
        }
        return new JobDetail(agent.toInfo(), reports);
    }

    public synchronized void runJob(String jobId) {
        FakeAgent agent = requireAgent(jobId);
        String stamp = LocalDateTime.now().format(TS);
        agent.setRunning(false);
        agent.setEnabled(true);
        agent.setLastRun(stamp);
        agent.setLastReturn("0");
        agent.setCurrentStatus("Completed successfully (mock)");
        long reportsFolder = 1200;
        if (!nodes.containsKey(reportsFolder)) {
            reportsFolder = 1000;
        }
        FakeNode report = new FakeNode();
        long id = reportSeq.incrementAndGet();
        report.setId(id);
        report.setName(agent.getName().replace(' ', '_') + "_" + stamp.replace(":", "-").replace("T", "_") + ".log");
        report.setType(144);
        report.setParentId(reportsFolder);
        report.getProperties().put("create_date", stamp);
        String body = "Agent Report: " + agent.getName() + "\nThread: " + agent.getThread()
                + "\nStarted: " + stamp + "\nFinished: " + stamp + "\nReturn: 0\n\nMock CS agent completed RUN NOW.\n";
        report.setContent(body.getBytes(StandardCharsets.UTF_8));
        nodes.put(id, report);
        agent.getReportIds().add(0, id);
    }

    private FakeAgent requireAgent(String jobId) {
        long id;
        try {
            id = Long.parseLong(jobId);
        } catch (NumberFormatException e) {
            throw new SessionException("OTCS agent id must be numeric, got: " + jobId);
        }
        FakeAgent agent = agents.get(id);
        if (agent == null) {
            throw new SessionException("OTCS agent not found: " + jobId);
        }
        return agent;
    }

    public FakeNode require(long id) {
        FakeNode node = nodes.get(id);
        if (node == null) {
            throw new SessionException("OTCS node not found: " + id);
        }
        return node;
    }

    private BrowseNode toBrowse(FakeNode node) {
        return new BrowseNode(String.valueOf(node.getId()), node.getName(), node.typeName(), node.getType(),
                node.folder(), node.typeName());
    }

    private BusinessWorkspace toWorkspace(FakeNode node) {
        return new BusinessWorkspace(
                String.valueOf(node.getId()),
                node.getName(),
                node.getTemplateId(),
                node.getExtSystemId(),
                node.getBoType(),
                node.getBoId(),
                String.valueOf(node.getParentId())
        );
    }

    private static AttributeValue attr(String name, String value, boolean readOnly) {
        return new AttributeValue(name, "string", false, List.of(value == null ? "" : value), readOnly);
    }

    private void load(JsonNode root) {
        version = root.path("version").asText("24.2");
        for (JsonNode n : root.path("nodes")) {
            FakeNode node = new FakeNode();
            node.setId(n.path("id").asLong());
            node.setName(n.path("name").asText());
            node.setType(n.path("type").asInt());
            node.setParentId(n.path("parentId").asLong(-1));
            node.setVolume(n.path("volume").asBoolean(false));
            node.setExtSystemId(n.path("extSystemId").asText(""));
            node.setBoType(n.path("boType").asText(""));
            node.setBoId(n.path("boId").asText(""));
            node.setTemplateId(n.path("templateId").asText(""));
            if (n.has("content")) {
                node.setContent(n.path("content").asText().getBytes(StandardCharsets.UTF_8));
            }
            for (JsonNode cat : n.path("categories")) {
                Map<String, List<String>> attrs = new LinkedHashMap<>();
                cat.path("attributes").fields().forEachRemaining(e -> {
                    List<String> values = new ArrayList<>();
                    e.getValue().forEach(v -> values.add(v.asText()));
                    attrs.put(e.getKey(), values);
                });
                node.getCategories().add(new CategoryValue(
                        cat.path("categoryId").asText(),
                        cat.path("categoryName").asText(),
                        attrs
                ));
            }
            nodes.put(node.getId(), node);
        }
        for (JsonNode a : root.path("agents")) {
            FakeAgent agent = new FakeAgent();
            agent.setId(a.path("id").asLong());
            agent.setName(a.path("name").asText());
            agent.setThread(a.path("thread").asText("llserver"));
            agent.setEnabled(a.path("enabled").asBoolean(true));
            agent.setRunning(a.path("running").asBoolean(false));
            agent.setInterval(a.path("interval").asText(""));
            agent.setLastRun(a.path("lastRun").asText(""));
            agent.setNextRun(a.path("nextRun").asText(""));
            agent.setLastReturn(a.path("lastReturn").asText(""));
            agent.setCurrentStatus(a.path("currentStatus").asText(""));
            a.path("reports").forEach(r -> agent.getReportIds().add(r.asLong()));
            agents.put(agent.getId(), agent);
        }
    }
}
