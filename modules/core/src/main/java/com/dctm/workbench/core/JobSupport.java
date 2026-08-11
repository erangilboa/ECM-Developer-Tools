package com.dctm.workbench.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JobSupport {

    private JobSupport() {
    }

    public static String status(boolean inactive, boolean runNow, String lastReturn) {
        if (runNow) {
            return "RUNNING";
        }
        if (inactive) {
            return "INACTIVE";
        }
        if (lastReturn != null && !lastReturn.isBlank() && !"0".equals(lastReturn.trim())) {
            return "FAILED";
        }
        if (lastReturn != null && "0".equals(lastReturn.trim())) {
            return "SUCCESS";
        }
        return "SCHEDULED";
    }

    public static JobInfo fromRow(DqlResult result, List<String> row) {
        boolean inactive = bool(col(result, row, "is_inactive"));
        boolean runNow = bool(col(result, row, "run_now"));
        String lastReturn = col(result, row, "a_last_return");
        return new JobInfo(
                col(result, row, "r_object_id"),
                col(result, row, "object_name"),
                col(result, row, "method_name"),
                inactive,
                runNow,
                col(result, row, "a_last_completion"),
                col(result, row, "a_next_invocation"),
                col(result, row, "run_interval"),
                lastReturn,
                col(result, row, "a_current_status"),
                status(inactive, runNow, lastReturn)
        );
    }

    public static JobInfo fromDump(ObjectDump dump) {
        boolean inactive = bool(dump.attr("is_inactive"));
        boolean runNow = bool(dump.attr("run_now"));
        String lastReturn = dump.attr("a_last_return");
        return new JobInfo(
                dump.id(),
                dump.objectName(),
                dump.attr("method_name"),
                inactive,
                runNow,
                dump.attr("a_last_completion"),
                dump.attr("a_next_invocation"),
                dump.attr("run_interval"),
                lastReturn,
                dump.attr("a_current_status"),
                status(inactive, runNow, lastReturn)
        );
    }

    public static JobDetail load(DocumentumSession session, String jobId) {
        ObjectDump dump = session.dump(jobId);
        JobInfo info = fromDump(dump);
        List<JobReport> reports = new ArrayList<>();
        String folderId = reportsFolderId(session);
        DqlResult result = new DqlResult(List.of(), List.of(), 0, "", 0);
        try {
            if (folderId != null) {
                result = session.executeDql(DqlRequest.select(
                        "SELECT r_object_id, object_name, r_creation_date, a_content_type, subject FROM dm_document WHERE folder('"
                                + folderId + "')"));
            } else {
                result = session.executeDql(DqlRequest.select(
                        "SELECT r_object_id, object_name, r_creation_date, a_content_type, subject FROM dm_document WHERE subject = '"
                                + escape(info.objectName()) + "'"));
            }
        } catch (SessionException ignored) {
            // REST/live repos may lack Sysadmin/Reports or folder() support
        }
        String jobName = info.objectName() == null ? "" : info.objectName().toLowerCase(Locale.ROOT);
        for (List<String> row : result.rows()) {
            String subject = col(result, row, "subject");
            String name = col(result, row, "object_name");
            if (!jobName.isEmpty()
                    && !name.toLowerCase(Locale.ROOT).contains(jobName)
                    && !subject.toLowerCase(Locale.ROOT).contains(jobName)) {
                continue;
            }
            reports.add(new JobReport(
                    col(result, row, "r_object_id"),
                    name,
                    col(result, row, "r_creation_date"),
                    col(result, row, "a_content_type"),
                    subject
            ));
        }
        return new JobDetail(info, reports);
    }

    private static String reportsFolderId(DocumentumSession session) {
        try {
            DqlResult folders = session.executeDql(DqlRequest.select(
                    "SELECT r_object_id FROM dm_folder WHERE r_folder_path = '/System/Sysadmin/Reports'"));
            if (folders.rowCount() > 0 && !folders.rows().get(0).isEmpty()) {
                return folders.rows().get(0).get(0);
            }
        } catch (SessionException ignored) {
            // older repos may not have the path
        }
        return null;
    }

    private static String col(DqlResult result, List<String> row, String name) {
        for (int i = 0; i < result.columns().size(); i++) {
            if (result.columns().get(i).equalsIgnoreCase(name) && i < row.size()) {
                return row.get(i);
            }
        }
        return "";
    }

    private static boolean bool(String value) {
        return "T".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
