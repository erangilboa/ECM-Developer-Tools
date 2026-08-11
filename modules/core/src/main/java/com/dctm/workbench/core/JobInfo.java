package com.dctm.workbench.core;

public record JobInfo(
        String id,
        String objectName,
        String methodName,
        boolean inactive,
        boolean runNow,
        String lastCompletionDate,
        String nextInvocationDate,
        String runInterval,
        String lastReturn,
        String currentStatus,
        String status
) {
}
