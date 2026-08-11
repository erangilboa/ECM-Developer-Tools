package com.dctm.workbench.otcs.mock;

import com.dctm.workbench.core.AuthMode;
import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.OtcsConnectRequest;
import com.dctm.workbench.core.OtcsRepositorySession;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.RepositorySession;
import com.dctm.workbench.core.ServerInfo;
import com.dctm.workbench.core.SessionFactory;

public class MockOtcsSessionFactory implements SessionFactory {

    @Override
    public boolean supports(ConnectionProfile profile) {
        return profile.getProtocol() == Protocol.MOCK_OTCS;
    }

    @Override
    public RepositorySession connect(ConnectionProfile profile, char[] secret) {
        MockOtcsBridge bridge = new MockOtcsBridge();
        ServerInfo info = bridge.connect(new OtcsConnectRequest(
                profile.getCgiRoot(),
                profile.getUsername(),
                secret,
                profile.getOtdsUrl(),
                profile.getAuthMode() == null ? AuthMode.PASSWORD : profile.getAuthMode(),
                secret == null ? null : new String(secret),
                profile.getReportedVersion()
        ));
        return new OtcsRepositorySession(bridge, info);
    }
}
