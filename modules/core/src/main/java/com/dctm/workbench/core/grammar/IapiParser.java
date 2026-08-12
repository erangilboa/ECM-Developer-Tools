package com.dctm.workbench.core.grammar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Documentum IAPI command checker: {@code method,session[,args...]}.
 * Quotes in later fields (execquery DQL) are respected when splitting.
 */
final class IapiParser {

    record MethodSpec(int minArgs, int maxArgs, boolean sessionFirst, String hint) {
    }

    private static final Map<String, MethodSpec> METHODS = Map.ofEntries(
            entry("abort", 1, 2, true, "abort,c[,id]"),
            entry("append", 2, 4, true, "append,c,attribute,value"),
            entry("apply", 2, 16, true, "apply,c,id,FUNCTION[,args]"),
            entry("archive", 1, 2, true, "archive,c[,id]"),
            entry("assemble", 1, 4, true, "assemble,c[,id]"),
            entry("bindfile", 2, 4, true, "bindfile,c,page,path"),
            entry("cachequery", 2, 3, true, "cachequery,c,dql"),
            entry("cancelcheckout", 1, 2, true, "cancelcheckout,c[,id]"),
            entry("checkin", 1, 4, true, "checkin,c[,keepLock,version]"),
            entry("checkout", 1, 2, true, "checkout,c[,id]"),
            entry("close", 1, 2, true, "close,c[,collection]"),
            entry("commit", 1, 1, true, "commit,c"),
            entry("connect", 2, 5, false, "connect,docbase,user[,password[,domain]]"),
            entry("count", 1, 2, true, "count,c[,collection]"),
            entry("create", 2, 3, true, "create,c,type"),
            entry("decryptfile", 2, 3, true, "decryptfile,c,path"),
            entry("demote", 1, 2, true, "demote,c[,id]"),
            entry("destroy", 1, 2, true, "destroy,c[,id]"),
            entry("disconnect", 1, 1, true, "disconnect,c"),
            entry("dump", 1, 2, true, "dump,c[,id]"),
            entry("encryptfile", 2, 3, true, "encryptfile,c,path"),
            entry("execquery", 2, 3, true, "execquery,c,dql"),
            entry("execreadquery", 2, 3, true, "execreadquery,c,dql"),
            entry("fetch", 2, 3, true, "fetch,c,id"),
            entry("get", 2, 4, true, "get,c,{id|l},attribute"),
            entry("getbase", 1, 2, true, "getbase,c"),
            entry("getconnection", 1, 1, true, "getconnection,c"),
            entry("getcontent", 1, 3, true, "getcontent,c[,page]"),
            entry("getdocbasemap", 1, 2, true, "getdocbasemap,c"),
            entry("getevent", 1, 2, true, "getevent,c"),
            entry("getfile", 2, 4, true, "getfile,c,path[,page]"),
            entry("getlastcoll", 1, 1, true, "getlastcoll,c"),
            entry("getmessage", 1, 1, true, "getmessage,c"),
            entry("getpath", 1, 3, true, "getpath,c[,page]"),
            entry("getservermap", 1, 2, true, "getservermap,c"),
            entry("id", 2, 3, true, "id,c,tag"),
            entry("insert", 3, 5, true, "insert,c,attribute,index,value"),
            entry("link", 2, 3, true, "link,c,folderId"),
            entry("listappend", 2, 4, true, "listappend,c,attribute,value"),
            entry("listinsert", 3, 5, true, "listinsert,c,attribute,index,value"),
            entry("listremove", 2, 4, true, "listremove,c,attribute,index"),
            entry("locate", 2, 4, true, "locate,c,attribute,value"),
            entry("lock", 1, 2, true, "lock,c[,id]"),
            entry("next", 1, 2, true, "next,c[,collection]"),
            entry("print", 1, 2, true, "print,c[,id]"),
            entry("promote", 1, 2, true, "promote,c[,id]"),
            entry("query", 2, 3, true, "query,c,dql"),
            entry("readquery", 2, 3, true, "readquery,c,dql"),
            entry("remove", 2, 4, true, "remove,c,attribute[,index]"),
            entry("repeat", 2, 3, true, "repeat,c,count"),
            entry("retrieve", 2, 3, true, "retrieve,c,id"),
            entry("save", 1, 2, true, "save,c"),
            entry("saveasnew", 1, 2, true, "saveasnew,c"),
            entry("set", 2, 5, true, "set,c,{id|l},attribute[,value]"),
            entry("setbatchhint", 2, 3, true, "setbatchhint,c,hint"),
            entry("setcontent", 2, 4, true, "setcontent,c,page,value"),
            entry("setfile", 2, 4, true, "setfile,c,path[,format]"),
            entry("setoutput", 2, 3, true, "setoutput,c,path"),
            entry("setpath", 2, 4, true, "setpath,c,path"),
            entry("trace", 2, 3, true, "trace,c,level"),
            entry("truncate", 2, 3, true, "truncate,c,attribute"),
            entry("unlink", 2, 3, true, "unlink,c,folderId"),
            entry("unlock", 1, 2, true, "unlock,c[,id]"),
            entry("useacl", 2, 3, true, "useacl,c,aclId")
    );

    private static final Set<String> SESSION_SLOTS = Set.of("c", "current", "l", "last");

    private static Map.Entry<String, MethodSpec> entry(String name, int min, int max, boolean session, String hint) {
        return Map.entry(name, new MethodSpec(min, max, session, hint));
    }

    static List<GrammarIssue> parse(String source) {
        String text = source == null ? "" : source;
        List<GrammarIssue> issues = new ArrayList<>();
        if (text.isBlank()) {
            return issues;
        }
        if (unterminatedQuote(text)) {
            issues.add(GrammarIssue.error(0, text.length(), text, "Unterminated quoted argument"));
            return issues;
        }
        List<Field> fields = split(text);
        if (fields.isEmpty()) {
            issues.add(GrammarIssue.error(0, 1, text, "Empty IAPI command"));
            return issues;
        }
        Field methodField = fields.get(0);
        String method = methodField.value.trim().toLowerCase(Locale.ROOT);
        if (method.isEmpty()) {
            issues.add(GrammarIssue.error(methodField.offset, Math.max(1, methodField.length), text,
                    "Missing IAPI method name"));
            return issues;
        }
        MethodSpec spec = METHODS.get(method);
        if (spec == null) {
            if (isPrefixOnly(method)) {
                return issues;
            }
            issues.add(GrammarIssue.warning(methodField.offset, methodField.length, text,
                    "Unknown IAPI method '" + methodField.value.trim() + "' — DFC may still accept it"));
            checkSessionIfPresent(fields, text, issues);
            return issues;
        }
        int argCount = fields.size() - 1;
        if (argCount < spec.minArgs()) {
            issues.add(GrammarIssue.error(methodField.offset, Math.max(1, text.length() - methodField.offset),
                    text, "Too few arguments. Usage: " + spec.hint()));
        } else if (argCount > spec.maxArgs()) {
            Field extra = fields.get(spec.maxArgs() + 1);
            issues.add(GrammarIssue.warning(extra.offset, extra.length, text,
                    "Extra arguments for " + method + ". Usage: " + spec.hint()));
        }
        if (spec.sessionFirst() && fields.size() >= 2) {
            Field session = fields.get(1);
            String slot = session.value.trim().toLowerCase(Locale.ROOT);
            if (!slot.isEmpty() && !SESSION_SLOTS.contains(slot) && !looksLikeObjectId(slot)
                    && slot.length() > 1) {
                issues.add(GrammarIssue.warning(session.offset, session.length, text,
                        "Session slot is usually 'c' (current). Found '" + session.value.trim() + "'"));
            }
        }
        if (("execquery".equals(method) || "query".equals(method) || "readquery".equals(method)
                || "execreadquery".equals(method) || "cachequery".equals(method))
                && fields.size() >= 3) {
            Field dql = fields.get(2);
            for (GrammarIssue issue : DqlParser.parse(dql.value)) {
                int offset = dql.offset + issue.offset();
                issues.add(issue.severity() == GrammarIssue.Severity.WARNING
                        ? GrammarIssue.warning(offset, issue.length(), text, "DQL: " + issue.message())
                        : GrammarIssue.error(offset, issue.length(), text, "DQL: " + issue.message()));
            }
        }
        if (("fetch".equals(method) || "retrieve".equals(method) || "dump".equals(method)
                || "destroy".equals(method) || "apply".equals(method))
                && fields.size() >= 3) {
            Field id = fields.get(2);
            String v = id.value.trim();
            if (!v.isEmpty() && !looksLikeObjectId(v) && !"l".equalsIgnoreCase(v)
                    && !"current".equalsIgnoreCase(v)) {
                issues.add(GrammarIssue.warning(id.offset, id.length, text,
                        "Object id should be a 16-hex Documentum id"));
            }
        }
        return issues;
    }

    private static void checkSessionIfPresent(List<Field> fields, String text, List<GrammarIssue> issues) {
        if (fields.size() >= 2) {
            Field session = fields.get(1);
            String slot = session.value.trim().toLowerCase(Locale.ROOT);
            if (!slot.isEmpty() && !SESSION_SLOTS.contains(slot) && !looksLikeObjectId(slot)) {
                issues.add(GrammarIssue.warning(session.offset, session.length, text,
                        "Second field is usually the session ('c')"));
            }
        }
    }

    private static boolean isPrefixOnly(String method) {
        for (String name : METHODS.keySet()) {
            if (name.startsWith(method) && method.length() < name.length()) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeObjectId(String value) {
        if (value.length() != 16) {
            return false;
        }
        for (int i = 0; i < 16; i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static boolean unterminatedQuote(String text) {
        boolean in = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                if (in && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                in = !in;
            }
        }
        return in;
    }

    private record Field(String value, int offset, int length) {
    }

    private static List<Field> split(String text) {
        List<Field> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int start = 0;
        boolean in = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                cur.append(c);
                if (in && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    cur.append('\'');
                    i++;
                } else {
                    in = !in;
                }
                continue;
            }
            if (c == ',' && !in) {
                fields.add(new Field(cur.toString(), start, i - start));
                cur.setLength(0);
                start = i + 1;
                continue;
            }
            cur.append(c);
        }
        fields.add(new Field(cur.toString(), start, text.length() - start));
        return fields;
    }
}
