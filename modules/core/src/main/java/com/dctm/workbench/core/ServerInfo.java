package com.dctm.workbench.core;

import java.util.Set;

public record ServerInfo(
        Product product,
        Protocol protocol,
        String repository,
        String version,
        String userName,
        Set<Capability> capabilities,
        String idLabel
) {
    public static ServerInfo documentum(Protocol protocol, String repository, String version, String user,
                                        Set<Capability> capabilities) {
        return new ServerInfo(Product.DOCUMENTUM, protocol, repository, version, user, capabilities, "r_object_id");
    }

    public static ServerInfo otcs(Protocol protocol, String repository, String version, String user,
                                  Set<Capability> capabilities) {
        return new ServerInfo(Product.EXTENDED_ECM, protocol, repository, version, user, capabilities, "node id");
    }
}
