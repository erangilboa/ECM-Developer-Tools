package com.dctm.workbench.core;

import java.util.Locale;

public final class ContentTypes {

    private ContentTypes() {
    }

    public static String guess(String fileName, String format) {
        String fmt = format == null ? "" : format.toLowerCase(Locale.ROOT);
        return switch (fmt) {
            case "crtext", "text", "txt" -> "text/plain";
            case "html", "htm" -> "text/html";
            case "xml" -> "application/xml";
            case "json" -> "application/json";
            case "pdf" -> "application/pdf";
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "tiff", "tif" -> "image/tiff";
            default -> fromName(fileName);
        };
    }

    public static String fromName(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".csv")) {
            return "text/plain";
        }
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return "text/html";
        }
        if (name.endsWith(".xml")) {
            return "application/xml";
        }
        if (name.endsWith(".json")) {
            return "application/json";
        }
        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        return "application/octet-stream";
    }

    public static boolean likelyHasContent(ObjectDump dump) {
        if (dump == null) {
            return false;
        }
        String type = dump.typeName() == null ? "" : dump.typeName().toLowerCase(Locale.ROOT);
        if (type.contains("document") || "144".equals(dump.extra() == null ? "" : dump.extra().getOrDefault("subtype", ""))) {
            return true;
        }
        String pages = dump.attr("r_page_cnt");
        if (pages != null && !pages.isBlank() && !"0".equals(pages)) {
            return true;
        }
        String format = dump.attr("a_content_type");
        return format != null && !format.isBlank();
    }
}
