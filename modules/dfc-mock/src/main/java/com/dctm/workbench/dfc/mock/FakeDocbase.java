package com.dctm.workbench.dfc.mock;

import com.dctm.workbench.core.AttributeValue;
import com.dctm.workbench.core.ClasspathFixtures;
import com.dctm.workbench.core.ObjectDump;
import com.dctm.workbench.core.SessionException;
import com.dctm.workbench.core.TypeDictionary;
import com.dctm.workbench.core.TypeInfo;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeDocbase {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, TypeInfo> types = new LinkedHashMap<>();
    private final Map<String, FakeSysObject> objects = new LinkedHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(100);
    private String version = "24.2";
    private String repository = "mock";

    public static FakeDocbase fromClasspath() {
        FakeDocbase db = new FakeDocbase();
        db.loadFromClasspath();
        return db;
    }

    public void loadFromClasspath() {
        try {
            byte[] json = ClasspathFixtures.read(FakeDocbase.class, "/fixtures/docbase.json",
                    "dfc-mock/src/main/resources/fixtures/docbase.json",
                    "../dfc-mock/src/main/resources/fixtures/docbase.json",
                    "src/main/resources/fixtures/docbase.json");
            load(mapper.readTree(json));
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("Failed to load mock docbase: " + e.getMessage(), e);
        }
    }

    public synchronized void reset() {
        types.clear();
        objects.clear();
        seq.set(100);
        loadFromClasspath();
    }

    public String version() {
        return version;
    }

    public String repository() {
        return repository;
    }

    public TypeDictionary typeDictionary() {
        List<TypeInfo> list = new ArrayList<>();
        for (TypeInfo t : types.values()) {
            list.add(new TypeInfo(t.name(), t.superName(), inheritedAttributes(t.name())));
        }
        return new TypeDictionary(list);
    }

    public List<String> inheritedAttributes(String typeName) {
        List<String> attrs = new ArrayList<>();
        TypeInfo current = types.get(typeName);
        while (current != null) {
            for (int i = current.attributes().size() - 1; i >= 0; i--) {
                String attr = current.attributes().get(i);
                if (!attrs.contains(attr)) {
                    attrs.add(0, attr);
                }
            }
            current = current.superName() == null ? null : types.get(current.superName());
        }
        return attrs;
    }

    public boolean isSubtype(String typeName, String ancestor) {
        String current = typeName;
        while (current != null) {
            if (current.equalsIgnoreCase(ancestor)) {
                return true;
            }
            TypeInfo info = types.get(current);
            current = info == null ? null : info.superName();
        }
        return false;
    }

    public Optional<FakeSysObject> find(String id) {
        return Optional.ofNullable(objects.get(id));
    }

    public FakeSysObject require(String id) {
        FakeSysObject obj = objects.get(id);
        if (obj == null) {
            throw new SessionException("[DM_API_E_NOT_EXIST] no object with id " + id);
        }
        return obj;
    }

    public List<FakeSysObject> all() {
        return new ArrayList<>(objects.values());
    }

    public ObjectDump dump(String id) {
        FakeSysObject obj = require(id);
        List<AttributeValue> attrs = new ArrayList<>();
        List<String> names = inheritedAttributes(obj.getType());
        if (names.isEmpty()) {
            names = new ArrayList<>(obj.getAttrs().keySet());
            if (!names.contains("r_object_id")) {
                names.add(0, "r_object_id");
            }
        }
        for (String name : names) {
            List<String> values = obj.values(name);
            if (values.isEmpty() && ("r_object_id".equals(name) || "r_object_type".equals(name) || "object_name".equals(name))) {
                values = List.of(obj.first(name));
            }
            boolean repeating = values.size() > 1 || "r_version_label".equals(name);
            boolean readOnly = name.startsWith("r_") && !"r_version_label".equals(name);
            attrs.add(new AttributeValue(name, "string", repeating, values, readOnly));
        }
        return new ObjectDump(obj.getId(), obj.getType(), obj.objectName(), attrs, List.of(), Map.of("parentId",
                obj.getParentId() == null ? "" : obj.getParentId()), false);
    }

    public void saveDump(ObjectDump dump) {
        FakeSysObject obj = require(dump.id());
        if (dump.attributes() != null) {
            for (AttributeValue attr : dump.attributes()) {
                if (attr.readOnly()) {
                    continue;
                }
                obj.putAll(attr.name(), attr.values());
            }
        }
        obj.put("r_modify_date", now());
    }

    public void checkout(String id) {
        FakeSysObject obj = require(id);
        obj.put("r_lock_owner", "dmadmin");
    }

    public void checkin(String id) {
        FakeSysObject obj = require(id);
        obj.put("r_lock_owner", "");
        obj.put("r_modify_date", now());
    }

    public FakeSysObject create(String type) {
        FakeSysObject obj = new FakeSysObject();
        obj.setId(nextId(type));
        obj.setType(type);
        obj.put("object_name", "New " + type);
        obj.put("owner_name", "dmadmin");
        obj.put("r_creation_date", now());
        obj.put("r_modify_date", now());
        objects.put(obj.getId(), obj);
        return obj;
    }

    public byte[] content(String id) {
        return require(id).getContent();
    }

    public synchronized String completeRunNow(String jobId) {
        FakeSysObject job = require(jobId);
        String jobName = job.objectName();
        String stamp = now();
        job.put("run_now", "F");
        job.put("a_last_completion", stamp);
        job.put("a_last_return", "0");
        job.put("a_current_status", "Completed successfully (mock)");
        job.put("r_modify_date", stamp);
        String reportsFolderId = null;
        for (FakeSysObject obj : objects.values()) {
            if ("/System/Sysadmin/Reports".equals(obj.first("r_folder_path"))) {
                reportsFolderId = obj.getId();
                break;
            }
        }
        if (reportsFolderId == null) {
            return null;
        }
        FakeSysObject report = create("dm_document");
        report.setParentId(reportsFolderId);
        String name = jobName + "_" + stamp.replace(":", "-").replace("T", "_") + ".log";
        report.put("object_name", name);
        report.put("title", jobName);
        report.put("subject", jobName);
        report.put("a_content_type", "crtext");
        report.put("r_page_cnt", "1");
        report.put("owner_name", "dmadmin");
        String body = "Job Report: " + jobName + "\nStarted: " + stamp + "\nFinished: " + stamp
                + "\nReturn: 0\n\nMock agent completed RUN_NOW.\n";
        report.setContent(body.getBytes(StandardCharsets.UTF_8));
        return report.getId();
    }

    public synchronized String nextId(String type) {
        int n = seq.incrementAndGet();
        String prefix = switch (type) {
            case "dm_document" -> "09";
            case "dm_folder" -> "0b";
            case "dm_cabinet" -> "0c";
            case "dm_job" -> "08";
            case "dm_user" -> "11";
            case "dm_acl" -> "45";
            default -> "09";
        };
        return prefix + String.format("%014x", n);
    }

    private void load(JsonNode root) {
        version = root.path("version").asText("24.2");
        repository = root.path("repository").asText("mock");
        for (JsonNode t : root.path("types")) {
            List<String> attrs = new ArrayList<>();
            t.path("attributes").forEach(a -> attrs.add(a.asText()));
            String superName = t.path("superName").isNull() || t.path("superName").asText().isBlank()
                    ? null : t.path("superName").asText();
            TypeInfo info = new TypeInfo(t.path("name").asText(), superName, attrs);
            types.put(info.name(), info);
        }
        for (JsonNode o : root.path("objects")) {
            FakeSysObject obj = new FakeSysObject();
            obj.setId(o.path("id").asText());
            obj.setType(o.path("type").asText());
            JsonNode parent = o.get("parentId");
            obj.setParentId(parent == null || parent.isNull() || parent.asText().isBlank() ? null : parent.asText());
            JsonNode attrs = o.path("attrs");
            attrs.fields().forEachRemaining(e -> {
                List<String> values = new ArrayList<>();
                if (e.getValue().isArray()) {
                    e.getValue().forEach(v -> values.add(v.asText()));
                } else {
                    values.add(e.getValue().asText());
                }
                obj.putAll(e.getKey(), values);
            });
            if (o.has("content")) {
                obj.setContent(o.path("content").asText("").getBytes(StandardCharsets.UTF_8));
            }
            objects.put(obj.getId(), obj);
        }
    }

    private static String now() {
        return LocalDateTime.now().format(TS);
    }
}
