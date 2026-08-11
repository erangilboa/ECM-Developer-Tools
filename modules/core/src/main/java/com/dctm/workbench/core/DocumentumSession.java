package com.dctm.workbench.core;

public interface DocumentumSession extends RepositorySession {

    DqlResult executeDql(DqlRequest request);

    IapiResult iapi(String command);

    JobList listJobs(JobFilter filter);

    void runJob(String jobId);

    default JobDetail getJob(String jobId) {
        return JobSupport.load(this, jobId);
    }

    TypeDictionary types();

    void checkout(String id);

    void checkin(String id);

    void resetMock();
}
