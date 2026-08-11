package com.dctm.workbench.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTypesTest {

    @Test
    void guessesFromFormatAndName() {
        assertThat(ContentTypes.guess("x.log", "crtext")).isEqualTo("text/plain");
        assertThat(ContentTypes.guess("Manual.pdf", "")).isEqualTo("application/pdf");
        assertThat(ContentTypes.guess("Notes.txt", null)).isEqualTo("text/plain");
    }
}
