package com.dctm.workbench.core;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects repository entity references from pasted text (ids, chronicles, URLs).
 */
public final class RepositoryEntityResolver {

    private static final Pattern DOCUMENTUM_OBJECT_ID = Pattern.compile("\\b([0-9a-fA-F]{16})\\b");
    private static final Pattern OTCS_NODE_ID = Pattern.compile("\\b(\\d{4,})\\b");
    private static final Pattern DCTM_REST_OBJECT = Pattern.compile(
            "/repositories/[^/]+/objects/([0-9a-fA-F]{16})", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTCS_NODE_PATH = Pattern.compile(
            "/(?:api/v\\d+|nodes)/(\\d+)(?:/|$)", Pattern.CASE_INSENSITIVE);

    private RepositoryEntityResolver() {
    }

    public static ResolveResult resolve(String input, Product product) {
        if (input == null || input.isBlank()) {
            return unknown(input);
        }
        String trimmed = input.trim();

        ResolveResult fromUrl = resolveUrl(trimmed, product);
        if (fromUrl != null) {
            return fromUrl;
        }

        if (product == Product.DOCUMENTUM) {
            Matcher m = DOCUMENTUM_OBJECT_ID.matcher(trimmed);
            if (m.find()) {
                String id = m.group(1).toLowerCase();
                return new ResolveResult(ResolveResult.KIND_OBJECT, id, "Object " + id, ResolveResult.ACTION_DUMP);
            }
        }

        if (product == Product.EXTENDED_ECM) {
            Matcher m = OTCS_NODE_ID.matcher(trimmed);
            if (m.matches()) {
                String id = m.group(1);
                return new ResolveResult(ResolveResult.KIND_NODE, id, "Node " + id, ResolveResult.ACTION_DUMP);
            }
        }

        return unknown(trimmed);
    }

    private static ResolveResult resolveUrl(String input, Product product) {
        String path = input;
        if (input.contains("://")) {
            try {
                path = URI.create(input).getPath();
            } catch (Exception ignored) {
                path = input;
            }
        }

        Matcher dctm = DCTM_REST_OBJECT.matcher(path);
        if (dctm.find() && (product == Product.DOCUMENTUM || product == null)) {
            String id = dctm.group(1).toLowerCase();
            return new ResolveResult(ResolveResult.KIND_OBJECT, id, "Object " + id, ResolveResult.ACTION_DUMP);
        }

        Matcher otcs = OTCS_NODE_PATH.matcher(path);
        if (otcs.find() && (product == Product.EXTENDED_ECM || product == null)) {
            String id = otcs.group(1);
            return new ResolveResult(ResolveResult.KIND_NODE, id, "Node " + id, ResolveResult.ACTION_DUMP);
        }

        if (product == Product.DOCUMENTUM) {
            Matcher m = DOCUMENTUM_OBJECT_ID.matcher(path);
            if (m.find()) {
                String id = m.group(1).toLowerCase();
                return new ResolveResult(ResolveResult.KIND_OBJECT, id, "Object " + id, ResolveResult.ACTION_DUMP);
            }
        }

        return null;
    }

    private static ResolveResult unknown(String input) {
        String label = input == null || input.isBlank() ? "Unknown" : input;
        return new ResolveResult(ResolveResult.KIND_UNKNOWN, "", label, "");
    }
}
