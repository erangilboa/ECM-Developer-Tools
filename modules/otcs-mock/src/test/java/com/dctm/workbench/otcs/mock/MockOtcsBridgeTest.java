package com.dctm.workbench.otcs.mock;

import com.dctm.workbench.core.AuthMode;
import com.dctm.workbench.core.BrowseFilter;
import com.dctm.workbench.core.JobFilter;
import com.dctm.workbench.core.OtcsConnectRequest;
import com.dctm.workbench.core.SearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockOtcsBridgeTest {

    private MockOtcsBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new MockOtcsBridge();
        bridge.connect(new OtcsConnectRequest(null, "Admin", new char[0], null, AuthMode.PASSWORD, null, "24.2"));
    }

    @Test
    void volumesAndChildren() {
        assertThat(bridge.volumes()).extracting(n -> n.name()).contains("Enterprise", "Personal");
        assertThat(bridge.children(2000, BrowseFilter.none()).children()).extracting(n -> n.name()).contains("Projects");
    }

    @Test
    void workspaceDumpIsSapLinked() {
        assertThat(bridge.node(4000).sapLinked()).isTrue();
        assertThat(bridge.workspaces()).hasSize(1);
        assertThat(bridge.workspace(4000).boType()).isEqualTo("EQUIPMENT");
    }

    @Test
    void searchByName() {
        assertThat(bridge.search(new SearchRequest("Pump", 20)).rowCount()).isGreaterThan(0);
    }

    @Test
    void refusesSapMutation() {
        assertThatThrownBy(() -> bridge.updateNode(bridge.node(4000)))
                .hasMessageContaining("SAP-linked");
    }

    @Test
    void scheduledAgents() {
        assertThat(bridge.listJobs(JobFilter.none()).jobs())
                .extracting(j -> j.objectName())
                .contains("Notification", "SAP Business Workspace Sync", "Search Index");
        assertThat(bridge.getJob("8002").reports()).isNotEmpty();
        int before = bridge.getJob("8001").reports().size();
        bridge.runJob("8001");
        assertThat(bridge.getJob("8001").info().status()).isEqualTo("SUCCESS");
        assertThat(bridge.getJob("8001").reports().size()).isGreaterThan(before);
    }
}
