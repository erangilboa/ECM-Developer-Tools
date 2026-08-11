package com.dctm.workbench.server.config;

import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.RepositorySession;
import com.dctm.workbench.core.SessionException;
import com.dctm.workbench.core.SessionFactory;

import java.util.List;

public class CompositeSessionFactory {

    private final List<SessionFactory> factories;

    public CompositeSessionFactory(List<SessionFactory> factories) {
        this.factories = factories;
    }

    public RepositorySession connect(ConnectionProfile profile, char[] secret) {
        return factories.stream()
                .filter(f -> f.supports(profile))
                .findFirst()
                .orElseThrow(() -> new SessionException("No adapter for protocol " + profile.getProtocol()))
                .connect(profile, secret);
    }
}
