package com.dctm.workbench.server.api;

import com.dctm.workbench.core.AuthMode;
import com.dctm.workbench.core.Capability;
import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.Product;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.ResolveResult;
import com.dctm.workbench.core.ServerInfo;

import java.time.Instant;
import java.util.List;
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
            Set<Capability> capabilities,
            AuthMode authMode,
            String restBaseUrl,
            String cgiRoot,
            String connectedAt
    ) {
        static SessionView of(String id, ConnectionProfile profile, ServerInfo info, Instant connectedAt) {
            return new SessionView(id, profile.getId(), profile.getName(), info.product(), info.protocol(),
                    info.repository(), info.version(), info.userName(), info.idLabel(), info.capabilities(),
                    profile.getAuthMode(), profile.getRestBaseUrl(), profile.getCgiRoot(),
                    connectedAt == null ? null : connectedAt.toString());
        }
    }

    public record ResolveBody(String input) {
    }

    public record ResolveResponse(String kind, String id, String label, String action) {
        static ResolveResponse of(ResolveResult result) {
            return new ResolveResponse(result.kind(), result.id(), result.label(), result.action());
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

    public record GrammarBody(String language, String text) {
    }

    public record GrammarIssueView(
            int offset,
            int length,
            int line,
            int column,
            String message,
            String severity
    ) {
        static GrammarIssueView of(com.dctm.workbench.core.grammar.GrammarIssue issue) {
            return new GrammarIssueView(
                    issue.offset(),
                    issue.length(),
                    issue.line(),
                    issue.column(),
                    issue.message(),
                    issue.severity().name());
        }
    }

    public record GrammarResponse(List<GrammarIssueView> issues) {
    }
}
