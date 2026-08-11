package com.dctm.workbench.otds;

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

/**
 * OTDS helpers: password grant / stored bearer. Browser SSO is not implemented.
 */
public class OtdsClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public OtdsToken passwordGrant(String otdsBase, String clientId, String clientSecret, String username, char[] password) {
        try {
            String body = "grant_type=password"
                    + "&username=" + enc(username)
                    + "&password=" + enc(password == null ? "" : new String(password))
                    + "&client_id=" + enc(clientId == null ? "" : clientId)
                    + (clientSecret == null || clientSecret.isBlank() ? "" : "&client_secret=" + enc(clientSecret));
            String url = trimSlash(otdsBase) + "/otdsws/oauth2/token";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new SessionException("OTDS token request failed: HTTP " + response.statusCode());
            }
            JsonNode json = mapper.readTree(response.body());
            String access = text(json, "access_token");
            String type = text(json, "token_type");
            long expires = json.path("expires_in").asLong(3600);
            return new OtdsToken(access, type, null, expires);
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("OTDS token request failed: " + e.getMessage(), e);
        }
    }

    public OtdsToken fromStoredBearer(String token) {
        return new OtdsToken(token, "Bearer", null, 3600);
    }

    public OtdsToken credentialsTicket(String otdsBase, String username, char[] password) {
        try {
            String body = "username=" + enc(username) + "&password=" + enc(password == null ? "" : new String(password));
            String url = trimSlash(otdsBase) + "/otdsws/v1/authentication/credentials";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new SessionException("OTDS ticket request failed: HTTP " + response.statusCode());
            }
            JsonNode json = mapper.readTree(response.body());
            String ticket = text(json, "ticket");
            if (ticket.isBlank()) {
                ticket = text(json, "otdsticket");
            }
            return new OtdsToken(null, "OTDSTicket", ticket, 3600);
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("OTDS ticket request failed: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode json, String field) {
        JsonNode n = json.get(field);
        return n == null || n.isNull() ? "" : n.asText();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
