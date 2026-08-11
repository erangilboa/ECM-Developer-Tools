package com.dctm.workbench.core;

import java.util.List;

public interface OtcsSession extends RepositorySession {

    List<BusinessWorkspace> listBusinessWorkspaces();

    BusinessWorkspace getWorkspace(String id);

    JobList listJobs(JobFilter filter);

    JobDetail getJob(String jobId);

    void runJob(String jobId);

    void resetMock();
}
