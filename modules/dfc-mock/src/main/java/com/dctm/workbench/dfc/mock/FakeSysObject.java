package com.dctm.workbench.dfc.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FakeSysObject {

    private String id;
    private String type;
    private String parentId;
    private final Map<String, List<String>> attrs = new LinkedHashMap<>();
    private byte[] content = new byte[0];

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        put("r_object_id", id);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
        put("r_object_type", type);
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public Map<String, List<String>> getAttrs() {
        return attrs;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content == null ? new byte[0] : content;
    }

    public String objectName() {
        return first("object_name");
    }

    public String first(String name) {
        List<String> values = attrs.get(name);
        if (values == null || values.isEmpty()) {
            if ("r_object_id".equals(name)) {
                return id;
            }
            if ("r_object_type".equals(name)) {
                return type;
            }
            return "";
        }
        return values.get(0) == null ? "" : values.get(0);
    }

    public List<String> values(String name) {
        List<String> values = attrs.get(name);
        if (values == null) {
            String first = first(name);
            return first.isEmpty() ? List.of() : List.of(first);
        }
        return values;
    }

    public void put(String name, String value) {
        List<String> list = new ArrayList<>();
        list.add(value == null ? "" : value);
        attrs.put(name, list);
    }

    public void putAll(String name, List<String> values) {
        attrs.put(name, values == null ? new ArrayList<>() : new ArrayList<>(values));
    }

    public FakeSysObject copy() {
        FakeSysObject copy = new FakeSysObject();
        copy.id = id;
        copy.type = type;
        copy.parentId = parentId;
        copy.content = content == null ? new byte[0] : content.clone();
        attrs.forEach((k, v) -> copy.attrs.put(k, new ArrayList<>(v)));
        return copy;
    }
}
