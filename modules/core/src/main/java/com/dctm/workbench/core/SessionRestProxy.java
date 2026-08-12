package com.dctm.workbench.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simulates REST responses for mock/DFC sessions so the REST explorer is testable without a live server.
 */
public class SessionRestProxy implements RestCapable {

    private static final Pattern DCTM_OBJECT = Pattern.compile("/objects/([0-9a-fA-F]{16})");
    private static final Pattern OTCS_NODE = Pattern.compile("/nodes/(\\d+)");
    private static final ObjectMapper JSON = Json.mapper();

    private final RepositorySession session;

    public SessionRestProxy(RepositorySession session) {
        this.session = session;
    }

    @Override
    public RestProxyResponse restProxy(RestProxyRequest request) {
        long start = System.currentTimeMillis();
        String method = request.method() == null ? "GET" : request.method().toUpperCase(Locale.ROOT);
        String path = request.path() == null ? "/" : request.path();
        try {
            if (session.product() == Product.DOCUMENTUM) {
                return handleDocumentum(method, path, request.body(), start);
            }
            return handleOtcs(method, path, start);
        } catch (SessionException e) {
            return response(400, e.getMessage(), path, start, Map.of("X-Mock-Rest", "session-proxy"));
        }
    }

    private RestProxyResponse handleDocumentum(String method, String path, String body, long start) {
        Matcher object = DCTM_OBJECT.matcher(path);
        if ("GET".equals(method) && object.find()) {
            ObjectDump dump = session.dump(object.group(1).toLowerCase());
            return json(200, dumpToJson(dump), path, start);
        }
        if ("POST".equals(method) && path.toLowerCase(Locale.ROOT).contains("dql")) {
            DocumentumSession dctm = requireDocumentum();
            String dql = extractDql(body);
            DqlResult result = dctm.executeDql(new DqlRequest(dql, QueryMode.READ, 100));
            ObjectNode node = JSON.createObjectNode();
            node.put("rowCount", result.rowCount());
            node.put("elapsedMs", result.elapsedMs());
            node.set("columns", JSON.valueToTree(result.columns()));
            node.set("rows", JSON.valueToTree(result.rows()));
            return json(200, node, path, start);
        }
        if ("GET".equals(method) && (path.endsWith("/") || path.contains("/repositories"))) {
            ObjectNode home = JSON.createObjectNode();
            home.put("name", "mock-dctm-rest");
            home.put("repository", session.serverInfo().repository());
            home.put("product-version", session.serverInfo().version());
            home.put("hint", "Try GET .../objects/0900000180000001 or POST .../dql with {\"dql\":\"SELECT ...\"}");
            return json(200, home, path, start);
        }
        return response(404, "Mock REST: unsupported Documentum path. Try /repositories/mock/objects/{id} or /dql",
                path, start, Map.of());
    }

    private RestProxyResponse handleOtcs(String method, String path, long start) {
        Matcher node = OTCS_NODE.matcher(path);
        if ("GET".equals(method) && node.find()) {
            ObjectDump dump = session.dump(node.group(1));
            ObjectNode root = JSON.createObjectNode();
            ObjectNode data = JSON.createObjectNode();
            ObjectNode props = JSON.createObjectNode();
            props.put("id", dump.id());
            props.put("name", dump.objectName());
            props.put("type_name", dump.typeName());
            data.set("properties", props);
            root.set("data", data);
            return json(200, root, path, start);
        }
        if ("GET".equals(method) && path.contains("/volumes/")) {
            return json(200, JSON.createObjectNode().put("name", "Enterprise Workspace"), path, start);
        }
        if ("GET".equals(method)) {
            ObjectNode home = JSON.createObjectNode();
            home.put("name", "mock-otcs-rest");
            home.put("cgiRoot", session.serverInfo().repository());
            home.put("hint", "Try GET .../api/v2/nodes/5100");
            return json(200, home, path, start);
        }
        return response(404, "Mock REST: unsupported OTCS path. Try /api/v2/nodes/{nodeId}", path, start, Map.of());
    }

    private DocumentumSession requireDocumentum() {
        if (session instanceof DocumentumSession dctm) {
            return dctm;
        }
        throw new SessionException("DQL REST simulation requires a Documentum session");
    }

    private static String extractDql(String body) {
        if (body == null || body.isBlank()) {
            throw new SessionException("DQL body required, e.g. {\"dql\":\"SELECT ...\"}");
        }
        try {
            var node = JSON.readTree(body);
            String dql = node.path("dql").asText();
            if (dql.isBlank()) {
                throw new SessionException("Missing dql field in body");
            }
            return dql;
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            return body.trim();
        }
    }

    private static ObjectNode dumpToJson(ObjectDump dump) {
        ObjectNode root = JSON.createObjectNode();
        root.put("id", dump.id());
        root.put("objectName", dump.objectName());
        root.put("typeName", dump.typeName());
        ObjectNode props = JSON.createObjectNode();
        for (AttributeValue attr : dump.attributes()) {
            if (attr.values() != null && !attr.values().isEmpty()) {
                props.put(attr.name(), String.join(", ", attr.values()));
            }
        }
        root.set("properties", props);
        return root;
    }

    private static RestProxyResponse json(int status, Object body, String url, long start) {
        try {
            return response(status, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(body), url, start,
                    Map.of("Content-Type", "application/json", "X-Mock-Rest", "session-proxy"));
        } catch (Exception e) {
            return response(500, "JSON error: " + e.getMessage(), url, start, Map.of());
        }
    }

    private static RestProxyResponse response(int status, String body, String url, long start, Map<String, String> headers) {
        Map<String, String> all = new LinkedHashMap<>(headers);
        all.putIfAbsent("Content-Type", "text/plain");
        return new RestProxyResponse(status, all, body, System.currentTimeMillis() - start, url);
    }
}
