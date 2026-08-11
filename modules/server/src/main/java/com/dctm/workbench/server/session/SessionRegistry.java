package com.dctm.workbench.server.session;

import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.RepositorySession;
import com.dctm.workbench.core.SessionException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    public record Handle(String id, ConnectionProfile profile, RepositorySession session, Instant opened) {
    }

    private final Map<String, Handle> sessions = new ConcurrentHashMap<>();

    public Handle put(ConnectionProfile profile, RepositorySession session) {
        String id = UUID.randomUUID().toString();
        Handle handle = new Handle(id, profile, session, Instant.now());
        sessions.put(id, handle);
        return handle;
    }

    public Handle require(String id) {
        Handle handle = sessions.get(id);
        if (handle == null) {
            throw new SessionException("Unknown or expired session");
        }
        return handle;
    }

    public void close(String id) {
        Handle handle = sessions.remove(id);
        if (handle != null) {
            handle.session().close();
        }
    }
}
