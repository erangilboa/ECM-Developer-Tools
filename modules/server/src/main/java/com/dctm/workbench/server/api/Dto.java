package com.dctm.workbench.server.api;

import com.dctm.workbench.core.Capability;
import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.Product;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.ServerInfo;

import java.util.Set;

public final class Dto {

    private Dto() {
    }

    public record ConnectBody(String profileId, String secret) {
    }

    public record SessionView(
            String id,
            String profileId,
            String profileName,
            Product product,
            Protocol protocol,
            String repository,
            String version,
            String userName,
            String idLabel,
            Set<Capability> capabilities
    ) {
        static SessionView of(String id, ConnectionProfile profile, ServerInfo info) {
            return new SessionView(id, profile.getId(), profile.getName(), info.product(), info.protocol(),
                    info.repository(), info.version(), info.userName(), info.idLabel(), info.capabilities());
        }
    }

    public record DqlBody(String dql, String mode, int maxRows) {
    }

    public record SearchBody(String query, int limit) {
    }

    public record IapiBody(String command) {
    }

    public record ProfileSave(ConnectionProfile profile, String secret) {
    }
}
