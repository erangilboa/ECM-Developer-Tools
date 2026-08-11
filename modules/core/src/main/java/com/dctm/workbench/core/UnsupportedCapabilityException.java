package com.dctm.workbench.core;

public class UnsupportedCapabilityException extends SessionException {

    private final Capability capability;

    public UnsupportedCapabilityException(Capability capability, String message) {
        super(message);
        this.capability = capability;
    }

    public Capability capability() {
        return capability;
    }
}
