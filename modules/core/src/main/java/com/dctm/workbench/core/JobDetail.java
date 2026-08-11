package com.dctm.workbench.core;

import java.util.List;

public record JobDetail(JobInfo info, List<JobReport> reports) {
}
