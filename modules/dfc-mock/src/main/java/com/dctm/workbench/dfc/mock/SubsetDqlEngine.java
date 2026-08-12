package com.dctm.workbench.dfc.mock;

import com.dctm.workbench.core.DqlResult;
import com.dctm.workbench.core.QueryMode;
import com.dctm.workbench.core.SessionException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubsetDqlEngine {

    private static final Pattern ENABLE_TOP = Pattern.compile(
            "(?i)\\s*ENABLE\\s*\\(\\s*RETURN_TOP\\s+(\\d+)\\s*\\)\\s*$");
    private static final Pattern SELECT = Pattern.compile(
            "(?i)^SELECT\\s+(.+?)\\s+FROM\\s+([a-zA-Z0-9_]+)(?:\\s+WHERE\\s+(.+))?$");
    private static final Pattern UPDATE = Pattern.compile(
            "(?i)^UPDATE\\s+([a-zA-Z0-9_]+)\\s+OBJECTS\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+))?$");

    private final FakeDocbase docbase;

    public SubsetDqlEngine(FakeDocbase docbase) {
        this.docbase = docbase;
    }

    public DqlResult execute(String dql, QueryMode mode) {
        long start = System.currentTimeMillis();
        if (dql == null || dql.isBlank()) {
            throw new SessionException("Empty DQL");
        }
        String sql = dql.trim();
        Integer top = null;
        Matcher enable = ENABLE_TOP.matcher(sql);
        if (enable.find()) {
            top = Integer.parseInt(enable.group(1));
            sql = sql.substring(0, enable.start()).trim();
        }
        sql = sql.replaceAll("\\s+", " ").trim();
        Matcher update = UPDATE.matcher(sql);
        if (update.matches()) {
            if (mode == QueryMode.READ) {
                throw new SessionException("UPDATE requires EXEC query mode / DQL_EXECUTE");
            }
            int count = update(update.group(1), update.group(2), update.group(3));
            return new DqlResult(List.of("objects_updated"), List.of(List.of(String.valueOf(count))), count, dql,
                    System.currentTimeMillis() - start);
        }
        Matcher select = SELECT.matcher(sql);
        if (!select.matches()) {
            throw new SessionException("Mock DQL does not understand: " + dql
                    + " (supported: SELECT ... FROM type [WHERE ...] [ENABLE(RETURN_TOP n)]; "
                    + "UPDATE type OBJECTS SET attr = value [WHERE ...])");
        }
        String cols = select.group(1).trim();
        String type = select.group(2).trim();
        String where = select.group(3);
        List<FakeSysObject> rows = new ArrayList<>();
        for (FakeSysObject obj : docbase.all()) {
            if (!docbase.isSubtype(obj.getType(), type)) {
                continue;
            }
            if (where != null && !where.isBlank() && !matchesWhere(obj, where)) {
                continue;
            }
            rows.add(obj);
        }
        if (top != null && rows.size() > top) {
            rows = new ArrayList<>(rows.subList(0, top));
        }
        List<String> columns = columns(cols, rows, type);
        List<List<String>> data = new ArrayList<>();
        for (FakeSysObject obj : rows) {
            List<String> row = new ArrayList<>();
            for (String col : columns) {
                row.add(join(obj.values(col)));
            }
            data.add(row);
        }
        return new DqlResult(columns, data, data.size(), dql, System.currentTimeMillis() - start);
    }

    private int update(String type, String setClause, String where) {
        int count = 0;
        for (FakeSysObject obj : docbase.all()) {
            if (!docbase.isSubtype(obj.getType(), type)) {
                continue;
            }
            if (where != null && !where.isBlank() && !matchesWhere(obj, where)) {
                continue;
            }
            for (String assignment : splitComma(setClause)) {
                String[] parts = assignment.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }
                String attr = parts[0].trim();
                String value = unquote(parts[1].trim());
                obj.put(attr, value);
            }
            count++;
        }
        return count;
    }

    boolean matchesWhere(FakeSysObject obj, String where) {
        List<String> ands = splitLogical(where, "AND");
        for (String and : ands) {
            List<String> ors = splitLogical(and, "OR");
            boolean any = false;
            for (String term : ors) {
                if (matchesTerm(obj, term.trim())) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesTerm(FakeSysObject obj, String term) {
        Matcher folder = Pattern.compile("(?i)^folder\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)$").matcher(term);
        if (folder.matches()) {
            return folder.group(1).equals(obj.getParentId());
        }
        Matcher like = Pattern.compile("(?i)^([a-zA-Z0-9_]+)\\s+LIKE\\s+(.+)$").matcher(term);
        if (like.matches()) {
            String attr = like.group(1);
            String pattern = unquote(like.group(2).trim()).toLowerCase(Locale.ROOT);
            String regex = pattern.replace("%", ".*").replace("_", ".");
            return obj.first(attr).toLowerCase(Locale.ROOT).matches(regex);
        }
        Matcher ne = Pattern.compile("(?i)^([a-zA-Z0-9_]+)\\s*(<>|!=)\\s*(.+)$").matcher(term);
        if (ne.matches()) {
            return !scalarEquals(obj.first(ne.group(1)), unquote(ne.group(3).trim()));
        }
        Matcher eq = Pattern.compile("(?i)^([a-zA-Z0-9_]+)\\s*=\\s*(.+)$").matcher(term);
        if (eq.matches()) {
            return scalarEquals(obj.first(eq.group(1)), unquote(eq.group(2).trim()));
        }
        throw new SessionException("Mock DQL WHERE term not supported: " + term);
    }

    private List<String> columns(String cols, List<FakeSysObject> rows, String type) {
        if ("*".equals(cols.trim())) {
            if (rows.isEmpty()) {
                return docbase.inheritedAttributes(type);
            }
            return docbase.inheritedAttributes(rows.get(0).getType());
        }
        return Arrays.stream(cols.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(",", values);
    }

    private static boolean scalarEquals(String actual, String expected) {
        String value = actual == null ? "" : actual;
        if ("true".equalsIgnoreCase(expected) || "T".equalsIgnoreCase(expected)) {
            return "T".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
        }
        if ("false".equalsIgnoreCase(expected) || "F".equalsIgnoreCase(expected)) {
            return "F".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || value.isBlank();
        }
        return value.equalsIgnoreCase(expected);
    }

    private static String unquote(String value) {
        String v = value.trim();
        if ((v.startsWith("'") && v.endsWith("'")) || (v.startsWith("\"") && v.endsWith("\""))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static List<String> splitComma(String text) {
        return splitLogical(text, ",");
    }

    private static List<String> splitLogical(String text, String op) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        String upper = text;
        int i = 0;
        while (i < upper.length()) {
            char c = upper.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                current.append(c);
                i++;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                current.append(c);
                i++;
                continue;
            }
            if (!inSingle && !inDouble) {
                if (op.equals(",") && c == ',') {
                    parts.add(current.toString().trim());
                    current.setLength(0);
                    i++;
                    continue;
                }
                if (!op.equals(",") && upper.regionMatches(true, i, " " + op + " ", 0, op.length() + 2)) {
                    parts.add(current.toString().trim());
                    current.setLength(0);
                    i += op.length() + 2;
                    continue;
                }
            }
            current.append(c);
            i++;
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts.stream().filter(s -> !s.isBlank()).toList();
    }
}
