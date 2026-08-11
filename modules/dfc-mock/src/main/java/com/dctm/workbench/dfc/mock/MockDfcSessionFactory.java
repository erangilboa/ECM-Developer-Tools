package com.dctm.workbench.dfc.mock;

import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.DfcConnectRequest;
import com.dctm.workbench.core.DfcDocumentumSession;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.RepositorySession;
import com.dctm.workbench.core.ServerInfo;
import com.dctm.workbench.core.SessionFactory;

public class MockDfcSessionFactory implements SessionFactory {

    @Override
    public boolean supports(ConnectionProfile profile) {
        return profile.getProtocol() == Protocol.MOCK_DFC;
    }

    @Override
    public RepositorySession connect(ConnectionProfile profile, char[] secret) {
        MockDfcBridge bridge = new MockDfcBridge();
        ServerInfo info = bridge.connect(new DfcConnectRequest(
                profile.getRepository(),
                profile.getUsername(),
                secret,
                null,
                null,
                profile.getReportedVersion()
        ));
        return new DfcDocumentumSession(bridge, info);
    }
}
