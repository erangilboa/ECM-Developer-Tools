package com.dctm.workbench.dfc.mock;

import com.dctm.workbench.core.AttributeValue;
import com.dctm.workbench.core.IapiResult;
import com.dctm.workbench.core.ObjectDump;
import com.dctm.workbench.core.QueryMode;
import com.dctm.workbench.core.SessionException;

import java.util.Locale;

public class SubsetIapi {

    private final FakeDocbase docbase;
    private final SubsetDqlEngine dql;
    private String currentId;

    public SubsetIapi(FakeDocbase docbase, SubsetDqlEngine dql) {
        this.docbase = docbase;
        this.dql = dql;
    }

    public IapiResult execute(String command) {
        if (command == null || command.isBlank()) {
            return IapiResult.error("Empty API command", currentId);
        }
        String[] parts = command.split(",", 4);
        String verb = parts[0].trim().toLowerCase(Locale.ROOT);
        try {
            return switch (verb) {
                case "fetch" -> fetch(arg(parts, 2));
                case "dump" -> dump(arg(parts, 2));
                case "get" -> get(arg(parts, 2));
                case "set" -> set(parts);
                case "append" -> append(parts);
                case "save" -> save();
                case "execquery" -> execquery(rest(command, 2));
                case "create" -> create(arg(parts, 2));
                case "apply" -> apply(parts);
                case "disconnect" -> {
                    currentId = null;
                    yield IapiResult.ok("OK", null);
                }
                default -> IapiResult.error("Mock IAPI does not support: " + verb, currentId);
            };
        } catch (SessionException e) {
            return IapiResult.error(e.getMessage(), currentId);
        }
    }

    private IapiResult fetch(String id) {
        docbase.require(id);
        currentId = id;
        return IapiResult.ok("OK", currentId);
    }

    private IapiResult dump(String id) {
        String target = id == null || id.isBlank() ? currentId : id;
        ObjectDump dump = docbase.dump(target);
        StringBuilder sb = new StringBuilder();
        for (AttributeValue attr : dump.attributes()) {
            sb.append(attr.name()).append(": ").append(String.join(", ", attr.values())).append('\n');
        }
        currentId = target;
        return IapiResult.ok(sb.toString(), currentId);
    }

    private IapiResult get(String attr) {
        requireCurrent();
        return IapiResult.ok(docbase.require(currentId).first(attr), currentId);
    }

    private IapiResult set(String[] parts) {
        requireCurrent();
        FakeSysObject obj = docbase.require(currentId);
        if (parts.length < 3) {
            return IapiResult.error("set requires attribute", currentId);
        }
        String rest = parts.length >= 4 ? parts[3] : "";
        String attr = parts[2];
        obj.put(attr, rest);
        return IapiResult.ok("OK", currentId);
    }

    private IapiResult append(String[] parts) {
        requireCurrent();
        FakeSysObject obj = docbase.require(currentId);
        String attr = arg(parts, 2);
        String value = parts.length >= 4 ? parts[3] : "";
        java.util.List<String> values = new java.util.ArrayList<>(obj.values(attr));
        values.add(value);
        obj.putAll(attr, values);
        return IapiResult.ok("OK", currentId);
    }

    private IapiResult save() {
        requireCurrent();
        return IapiResult.ok("OK", currentId);
    }

    private IapiResult execquery(String dqlText) {
        var result = dql.execute(dqlText, QueryMode.READ);
        StringBuilder sb = new StringBuilder();
        sb.append(String.join("\t", result.columns())).append('\n');
        for (var row : result.rows()) {
            sb.append(String.join("\t", row)).append('\n');
        }
        return IapiResult.ok(sb.toString(), currentId);
    }

    private IapiResult create(String type) {
        FakeSysObject obj = docbase.create(type);
        currentId = obj.getId();
        return IapiResult.ok(currentId, currentId);
    }

    private IapiResult apply(String[] parts) {
        String id = arg(parts, 2);
        String method = parts.length >= 4 ? parts[3] : "";
        if ("RUN_NOW".equalsIgnoreCase(method)) {
            docbase.require(id);
            docbase.completeRunNow(id);
            currentId = id;
            return IapiResult.ok("OK", currentId);
        }
        return IapiResult.error("Mock apply supports RUN_NOW only", currentId);
    }

    private void requireCurrent() {
        if (currentId == null) {
            throw new SessionException("No current object — fetch first");
        }
    }

    private static String arg(String[] parts, int index) {
        return parts.length > index ? parts[index].trim() : "";
    }

    private static String rest(String command, int commaIndex) {
        int seen = 0;
        for (int i = 0; i < command.length(); i++) {
            if (command.charAt(i) == ',') {
                seen++;
                if (seen == commaIndex) {
                    return command.substring(i + 1).trim();
                }
            }
        }
        return "";
    }
}
