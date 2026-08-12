package com.dctm.workbench.server.store;

import com.dctm.workbench.core.AuthMode;
import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.Json;
import com.dctm.workbench.core.ExecutionHistoryEntry;
import com.dctm.workbench.core.Product;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.QueryHistoryEntry;
import com.dctm.workbench.core.SavedQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProfileStore {

    private final Path home;
    private final Path profilesFile;
    private final Path queriesFile;
    private final Path queryHistoryFile;
    private final Path executionHistoryFile;
    private final SecretKey key;
    private static final int MAX_QUERY_HISTORY = 100;
    private static final int MAX_EXECUTION_HISTORY = 200;
    private final Map<String, String> secrets = new ConcurrentHashMap<>();

    public ProfileStore(@Value("${workbench.home}") String homeDir) {
        this.home = migrateLegacyHome(Path.of(homeDir));
        this.profilesFile = home.resolve("profiles.json");
        this.queriesFile = home.resolve("queries.json");
        this.queryHistoryFile = home.resolve("query-history.json");
        this.executionHistoryFile = home.resolve("execution-history.json");
        this.key = AesGcm.loadOrCreate(home.resolve("master.key"));
        loadSecrets();
        ensureDefaults();
    }

    /** Prefer ~/.ecm-dev-workbench; adopt ~/.dctm-admin if that is all that exists. */
    private static Path migrateLegacyHome(Path home) {
        Path legacy = Path.of(System.getProperty("user.home"), ".dctm-admin");
        try {
            if (!Files.exists(home) && Files.isDirectory(legacy)) {
                Files.move(legacy, home);
            }
        } catch (Exception ignored) {
            // keep the new home; user can copy profiles manually
        }
        return home;
    }

    public synchronized List<ConnectionProfile> list() {
        return readProfiles();
    }

    public synchronized Optional<ConnectionProfile> get(String id) {
        return readProfiles().stream().filter(p -> id.equals(p.getId())).findFirst();
    }

    public synchronized ConnectionProfile save(ConnectionProfile profile, String secret) {
        List<ConnectionProfile> all = readProfiles();
        if (profile.getId() == null || profile.getId().isBlank()) {
            profile.setId(UUID.randomUUID().toString());
        }
        if (profile.getSecretId() == null) {
            profile.setSecretId("secret-" + profile.getId());
        }
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(profile.getId())) {
                all.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            all.add(profile);
        }
        writeProfiles(all);
        if (secret != null) {
            secrets.put(profile.getSecretId(), secret);
            persistSecrets();
        }
        return profile;
    }

    public synchronized void delete(String id) {
        List<ConnectionProfile> all = readProfiles();
        all.removeIf(p -> id.equals(p.getId()));
        writeProfiles(all);
    }

    public char[] secretFor(ConnectionProfile profile) {
        if (profile.getSecretId() == null) {
            return new char[0];
        }
        String s = secrets.get(profile.getSecretId());
        return s == null ? new char[0] : s.toCharArray();
    }

    public synchronized List<SavedQuery> queries() {
        try {
            if (!Files.exists(queriesFile)) {
                return new ArrayList<>();
            }
            return Json.mapper().readValue(queriesFile.toFile(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public synchronized SavedQuery saveQuery(SavedQuery query) {
        List<SavedQuery> all = queries();
        if (query.getId() == null) {
            query.setId(UUID.randomUUID().toString());
        }
        all.removeIf(q -> q.getId().equals(query.getId()));
        all.add(query);
        writeJson(queriesFile, all);
        return query;
    }

    public synchronized void deleteQuery(String id) {
        List<SavedQuery> all = queries();
        if (all.removeIf(q -> id.equals(q.getId()))) {
            writeJson(queriesFile, all);
        }
    }

    public synchronized List<QueryHistoryEntry> queryHistory(Product product) {
        try {
            if (!Files.exists(queryHistoryFile)) {
                return new ArrayList<>();
            }
            List<QueryHistoryEntry> all = Json.mapper().readValue(queryHistoryFile.toFile(), new TypeReference<>() {
            });
            if (product == null) {
                return all;
            }
            return all.stream().filter(e -> product.equals(e.getProduct())).toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public synchronized QueryHistoryEntry appendQueryHistory(String text, Product product) {
        if (text == null || text.isBlank()) {
            throw new com.dctm.workbench.core.SessionException("Query text is required");
        }
        List<QueryHistoryEntry> all = new ArrayList<>(queryHistory(null));
        all.removeIf(e -> text.equals(e.getText()) && product.equals(e.getProduct()));
        QueryHistoryEntry entry = new QueryHistoryEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setText(text);
        entry.setProduct(product);
        entry.setLastUsed(System.currentTimeMillis());
        all.add(0, entry);
        if (all.size() > MAX_QUERY_HISTORY) {
            all = new ArrayList<>(all.subList(0, MAX_QUERY_HISTORY));
        }
        writeJson(queryHistoryFile, all);
        return entry;
    }

    public synchronized List<ExecutionHistoryEntry> executionHistory(Product product) {
        try {
            if (!Files.exists(executionHistoryFile)) {
                return new ArrayList<>();
            }
            List<ExecutionHistoryEntry> all = Json.mapper().readValue(executionHistoryFile.toFile(), new TypeReference<>() {
            });
            if (product == null) {
                return all;
            }
            return all.stream().filter(e -> product.equals(e.getProduct())).toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public synchronized ExecutionHistoryEntry appendExecutionHistory(ExecutionHistoryEntry entry) {
        List<ExecutionHistoryEntry> all = new ArrayList<>(executionHistory(null));
        if (entry.getId() == null) {
            entry.setId(UUID.randomUUID().toString());
        }
        entry.setLastUsed(System.currentTimeMillis());
        all.add(0, entry);
        if (all.size() > MAX_EXECUTION_HISTORY) {
            all = new ArrayList<>(all.subList(0, MAX_EXECUTION_HISTORY));
        }
        writeJson(executionHistoryFile, all);
        return entry;
    }

    private void writeJson(Path file, Object value) {
        try {
            Files.createDirectories(home);
            Json.mapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value);
        } catch (Exception e) {
            throw new com.dctm.workbench.core.SessionException("Cannot write " + file.getFileName(), e);
        }
    }

    private void ensureDefaults() {
        List<ConnectionProfile> all = readProfiles();
        if (!all.isEmpty()) {
            return;
        }
        ConnectionProfile dctm = new ConnectionProfile();
        dctm.setName("Local mock (Documentum)");
        dctm.setProduct(Product.DOCUMENTUM);
        dctm.setProtocol(Protocol.MOCK_DFC);
        dctm.setRepository("mock");
        dctm.setUsername("dmadmin");
        dctm.setReportedVersion("24.2");
        save(dctm, "");

        ConnectionProfile otcs = new ConnectionProfile();
        otcs.setName("Local mock (Extended ECM)");
        otcs.setProduct(Product.EXTENDED_ECM);
        otcs.setProtocol(Protocol.MOCK_OTCS);
        otcs.setRepository("mock-otcs");
        otcs.setUsername("Admin");
        otcs.setReportedVersion("24.2");
        otcs.setAuthMode(AuthMode.PASSWORD);
        save(otcs, "");
    }

    private List<ConnectionProfile> readProfiles() {
        try {
            if (!Files.exists(profilesFile)) {
                return new ArrayList<>();
            }
            return Json.mapper().readValue(profilesFile.toFile(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void writeProfiles(List<ConnectionProfile> all) {
        try {
            Files.createDirectories(home);
            Json.mapper().writerWithDefaultPrettyPrinter().writeValue(profilesFile.toFile(), all);
        } catch (Exception e) {
            throw new com.dctm.workbench.core.SessionException("Cannot save profiles", e);
        }
    }

    private void loadSecrets() {
        Path file = home.resolve("secrets.enc");
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = AesGcm.decrypt(key, Files.readString(file));
            Map<String, String> map = Json.mapper().readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
            secrets.putAll(map);
        } catch (Exception ignored) {
            // start empty
        }
    }

    private void persistSecrets() {
        try {
            Files.createDirectories(home);
            String json = Json.mapper().writeValueAsString(secrets);
            Files.writeString(home.resolve("secrets.enc"), AesGcm.encrypt(key, json));
        } catch (Exception e) {
            throw new com.dctm.workbench.core.SessionException("Cannot persist secrets", e);
        }
    }
}
