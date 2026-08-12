package com.dctm.workbench.otcs.rest;

import com.dctm.workbench.core.AttributeValue;
import com.dctm.workbench.core.AuthMode;
import com.dctm.workbench.core.BrowseFilter;
import com.dctm.workbench.core.BrowseNode;
import com.dctm.workbench.core.BusinessWorkspace;
import com.dctm.workbench.core.CapabilitySets;
import com.dctm.workbench.core.CategoryValue;
import com.dctm.workbench.core.FolderContents;
import com.dctm.workbench.core.JobDetail;
import com.dctm.workbench.core.JobFilter;
import com.dctm.workbench.core.JobInfo;
import com.dctm.workbench.core.JobList;
import com.dctm.workbench.core.JobReport;
import com.dctm.workbench.core.JobSupport;
import com.dctm.workbench.core.ObjectDump;
import com.dctm.workbench.core.OtcsBridge;
import com.dctm.workbench.core.OtcsConnectRequest;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.SearchRequest;
import com.dctm.workbench.core.SearchResult;
import com.dctm.workbench.core.DiagnosticRedactor;
import com.dctm.workbench.core.RestCapable;
import com.dctm.workbench.core.RestProxyRequest;
import com.dctm.workbench.core.RestProxyResponse;
import com.dctm.workbench.core.ServerInfo;
import com.dctm.workbench.core.SessionException;
import com.dctm.workbench.otds.OtdsClient;
import com.dctm.workbench.otds.OtdsToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class OtcsRestBridge implements OtcsBridge, RestCapable {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final OtdsClient otds = new OtdsClient();
    private String cgiRoot;
    private String ticket;
    private String otdsTicket;
    private String bearer;
    private ServerInfo info;

    @Override
    public ServerInfo connect(OtcsConnectRequest request) {
        cgiRoot = trimSlash(request.cgiRoot());
        if (cgiRoot.isBlank()) {
            throw new SessionException("OTCS CGI root is required");
        }
        AuthMode mode = request.authMode() == null ? AuthMode.PASSWORD : request.authMode();
        if (mode == AuthMode.OTDS_BEARER) {
            bearer = request.bearerToken();
        } else if (mode == AuthMode.OTDS_PASSWORD && request.otdsUrl() != null) {
            OtdsToken token = otds.passwordGrant(request.otdsUrl(), "otcs", null, request.username(), request.password());
            bearer = token.accessToken();
        } else if (mode == AuthMode.OTDS_SSO) {
            throw new SessionException("OTDS browser SSO is not implemented yet");
        } else {
            ticket = loginTicket(request.username(), request.password());
        }
        String version = request.reportedVersion() == null ? "unknown" : request.reportedVersion();
        try {
            JsonNode enterprise = getJson(cgiRoot + "/api/v1/volumes/141");
            JsonNode props = dataProperties(enterprise);
            if (!props.path("id").isMissingNode()) {
                version = version.equals("unknown") ? "21.2+" : version;
            }
        } catch (SessionException ignored) {
            // v1 volumes may differ; still connected if auth succeeded
        }
        info = ServerInfo.otcs(Protocol.OTCS_REST, cgiRoot, version, request.username(), CapabilitySets.otcsRest());
        return info;
    }

    private String loginTicket(String username, char[] password) {
        try {
            String body = "username=" + enc(username) + "&password=" + enc(password == null ? "" : new String(password));
            HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(cgiRoot + "/api/v1/auth"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)));
            JsonNode json = mapper.readTree(response.body());
            String t = json.path("ticket").asText();
            if (t.isBlank()) {
                throw new SessionException("OTCS auth did not return a ticket");
            }
            return t;
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("OTCS auth failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<BrowseNode> volumes() {
        List<BrowseNode> volumes = new ArrayList<>();
        volumes.add(volume(141, "Enterprise Workspace"));
        try {
            volumes.add(volume(142, "Personal Workspace"));
        } catch (SessionException ignored) {
            // personal volume optional
        }
        return volumes;
    }

    private BrowseNode volume(int subtype, String fallbackName) {
        JsonNode json = getJson(cgiRoot + "/api/v1/volumes/" + subtype);
        JsonNode props = dataProperties(json);
        String id = props.path("id").asText();
        String name = props.path("name").asText(fallbackName);
        int type = props.path("type").asInt(subtype);
        return new BrowseNode(id, name, fallbackName, type, true, fallbackName);
    }

    @Override
    public FolderContents children(long nodeId, BrowseFilter filter) {
        JsonNode json = getJson(cgiRoot + "/api/v2/nodes/" + nodeId + "/nodes");
        List<BrowseNode> children = new ArrayList<>();
        for (JsonNode item : results(json)) {
            BrowseNode node = toBrowse(item);
            if (filter == null || filter.matches(node)) {
                children.add(node);
            }
        }
        ObjectDump parent = node(nodeId);
        return new FolderContents(String.valueOf(nodeId), parent.objectName(), children);
    }

    @Override
    public ObjectDump node(long nodeId) {
        JsonNode json = getJson(cgiRoot + "/api/v2/nodes/" + nodeId + "?fields=properties,categories,permissions");
        JsonNode data = json.path("results").path("data");
        if (data.isMissingNode()) {
            data = json.path("data");
        }
        JsonNode props = data.path("properties");
        List<AttributeValue> attrs = new ArrayList<>();
        props.fields().forEachRemaining(e -> attrs.add(new AttributeValue(
                e.getKey(), "string", e.getValue().isArray(), scalarList(e.getValue()),
                "id".equals(e.getKey()) || "type".equals(e.getKey())
        )));
        List<CategoryValue> categories = new ArrayList<>();
        JsonNode cats = data.path("categories");
        if (cats.isObject()) {
            cats.fields().forEachRemaining(e -> {
                Map<String, List<String>> map = new LinkedHashMap<>();
                e.getValue().fields().forEachRemaining(a -> map.put(a.getKey(), scalarList(a.getValue())));
                categories.add(new CategoryValue(e.getKey(), e.getKey(), map));
            });
        } else if (cats.isArray()) {
            cats.forEach(c -> {
                Map<String, List<String>> map = new LinkedHashMap<>();
                c.fields().forEachRemaining(a -> {
                    if (!"id".equals(a.getKey())) {
                        map.put(a.getKey(), scalarList(a.getValue()));
                    }
                });
                categories.add(new CategoryValue(c.path("id").asText(), c.path("name").asText("category"), map));
            });
        }
        String ext = props.path("ext_system_id").asText("");
        boolean sap = ext != null && !ext.isBlank();
        return new ObjectDump(
                props.path("id").asText(String.valueOf(nodeId)),
                typeName(props.path("type").asInt()),
                props.path("name").asText(),
                attrs,
                categories,
                Map.of("subtype", props.path("type").asText()),
                sap
        );
    }

    @Override
    public void updateNode(ObjectDump dump) {
        if (dump.sapLinked()) {
            throw new SessionException("Refusing to mutate SAP-linked Business Workspace");
        }
        try {
            var body = mapper.createObjectNode();
            body.put("name", dump.objectName());
            String boundary = "----wb" + UUID.randomUUID();
            String payload = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"body\"\r\n\r\n"
                    + mapper.writeValueAsString(body) + "\r\n"
                    + "--" + boundary + "--\r\n";
            HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(cgiRoot + "/api/v2/nodes/" + dump.id()))
                    .PUT(HttpRequest.BodyPublishers.ofString(payload))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary));
            refreshTicket(response);
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("OTCS update failed: " + e.getMessage(), e);
        }
    }

    @Override
    public SearchResult search(SearchRequest request) {
        JsonNode json = getJson(cgiRoot + "/api/v2/search?where=" + enc(request.query()) + "&limit=" + request.limit());
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode item : results(json)) {
            JsonNode props = item.path("data").path("properties");
            if (props.isMissingNode()) {
                props = item.path("properties");
            }
            rows.add(List.of(props.path("id").asText(), props.path("name").asText(),
                    typeName(props.path("type").asInt()), props.path("type").asText()));
        }
        return new SearchResult(List.of("id", "name", "type_name", "type"), rows, rows.size());
    }

    @Override
    public List<BusinessWorkspace> workspaces() {
        try {
            JsonNode json = getJson(cgiRoot + "/api/v2/businessworkspaces");
            List<BusinessWorkspace> list = new ArrayList<>();
            for (JsonNode item : results(json)) {
                JsonNode props = item.path("data").path("properties");
                if (props.isMissingNode()) {
                    props = item.path("properties");
                }
                list.add(toWorkspace(props));
            }
            return list;
        } catch (SessionException e) {
            // fall back: browse enterprise for type 848 is left to the UI; empty if API missing
            return List.of();
        }
    }

    @Override
    public BusinessWorkspace workspace(long nodeId) {
        try {
            JsonNode json = getJson(cgiRoot + "/api/v2/businessworkspaces/" + nodeId);
            JsonNode props = json.path("results").path("data").path("properties");
            if (props.isMissingNode()) {
                props = dataProperties(json);
            }
            return toWorkspace(props);
        } catch (SessionException e) {
            ObjectDump dump = node(nodeId);
            return new BusinessWorkspace(dump.id(), dump.objectName(), dump.attr("template_id"),
                    dump.attr("ext_system_id"), dump.attr("bo_type"), dump.attr("bo_id"), dump.attr("parent_id"));
        }
    }

    @Override
    public byte[] getContent(long nodeId) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(cgiRoot + "/api/v2/nodes/" + nodeId + "/content"));
            applyAuth(builder);
            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            refreshTicket(response);
            if (response.statusCode() >= 400) {
                throw new SessionException("Content HTTP " + response.statusCode());
            }
            return response.body();
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("Content download failed: " + e.getMessage(), e);
        }
    }

    @Override
    public JobList listJobs(JobFilter filter) {
        JsonNode json = tryGetJson(cgiRoot + "/api/v2/agents");
        if (json == null) {
            json = tryGetJson(cgiRoot + "/api/v2/scheduledjobs");
        }
        if (json == null) {
            return new JobList(List.of());
        }
        List<JobInfo> jobs = new ArrayList<>();
        String q = filter == null || filter.nameContains() == null ? "" : filter.nameContains().toLowerCase(Locale.ROOT);
        for (JsonNode item : agentItems(json)) {
            JobInfo info = toJob(item);
            if (!q.isBlank() && (info.objectName() == null || !info.objectName().toLowerCase(Locale.ROOT).contains(q))) {
                continue;
            }
            if (filter != null && filter.inactive() != null && info.inactive() != filter.inactive()) {
                continue;
            }
            jobs.add(info);
        }
        return new JobList(jobs);
    }

    @Override
    public JobDetail getJob(String jobId) {
        JsonNode json = tryGetJson(cgiRoot + "/api/v2/agents/" + enc(jobId));
        if (json == null) {
            json = tryGetJson(cgiRoot + "/api/v2/scheduledjobs/" + enc(jobId));
        }
        JobInfo info;
        JsonNode reportsNode = null;
        if (json != null) {
            JsonNode item = json.path("results").isMissingNode() ? json : json.path("results");
            if (item.isArray() && item.size() > 0) {
                item = item.get(0);
            }
            info = toJob(item);
            reportsNode = item.path("data").path("reports");
            if (reportsNode.isMissingNode()) {
                reportsNode = item.path("reports");
            }
        } else {
            info = listJobs(JobFilter.none()).jobs().stream()
                    .filter(j -> jobId.equals(j.id()))
                    .findFirst()
                    .orElseThrow(() -> new SessionException("OTCS agent not found: " + jobId
                            + ". This Content Server may not expose scheduled agents via REST."));
        }
        List<JobReport> reports = new ArrayList<>();
        if (reportsNode != null && reportsNode.isArray()) {
            for (JsonNode r : reportsNode) {
                JsonNode props = r.path("data").path("properties");
                if (props.isMissingNode()) {
                    props = r.has("id") ? r : r.path("properties");
                }
                reports.add(new JobReport(
                        props.path("id").asText(),
                        props.path("name").asText(props.path("object_name").asText()),
                        props.path("create_date").asText(props.path("created").asText()),
                        props.path("mime_type").asText("crtext"),
                        info.objectName()
                ));
            }
        }
        return new JobDetail(info, reports);
    }

    @Override
    public void runJob(String jobId) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(cgiRoot + "/api/v2/agents/" + enc(jobId) + "/run"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("Accept", "application/json");
            send(builder);
        } catch (SessionException e) {
            throw new SessionException("Could not run CS agent " + jobId
                    + ". Scheduled agents are not always exposed on CS REST. " + e.getMessage(), e);
        }
    }

    @Override
    public void reset() {
        throw new SessionException("reset is only available on mock OTCS");
    }

    @Override
    public void disconnect() {
        ticket = null;
        bearer = null;
        otdsTicket = null;
    }

    private JsonNode tryGetJson(String url) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET().header("Accept", "application/json");
            applyAuth(builder);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            refreshTicket(response);
            if (response.statusCode() >= 400) {
                return null;
            }
            return mapper.readTree(response.body());
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode getJson(String url) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET().header("Accept", "application/json");
            HttpResponse<String> response = send(builder);
            return mapper.readTree(response.body());
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("OTCS GET failed: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            applyAuth(builder);
            builder.timeout(Duration.ofSeconds(60));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            refreshTicket(response);
            if (response.statusCode() >= 400) {
                throw new SessionException("OTCS HTTP " + response.statusCode() + " " + truncate(response.body()));
            }
            return response;
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("OTCS HTTP failed: " + e.getMessage(), e);
        }
    }

    private void applyAuth(HttpRequest.Builder builder) {
        if (bearer != null && !bearer.isBlank()) {
            builder.header("Authorization", bearer.startsWith("Bearer") ? bearer : "Bearer " + bearer);
        }
        if (otdsTicket != null) {
            builder.header("OTDSTicket", otdsTicket);
        }
        if (ticket != null) {
            builder.header("OTCSTicket", ticket);
        }
    }

    private void refreshTicket(HttpResponse<?> response) {
        response.headers().firstValue("OTCSTicket").ifPresent(t -> ticket = t);
        response.headers().map().forEach((k, v) -> {
            if (k != null && k.equalsIgnoreCase("OTCSTicket") && !v.isEmpty()) {
                ticket = v.get(0);
            }
        });
    }

    private static List<JsonNode> results(JsonNode json) {
        JsonNode results = json.path("results");
        List<JsonNode> list = new ArrayList<>();
        if (results.isArray()) {
            results.forEach(list::add);
        } else if (results.has("data")) {
            list.add(results);
        }
        return list;
    }

    private static JsonNode dataProperties(JsonNode json) {
        JsonNode props = json.path("results").path("data").path("properties");
        if (!props.isMissingNode()) {
            return props;
        }
        if (json.has("properties")) {
            return json.get("properties");
        }
        return json.path("data").path("properties");
    }

    private static BrowseNode toBrowse(JsonNode item) {
        JsonNode props = item.path("data").path("properties");
        if (props.isMissingNode()) {
            props = item.path("properties");
        }
        int type = props.path("type").asInt();
        boolean folder = type == 0 || type == 141 || type == 142 || type == 848;
        return new BrowseNode(props.path("id").asText(), props.path("name").asText(), typeName(type), type, folder,
                typeName(type));
    }

    private static List<JsonNode> agentItems(JsonNode json) {
        List<JsonNode> items = new ArrayList<>();
        if (json.path("results").isArray()) {
            json.path("results").forEach(items::add);
        } else if (json.path("agents").isArray()) {
            json.path("agents").forEach(items::add);
        } else if (json.path("data").isArray()) {
            json.path("data").forEach(items::add);
        } else if (!json.path("results").path("data").isMissingNode()) {
            items.add(json.path("results"));
        }
        return items;
    }

    private static JobInfo toJob(JsonNode item) {
        JsonNode props = item.path("data").path("properties");
        if (props.isMissingNode() || props.isEmpty()) {
            props = item.has("name") || item.has("id") ? item : item.path("properties");
        }
        boolean enabled = props.path("enabled").asBoolean(
                !"true".equalsIgnoreCase(props.path("inactive").asText())
                        && !"T".equalsIgnoreCase(props.path("inactive").asText()));
        boolean running = props.path("running").asBoolean(props.path("run_now").asBoolean(false));
        String lastReturn = props.path("last_return").asText(props.path("exit_code").asText(""));
        boolean inactive = !enabled;
        return new JobInfo(
                props.path("id").asText(),
                props.path("name").asText(props.path("object_name").asText()),
                props.path("thread").asText(props.path("method_name").asText("llserver")),
                inactive,
                running,
                props.path("last_run").asText(props.path("a_last_completion").asText()),
                props.path("next_run").asText(props.path("a_next_invocation").asText()),
                props.path("interval").asText(props.path("run_interval").asText()),
                lastReturn,
                props.path("status").asText(props.path("current_status").asText()),
                JobSupport.status(inactive, running, lastReturn)
        );
    }

    private static BusinessWorkspace toWorkspace(JsonNode props) {
        return new BusinessWorkspace(
                props.path("id").asText(),
                props.path("name").asText(),
                props.path("template_id").asText(),
                props.path("ext_system_id").asText(),
                props.path("bo_type").asText(),
                props.path("bo_id").asText(),
                props.path("parent_id").asText()
        );
    }

    private static String typeName(int type) {
        return switch (type) {
            case 0 -> "Folder";
            case 141 -> "Enterprise Workspace";
            case 142 -> "Personal Workspace";
            case 144 -> "Document";
            case 848 -> "Business Workspace";
            default -> "Node";
        };
    }

    private static List<String> scalarList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || node.isNull() || node.isMissingNode()) {
            return values;
        }
        if (node.isArray()) {
            node.forEach(n -> values.add(n.asText()));
        } else {
            values.add(node.asText());
        }
        return values;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    @Override
    public RestProxyResponse restProxy(RestProxyRequest request) {
        long start = System.currentTimeMillis();
        String method = request.method() == null ? "GET" : request.method().toUpperCase(Locale.ROOT);
        String url = resolveProxyUrl(request.path());
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url));
        String body = request.body() == null ? "" : request.body();
        switch (method) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            case "DELETE" -> builder.DELETE();
            default -> builder.GET();
        }
        if (request.headers() != null) {
            request.headers().forEach((k, v) -> {
                if (k != null && v != null) {
                    String lower = k.toLowerCase(Locale.ROOT);
                    if (!lower.equals("authorization") && !lower.equals("otcsticket") && !lower.equals("otdsticket")) {
                        builder.header(k, v);
                    }
                }
            });
        }
        applyAuth(builder);
        builder.timeout(Duration.ofSeconds(60));
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            refreshTicket(response);
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
        } catch (Exception e) {
            throw new SessionException("REST proxy failed: " + e.getMessage(), e);
        }
    }

    private String resolveProxyUrl(String path) {
        if (path == null || path.isBlank()) {
            return cgiRoot + "/";
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (path.startsWith("/")) {
            return cgiRoot + path;
        }
        return cgiRoot + "/" + path;
    }
}
