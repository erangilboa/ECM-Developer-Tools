package com.dctm.workbench.otcs.mock;

import com.dctm.workbench.core.AttributeValue;
import com.dctm.workbench.core.JobInfo;
import com.dctm.workbench.core.JobSupport;
import com.dctm.workbench.core.ObjectDump;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FakeAgent {

    private long id;
    private String name = "";
    private String thread = "";
    private boolean enabled = true;
    private boolean running;
    private String interval = "";
    private String lastRun = "";
    private String nextRun = "";
    private String lastReturn = "";
    private String currentStatus = "";
    private final List<Long> reportIds = new ArrayList<>();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread == null ? "" : thread;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval == null ? "" : interval;
    }

    public String getLastRun() {
        return lastRun;
    }

    public void setLastRun(String lastRun) {
        this.lastRun = lastRun == null ? "" : lastRun;
    }

    public String getNextRun() {
        return nextRun;
    }

    public void setNextRun(String nextRun) {
        this.nextRun = nextRun == null ? "" : nextRun;
    }

    public String getLastReturn() {
        return lastReturn;
    }

    public void setLastReturn(String lastReturn) {
        this.lastReturn = lastReturn == null ? "" : lastReturn;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus == null ? "" : currentStatus;
    }

    public List<Long> getReportIds() {
        return reportIds;
    }

    public JobInfo toInfo() {
        boolean inactive = !enabled;
        return new JobInfo(
                String.valueOf(id),
                name,
                thread,
                inactive,
                running,
                lastRun,
                nextRun,
                interval,
                lastReturn,
                currentStatus,
                JobSupport.status(inactive, running, lastReturn)
        );
    }

    public ObjectDump toDump() {
        List<AttributeValue> attrs = new ArrayList<>();
        attrs.add(attr("id", String.valueOf(id), true));
        attrs.add(attr("name", name, false));
        attrs.add(attr("thread", thread, false));
        attrs.add(attr("enabled", enabled ? "true" : "false", false));
        attrs.add(attr("running", running ? "true" : "false", true));
        attrs.add(attr("interval", interval, false));
        attrs.add(attr("last_run", lastRun, true));
        attrs.add(attr("next_run", nextRun, true));
        attrs.add(attr("last_return", lastReturn, true));
        attrs.add(attr("current_status", currentStatus, true));
        return new ObjectDump(String.valueOf(id), "Scheduled Agent", name, attrs, List.of(),
                Map.of("subtype", "agent"), false);
    }

    private static AttributeValue attr(String name, String value, boolean readOnly) {
        return new AttributeValue(name, "string", false, List.of(value == null ? "" : value), readOnly);
    }
}
