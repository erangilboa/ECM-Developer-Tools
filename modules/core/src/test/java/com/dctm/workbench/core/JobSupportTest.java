package com.dctm.workbench.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobSupportTest {

    @Test
    void statusFromFlags() {
        assertThat(JobSupport.status(false, true, "0")).isEqualTo("RUNNING");
        assertThat(JobSupport.status(true, false, "0")).isEqualTo("INACTIVE");
        assertThat(JobSupport.status(false, false, "1")).isEqualTo("FAILED");
        assertThat(JobSupport.status(false, false, "0")).isEqualTo("SUCCESS");
        assertThat(JobSupport.status(false, false, "")).isEqualTo("SCHEDULED");
    }
}
