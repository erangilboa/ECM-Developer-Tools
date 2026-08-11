package com.dctm.workbench.rest;

import com.dctm.workbench.core.AuthMode;
import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.RepositorySession;
import com.dctm.workbench.core.SessionException;
import com.dctm.workbench.core.SessionFactory;
import com.dctm.workbench.otds.OtdsClient;
import com.dctm.workbench.otds.OtdsToken;

public class DctmRestSessionFactory implements SessionFactory {

    private final OtdsClient otds = new OtdsClient();

    @Override
    public boolean supports(ConnectionProfile profile) {
        return profile.getProtocol() == Protocol.DCTM_REST;
    }

    @Override
    public RepositorySession connect(ConnectionProfile profile, char[] secret) {
        if (profile.getRestBaseUrl() == null || profile.getRestBaseUrl().isBlank()) {
            throw new SessionException("DCTM REST base URL is required");
        }
        String bearer = null;
        if (profile.getAuthMode() == AuthMode.OTDS_BEARER && secret != null) {
            bearer = otds.fromStoredBearer(new String(secret)).bearerHeader();
        } else if (profile.getAuthMode() == AuthMode.OTDS_PASSWORD && profile.getOtdsUrl() != null) {
            OtdsToken token = otds.passwordGrant(profile.getOtdsUrl(), profile.getOtdsClientId(), null,
                    profile.getUsername(), secret);
            bearer = token.bearerHeader();
        } else if (profile.getAuthMode() == AuthMode.OTDS_SSO) {
            throw new SessionException("OTDS browser SSO is not implemented yet");
        }
        return new DctmRestSession(profile.getRestBaseUrl(), profile.getRepository(), profile.getUsername(), secret, bearer);
    }
}
