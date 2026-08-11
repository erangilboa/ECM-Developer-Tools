package com.dctm.workbench.dfc.live;

import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.DfcConnectRequest;
import com.dctm.workbench.core.DfcDocumentumSession;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.RepositorySession;
import com.dctm.workbench.core.ServerInfo;
import com.dctm.workbench.core.SessionFactory;

public class LiveDfcSessionFactory implements SessionFactory {

    @Override
    public boolean supports(ConnectionProfile profile) {
        return profile.getProtocol() == Protocol.LIVE_DFC;
    }

    @Override
    public RepositorySession connect(ConnectionProfile profile, char[] secret) {
        LiveDfcBridge bridge = new LiveDfcBridge();
        ServerInfo info = bridge.connect(new DfcConnectRequest(
                profile.getRepository(),
                profile.getUsername(),
                secret,
                profile.getDfcLibDir(),
                profile.getDfcPropertiesPath(),
                profile.getReportedVersion()
        ));
        return new DfcDocumentumSession(bridge, info);
    }
}
