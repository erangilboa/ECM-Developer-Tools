package com.dctm.workbench.core.grammar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrammarCheckTest {

    @Test
    void selectStarIsValid() {
        assertThat(errors("SELECT r_object_id, object_name FROM dm_document")).isEmpty();
        assertThat(errors("SELECT * FROM dm_document WHERE object_name <> 'בדיקה'")).isEmpty();
        assertThat(errors("SELECT * FROM dm_document WHERE FOLDER('/Temp', DESCEND)")).isEmpty();
        assertThat(errors("select * from dm_sysobject where r_object_type in ('dm_document','dm_folder')"))
                .isEmpty();
    }

    @Test
    void incompletePrefixIsSilent() {
        assertThat(GrammarCheck.dql("sel")).noneMatch(i -> i.severity() == GrammarIssue.Severity.ERROR);
        assertThat(GrammarCheck.dql("select * fr")).noneMatch(i -> i.severity() == GrammarIssue.Severity.ERROR);
        assertThat(GrammarCheck.dql("select * from dm_document where object_name <>"))
                .noneMatch(i -> i.severity() == GrammarIssue.Severity.ERROR);
    }

    @Test
    void missingSelectList() {
        assertThat(errors("SELECT FROM dm_document")).isNotEmpty();
    }

    @Test
    void missingFrom() {
        assertThat(errors("SELECT * WHERE object_name = 'x'")).anyMatch(m -> m.contains("FROM"));
    }

    @Test
    void unterminatedString() {
        assertThat(errors("SELECT * FROM dm_document WHERE object_name = 'open")).anyMatch(m -> m.contains("Unterminated"));
    }

    @Test
    void unexpectedCharacter() {
        assertThat(errors("SELECT * FROM dm_document WHERE object_name @ 'x'")).isNotEmpty();
    }

    @Test
    void updateAndDelete() {
        assertThat(errors("UPDATE dm_document OBJECTS SET title = 'n' WHERE r_object_id = '0900000180000001'"))
                .isEmpty();
        assertThat(errors("DELETE dm_document OBJECTS WHERE object_name = 'gone'")).isEmpty();
    }

    @Test
    void iapiDumpAndGet() {
        assertThat(GrammarCheck.iapi("dump,c,0900000180000001"))
                .noneMatch(i -> i.severity() == GrammarIssue.Severity.ERROR);
        assertThat(GrammarCheck.iapi("get,c,l,object_name"))
                .noneMatch(i -> i.severity() == GrammarIssue.Severity.ERROR);
        assertThat(GrammarCheck.iapi("set,c,l,object_name,hello"))
                .noneMatch(i -> i.severity() == GrammarIssue.Severity.ERROR);
    }

    @Test
    void iapiTooFewArgs() {
        assertThat(errorsIapi("dump")).anyMatch(m -> m.contains("Too few"));
        assertThat(errorsIapi("fetch,c")).anyMatch(m -> m.contains("Too few"));
    }

    @Test
    void iapiUnknownMethodIsWarning() {
        List<GrammarIssue> issues = GrammarCheck.iapi("nope,c,0900000180000001");
        assertThat(issues).isNotEmpty();
        assertThat(issues.get(0).severity()).isEqualTo(GrammarIssue.Severity.WARNING);
        assertThat(issues.get(0).message()).contains("Unknown IAPI method");
    }

    @Test
    void iapiIncompleteMethodIsSilent() {
        assertThat(GrammarCheck.iapi("dum")).isEmpty();
        assertThat(GrammarCheck.iapi("exe")).isEmpty();
    }

    @Test
    void iapiExecqueryChecksDql() {
        List<GrammarIssue> issues = GrammarCheck.iapi("execquery,c,SELECT FROM dm_document");
        assertThat(issues).anyMatch(i -> i.message().contains("DQL") && i.message().contains("select list"));
    }

    @Test
    void iapiQuotedCommaInDql() {
        assertThat(GrammarCheck.iapi("execquery,c,select r_object_id from dm_document where object_name = 'a,b'"))
                .noneMatch(i -> i.severity() == GrammarIssue.Severity.ERROR);
    }

    private static List<String> errors(String dql) {
        return GrammarCheck.dql(dql).stream()
                .filter(i -> i.severity() == GrammarIssue.Severity.ERROR)
                .map(GrammarIssue::message)
                .toList();
    }

    private static List<String> errorsIapi(String cmd) {
        return GrammarCheck.iapi(cmd).stream()
                .filter(i -> i.severity() == GrammarIssue.Severity.ERROR)
                .map(GrammarIssue::message)
                .toList();
    }
}
