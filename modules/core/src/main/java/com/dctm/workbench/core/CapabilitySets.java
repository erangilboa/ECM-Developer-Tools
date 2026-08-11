package com.dctm.workbench.core;

import java.util.EnumSet;
import java.util.Set;

public final class CapabilitySets {

    private CapabilitySets() {
    }

    public static Set<Capability> mockDfc() {
        return EnumSet.of(
                Capability.BROWSE,
                Capability.OBJECT_READ,
                Capability.OBJECT_UPDATE,
                Capability.CONTENT_GET,
                Capability.CONTENT_PUT,
                Capability.CHECKOUT,
                Capability.DQL_SELECT,
                Capability.DQL_EXECUTE,
                Capability.IAPI,
                Capability.JOB_LIST,
                Capability.JOB_RUN,
                Capability.TYPE_DICTIONARY,
                Capability.ACL_READ
        );
    }

    public static Set<Capability> dctmRest() {
        return EnumSet.of(
                Capability.BROWSE,
                Capability.OBJECT_READ,
                Capability.OBJECT_UPDATE,
                Capability.CONTENT_GET,
                Capability.DQL_SELECT,
                Capability.JOB_LIST,
                Capability.JOB_RUN,
                Capability.TYPE_DICTIONARY,
                Capability.OTDS_AUTH
        );
    }

    public static Set<Capability> liveDfc() {
        Set<Capability> caps = EnumSet.copyOf(mockDfc());
        caps.add(Capability.DFS_INVOKE);
        caps.add(Capability.OTDS_AUTH);
        return caps;
    }

    public static Set<Capability> mockOtcs() {
        return EnumSet.of(
                Capability.BROWSE,
                Capability.OBJECT_READ,
                Capability.OBJECT_UPDATE,
                Capability.CONTENT_GET,
                Capability.CONTENT_PUT,
                Capability.CS_SEARCH,
                Capability.CS_CATEGORIES,
                Capability.BUSINESS_WORKSPACE,
                Capability.JOB_LIST,
                Capability.JOB_RUN
        );
    }

    public static Set<Capability> otcsRest() {
        Set<Capability> caps = EnumSet.copyOf(mockOtcs());
        caps.add(Capability.OTDS_AUTH);
        return caps;
    }
}
