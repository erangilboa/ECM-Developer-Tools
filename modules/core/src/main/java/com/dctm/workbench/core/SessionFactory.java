package com.dctm.workbench.core;

public interface SessionFactory {

    boolean supports(ConnectionProfile profile);

    RepositorySession connect(ConnectionProfile profile, char[] secret);
}
