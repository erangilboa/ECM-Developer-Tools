package com.dctm.workbench.dfs;

import com.dctm.workbench.core.Capability;
import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.RepositorySession;
import com.dctm.workbench.core.SessionFactory;
import com.dctm.workbench.core.UnsupportedCapabilityException;

public class DfsSessionFactory implements SessionFactory {

    @Override
    public boolean supports(ConnectionProfile profile) {
        return profile.getProtocol() == Protocol.DFS || profile.getProtocol() == Protocol.CWS;
    }

    @Override
    public RepositorySession connect(ConnectionProfile profile, char[] secret) {
        String name = profile.getProtocol() == Protocol.CWS ? "CWS SOAP" : "DFS";
        throw new UnsupportedCapabilityException(Capability.DFS_INVOKE,
                name + " explorer is stubbed. Configure Documentum REST/DFC or OTCS REST for live work.");
    }
}
