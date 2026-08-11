package com.dctm.workbench.otcs.mock;

import com.dctm.workbench.core.CategoryValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FakeNode {

    private long id;
    private String name;
    private int type;
    private long parentId;
    private boolean volume;
    private String extSystemId = "";
    private String boType = "";
    private String boId = "";
    private String templateId = "";
    private byte[] content = new byte[0];
    private final List<CategoryValue> categories = new ArrayList<>();
    private final Map<String, String> properties = new LinkedHashMap<>();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getParentId() {
        return parentId;
    }

    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    public boolean isVolume() {
        return volume;
    }

    public void setVolume(boolean volume) {
        this.volume = volume;
    }

    public String getExtSystemId() {
        return extSystemId;
    }

    public void setExtSystemId(String extSystemId) {
        this.extSystemId = extSystemId == null ? "" : extSystemId;
    }

    public String getBoType() {
        return boType;
    }

    public void setBoType(String boType) {
        this.boType = boType == null ? "" : boType;
    }

    public String getBoId() {
        return boId;
    }

    public void setBoId(String boId) {
        this.boId = boId == null ? "" : boId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId == null ? "" : templateId;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content == null ? new byte[0] : content;
    }

    public List<CategoryValue> getCategories() {
        return categories;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public boolean folder() {
        return type == 0 || type == 141 || type == 142 || type == 848;
    }

    public String typeName() {
        return switch (type) {
            case 0 -> "Folder";
            case 141 -> "Enterprise Workspace";
            case 142 -> "Personal Workspace";
            case 144 -> "Document";
            case 848 -> "Business Workspace";
            default -> "Node";
        };
    }
}
