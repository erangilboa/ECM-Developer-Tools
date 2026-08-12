package com.dctm.workbench.rest;

import com.dctm.workbench.core.AttributeValue;
import com.dctm.workbench.core.BrowseFilter;
import com.dctm.workbench.core.BrowseNode;
import com.dctm.workbench.core.Capability;
import com.dctm.workbench.core.CapabilitySets;
import com.dctm.workbench.core.ContentPayload;
import com.dctm.workbench.core.ContentTypes;
import com.dctm.workbench.core.DocumentumSession;
import com.dctm.workbench.core.DqlRequest;
import com.dctm.workbench.core.DqlResult;
import com.dctm.workbench.core.FolderContents;
import com.dctm.workbench.core.IapiResult;
import com.dctm.workbench.core.JobFilter;
import com.dctm.workbench.core.JobInfo;
import com.dctm.workbench.core.JobList;
import com.dctm.workbench.core.JobSupport;
import com.dctm.workbench.core.ObjectDump;
import com.dctm.workbench.core.Product;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.QueryMode;
import com.dctm.workbench.core.RestCapable;
import com.dctm.workbench.core.RestProxyRequest;
import com.dctm.workbench.core.RestProxyResponse;
import com.dctm.workbench.core.SearchRequest;
import com.dctm.workbench.core.SearchResult;
import com.dctm.workbench.core.DiagnosticRedactor;
import com.dctm.workbench.core.ServerInfo;
import com.dctm.workbench.core.SessionException;
import com.dctm.workbench.core.TypeDictionary;
import com.dctm.workbench.core.TypeInfo;
import com.dctm.workbench.core.UnsupportedCapabilityException;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DctmRestSession implements DocumentumSession, RestCapable {

    private final HttpSupport http = new HttpSupport();
    private final String base;
    private final String repo;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private ServerInfo info;

    public DctmRestSession(String restBaseUrl, String repository, String username, char[] password, String bearer) {
        this.base = http.trimSlash(restBaseUrl);
        this.repo = repository;
        if (bearer != null && !bearer.isBlank()) {
            headers.put("Authorization", bearer.startsWith("Bearer") ? bearer : "Bearer " + bearer);
        } else {
            headers.put("Authorization", http.basic(username, password));
        }
        headers.put("Accept", "application/vnd.emc.documentum+json, application/json");
        connect(username);
    }

    private void connect(String username) {
        HttpResponse<String> home = http.send(HttpRequest.newBuilder(URI.create(base + "/")), headers);
        captureCsrf(home);
        JsonNode homeJson = http.parse(home.body());
        String version = firstText(homeJson, "properties", "product-version");
        if (version.isBlank()) {
            version = firstText(homeJson, "product-version");
        }
        if (version.isBlank()) {
            try {
                JsonNode product = http.getJson(base + "/product-info", headers);
                version = firstText(product, "properties", "product-version");
                if (version.isBlank()) {
                    version = firstText(product, "major");
                }
            } catch (SessionException ignored) {
                version = "unknown";
            }
        }
        this.info = ServerInfo.documentum(Protocol.DCTM_REST, repo, version, username, CapabilitySets.dctmRest());
    }

    private void captureCsrf(HttpResponse<?> response) {
        http.header(response, "X-CSRF-TOKEN").ifPresent(t -> headers.put("X-CSRF-TOKEN", t));
        List<String> cookies = response.headers().allValues("Set-Cookie");
        if (!cookies.isEmpty()) {
            String cookie = cookies.stream()
                    .map(c -> c.split(";", 2)[0])
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            headers.merge("Cookie", cookie, (a, b) -> a + "; " + b);
        }
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
        DqlResult result = executeDql(DqlRequest.select("SELECT r_object_id, object_name, r_object_type FROM dm_cabinet"));
        return rowsToNodes(result, true);
    }

    @Override
    public FolderContents listChildren(String id, BrowseFilter filter) {
        ObjectDump parent = dump(id);
        DqlResult result = executeDql(DqlRequest.select(
                "SELECT r_object_id, object_name, r_object_type FROM dm_sysobject WHERE folder('" + id + "')"));
        List<BrowseNode> nodes = rowsToNodes(result, false).stream()
                .filter(n -> filter == null || filter.matches(n))
                .toList();
        return new FolderContents(id, parent.objectName(), nodes);
    }

    @Override
    public ObjectDump dump(String id) {
        JsonNode json = http.getJson(base + "/repositories/" + http.encode(repo) + "/objects/" + http.encode(id), headers);
        JsonNode props = json.has("properties") ? json.get("properties") : json;
        List<AttributeValue> attrs = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = props.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            List<String> values = new ArrayList<>();
            if (e.getValue().isArray()) {
                e.getValue().forEach(v -> values.add(v.asText()));
            } else if (!e.getValue().isNull()) {
                values.add(e.getValue().asText());
            }
            boolean repeating = e.getValue().isArray();
            attrs.add(new AttributeValue(e.getKey(), "string", repeating, values, e.getKey().startsWith("r_")));
        }
        String type = text(props, "r_object_type");
        String name = text(props, "object_name");
        return new ObjectDump(id, type, name, attrs, List.of(), Map.of(), false);
    }

    @Override
    public void saveDump(ObjectDump dump) {
        require(Capability.OBJECT_UPDATE, "REST object update");
        var body = http.mapper.createObjectNode();
        var props = body.putObject("properties");
        if (dump.attributes() != null) {
            dump.attributes().stream().filter(a -> !a.readOnly()).forEach(a -> {
                if (a.repeating()) {
                    var arr = props.putArray(a.name());
                    a.values().forEach(arr::add);
                } else {
                    props.put(a.name(), a.first());
                }
            });
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(base + "/repositories/" + http.encode(repo) + "/objects/" + http.encode(dump.id())))
                .method("POST", HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Content-Type", "application/vnd.emc.documentum+json");
        http.send(builder, headers);
    }

    @Override
    public SearchResult search(SearchRequest request) {
        DqlResult dql = executeDql(DqlRequest.select(request.query()));
        return new SearchResult(dql.columns(), dql.rows(), dql.rowCount());
    }

    @Override
    public ContentPayload getContent(String id) {
        HttpResponse<byte[]> response;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                    URI.create(base + "/repositories/" + http.encode(repo) + "/objects/" + http.encode(id) + "/contents/content"));
            headers.forEach(builder::header);
            response = http.http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new SessionException("Content download failed: " + e.getMessage(), e);
        }
        if (response.statusCode() >= 400) {
            throw new SessionException("Content HTTP " + response.statusCode());
        }
        ObjectDump dump = dump(id);
        String mime = ContentTypes.guess(dump.objectName(), dump.attr("a_content_type"));
        return new ContentPayload(dump.objectName(), mime, response.body());
    }

    @Override
    public void close() {
        // HTTP is stateless aside from CSRF cookie
    }

    @Override
    public DqlResult executeDql(DqlRequest request) {
        String dql = request.dql() == null ? "" : request.dql().trim();
        if (request.mode() == QueryMode.EXEC || looksMutating(dql)) {
            throw new UnsupportedCapabilityException(Capability.DQL_EXECUTE,
                    "Documentum REST is SELECT-only. Use mock DFC or live DFC for EXECUTE DQL.");
        }
        require(Capability.DQL_SELECT, "DQL SELECT");
        String rewritten = rewriteJobQuery(dql);
        String url = base + "/repositories/" + http.encode(repo) + "?dql=" + http.encode(rewritten)
                + "&items-per-page=" + request.maxRows();
        long start = System.currentTimeMillis();
        JsonNode json = http.getJson(url, headers);
        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        JsonNode entries = json.path("entries");
        if (entries.isArray() && entries.size() > 0) {
            JsonNode firstProps = propsOf(entries.get(0));
            firstProps.fieldNames().forEachRemaining(columns::add);
            for (JsonNode entry : entries) {
                JsonNode props = propsOf(entry);
                List<String> row = new ArrayList<>();
                for (String col : columns) {
                    JsonNode v = props.get(col);
                    row.add(v == null || v.isNull() ? "" : (v.isArray() ? joinArray(v) : v.asText()));
                }
                rows.add(row);
            }
        }
        return new DqlResult(columns, rows, rows.size(), dql, System.currentTimeMillis() - start);
    }

    @Override
    public IapiResult iapi(String command) {
        throw new UnsupportedCapabilityException(Capability.IAPI, "IAPI requires DFC (mock or live)");
    }

    @Override
    public JobList listJobs(JobFilter filter) {
        DqlResult result = executeDql(DqlRequest.select(
                "SELECT r_object_id, object_name, method_name, is_inactive, run_now, a_last_completion, a_next_invocation, run_interval, a_last_return, a_current_status FROM dm_job"));
        List<JobInfo> jobs = new ArrayList<>();
        for (List<String> row : result.rows()) {
            JobInfo job = JobSupport.fromRow(result, row);
            if (filter != null && filter.nameContains() != null && !filter.nameContains().isBlank()) {
                if (job.objectName() == null || !job.objectName().toLowerCase(Locale.ROOT)
                        .contains(filter.nameContains().toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }
            jobs.add(job);
        }
        return new JobList(jobs);
    }

    @Override
    public void runJob(String jobId) {
        var body = http.mapper.createObjectNode();
        body.putObject("properties").put("run_now", true);
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(base + "/repositories/" + http.encode(repo) + "/objects/" + http.encode(jobId)))
                .method("POST", HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .header("Content-Type", "application/vnd.emc.documentum+json");
        http.send(builder, headers);
    }

    @Override
    public TypeDictionary types() {
        JsonNode json = http.getJson(base + "/repositories/" + http.encode(repo) + "/types", headers);
        List<TypeInfo> types = new ArrayList<>();
        JsonNode entries = json.path("entries");
        if (entries.isArray()) {
            for (JsonNode entry : entries) {
                JsonNode props = propsOf(entry);
                String name = text(props, "name");
                if (name.isBlank()) {
                    name = text(entry, "title");
                }
                String superName = text(props, "parent");
                List<String> attrs = new ArrayList<>();
                JsonNode attrNodes = props.path("attributes");
                if (attrNodes.isArray()) {
                    attrNodes.forEach(a -> attrs.add(a.isTextual() ? a.asText() : a.path("name").asText()));
                }
                types.add(new TypeInfo(name, superName.isBlank() ? null : superName, attrs));
            }
        }
        return new TypeDictionary(types);
    }

    @Override
    public void checkout(String id) {
        throw new UnsupportedCapabilityException(Capability.CHECKOUT, "Checkout via REST is not in MVP");
    }

    @Override
    public void checkin(String id) {
        throw new UnsupportedCapabilityException(Capability.CHECKOUT, "Checkin via REST is not in MVP");
    }

    @Override
    public void resetMock() {
        throw new SessionException("resetMock is only available on mock sessions");
    }

    static String rewriteJobQuery(String dql) {
        String compact = dql.replaceAll("\\s+", " ");
        if (compact.matches("(?i).*\\bFROM\\s+dm_job\\b.*")
                && !compact.matches("(?i).*\\bmethod_id\\b.*")) {
            return dql.replaceAll("(?i)SELECT\\s+\\*", "SELECT *, r_object_id AS method_id");
        }
        return dql;
    }

    private static boolean looksMutating(String dql) {
        String head = dql.stripLeading().toUpperCase(Locale.ROOT);
        return head.startsWith("UPDATE") || head.startsWith("DELETE") || head.startsWith("CREATE")
                || head.startsWith("INSERT") || head.startsWith("EXECUTE");
    }

    private List<BrowseNode> rowsToNodes(DqlResult result, boolean folder) {
        List<BrowseNode> nodes = new ArrayList<>();
        for (List<String> row : result.rows()) {
            String type = col(result, row, "r_object_type");
            boolean isFolder = folder || "dm_folder".equals(type) || "dm_cabinet".equals(type);
            nodes.add(new BrowseNode(col(result, row, "r_object_id"), col(result, row, "object_name"), type,
                    isFolder ? 0 : 9, isFolder, type));
        }
        return nodes;
    }

    private static JsonNode propsOf(JsonNode entry) {
        JsonNode content = entry.path("content");
        if (content.has("properties")) {
            return content.get("properties");
        }
        if (entry.has("properties")) {
            return entry.get("properties");
        }
        return entry;
    }

    private static String firstText(JsonNode json, String... path) {
        JsonNode n = json;
        for (String p : path) {
            n = n.path(p);
        }
        return n.isMissingNode() || n.isNull() ? "" : n.asText();
    }

    private static String text(JsonNode json, String field) {
        JsonNode n = json.get(field);
        return n == null || n.isNull() ? "" : n.asText();
    }

    private static String joinArray(JsonNode v) {
        List<String> parts = new ArrayList<>();
        v.forEach(n -> parts.add(n.asText()));
        return String.join(",", parts);
    }

    private static String col(DqlResult result, List<String> row, String name) {
        for (int i = 0; i < result.columns().size(); i++) {
            if (result.columns().get(i).equalsIgnoreCase(name) && i < row.size()) {
                return row.get(i);
            }
        }
        return "";
    }

    @Override
    public RestProxyResponse restProxy(RestProxyRequest request) {
        long start = System.currentTimeMillis();
        String method = request.method() == null ? "GET" : request.method().toUpperCase(Locale.ROOT);
        String url = resolveProxyUrl(request.path());
        Map<String, String> hdrs = new LinkedHashMap<>(headers);
        if (request.headers() != null) {
            request.headers().forEach((k, v) -> {
                if (k == null || v == null) {
                    return;
                }
                String lower = k.toLowerCase(Locale.ROOT);
                if (!lower.equals("authorization") && !lower.equals("cookie")) {
                    hdrs.put(k, v);
                }
            });
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url));
        String body = request.body() == null ? "" : request.body();
        switch (method) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            case "DELETE" -> builder.DELETE();
            default -> builder.GET();
        }
        HttpResponse<String> response = http.sendAny(builder, hdrs);
        captureCsrf(response);
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        response.headers().map().forEach((k, values) -> {
            if (!values.isEmpty()) {
                responseHeaders.put(k, String.join(", ", values));
            }
        });
        return new RestProxyResponse(
                response.statusCode(),
                DiagnosticRedactor.redactHeaders(responseHeaders),
                response.body() == null ? "" : response.body(),
                System.currentTimeMillis() - start,
                url
        );
    }

    private String resolveProxyUrl(String path) {
        if (path == null || path.isBlank()) {
            return base + "/";
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (path.startsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }
}
