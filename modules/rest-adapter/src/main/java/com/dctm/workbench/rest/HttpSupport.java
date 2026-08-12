package com.dctm.workbench.rest;

import com.dctm.workbench.core.SessionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class HttpSupport {

    final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    final ObjectMapper mapper = new ObjectMapper();

    String basic(String user, char[] password) {
        String token = user + ":" + (password == null ? "" : new String(password));
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    JsonNode getJson(String url, Map<String, String> headers) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url)).GET(), headers);
        return parse(response.body());
    }

    HttpResponse<String> send(HttpRequest.Builder builder, Map<String, String> headers) {
        try {
            headers.forEach(builder::header);
            builder.timeout(Duration.ofSeconds(60));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new SessionException("HTTP " + response.statusCode() + " " + truncate(response.body()));
            }
            return response;
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("HTTP call failed: " + e.getMessage(), e);
        }
    }

    HttpResponse<String> sendAny(HttpRequest.Builder builder, Map<String, String> headers) {
        try {
            headers.forEach(builder::header);
            builder.timeout(Duration.ofSeconds(60));
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new SessionException("HTTP call failed: " + e.getMessage(), e);
        }
    }

    JsonNode parse(String body) {
        try {
            if (body == null || body.isBlank()) {
                return mapper.createObjectNode();
            }
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new SessionException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    Optional<String> header(HttpResponse<?> response, String name) {
        List<String> values = response.headers().allValues(name);
        if (values.isEmpty()) {
            // HTTP headers are case-insensitive but the API is exact in some JDKs
            return response.headers().map().entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst();
        }
        return Optional.of(values.get(0));
    }

    String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 400 ? body.substring(0, 400) : body;
    }
}
