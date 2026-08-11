package com.dctm.workbench.core;

public record JobReport(
        String id,
        String objectName,
        String created,
        String contentType,
        String subject
) {
}
