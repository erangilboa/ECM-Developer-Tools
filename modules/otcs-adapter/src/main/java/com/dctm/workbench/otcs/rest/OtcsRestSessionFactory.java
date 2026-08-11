package com.dctm.workbench.otcs.rest;

import com.dctm.workbench.core.AuthMode;
import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.OtcsConnectRequest;
import com.dctm.workbench.core.OtcsRepositorySession;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.RepositorySession;
import com.dctm.workbench.core.ServerInfo;
import com.dctm.workbench.core.SessionFactory;

public class OtcsRestSessionFactory implements SessionFactory {

    @Override
    public boolean supports(ConnectionProfile profile) {
        return profile.getProtocol() == Protocol.OTCS_REST;
    }

    @Override
    public RepositorySession connect(ConnectionProfile profile, char[] secret) {
        OtcsRestBridge bridge = new OtcsRestBridge();
        String bearer = profile.getAuthMode() == AuthMode.OTDS_BEARER && secret != null ? new String(secret) : null;
        ServerInfo info = bridge.connect(new OtcsConnectRequest(
                profile.getCgiRoot(),
                profile.getUsername(),
                secret,
                profile.getOtdsUrl(),
                profile.getAuthMode() == null ? AuthMode.PASSWORD : profile.getAuthMode(),
                bearer,
                profile.getReportedVersion()
        ));
        return new OtcsRepositorySession(bridge, info);
    }
}
