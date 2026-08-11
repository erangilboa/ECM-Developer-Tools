package com.dctm.workbench.core;

public record ConnectRequest(ConnectionProfile profile, char[] secret) {
}
