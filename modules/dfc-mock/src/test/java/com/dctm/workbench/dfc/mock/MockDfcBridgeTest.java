package com.dctm.workbench.dfc.mock;

import com.dctm.workbench.core.DfcConnectRequest;
import com.dctm.workbench.core.DqlResult;
import com.dctm.workbench.core.ObjectDump;
import com.dctm.workbench.core.QueryMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockDfcBridgeTest {

    private MockDfcBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new MockDfcBridge();
        bridge.connect(new DfcConnectRequest("mock", "dmadmin", new char[0], null, null, "24.2"));
    }

    @Test
    void selectCabinets() {
        DqlResult result = bridge.query("SELECT r_object_id, object_name FROM dm_cabinet", QueryMode.READ);
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.columns()).contains("object_name");
    }

    @Test
    void folderPredicate() {
        DqlResult result = bridge.query(
                "SELECT object_name FROM dm_sysobject WHERE folder('0c00000180000001')", QueryMode.READ);
        assertThat(result.rows().stream().map(r -> r.get(0))).contains("Incoming", "readme.txt");
    }

    @Test
    void dumpAndSave() {
        ObjectDump dump = bridge.getObject("0900000180000001");
        assertThat(dump.objectName()).isEqualTo("Sample Contract.txt");
        assertThat(dump.attr("title")).isEqualTo("Sample Contract");
    }

    @Test
    void runNowViaApply() {
        assertThat(bridge.iapi("apply,c,0800000180000001,RUN_NOW").ok()).isTrue();
        assertThat(bridge.getObject("0800000180000001").attr("run_now")).isEqualTo("F");
        assertThat(bridge.getObject("0800000180000001").attr("a_last_return")).isEqualTo("0");
        DqlResult reports = bridge.query(
                "SELECT object_name FROM dm_document WHERE folder('0b00000180000004')", QueryMode.READ);
        assertThat(reports.rows().stream().map(r -> r.get(0)))
                .anyMatch(name -> name.startsWith("dm_DataDictionaryPublisher_"));
    }

    @Test
    void whereNotEqualsAcceptsUnicode() {
        DqlResult result = bridge.query(
                "select * from dm_document where object_name <>'בדיקה'", QueryMode.READ);
        assertThat(result.rowCount()).isPositive();
        int nameCol = 0;
        for (int i = 0; i < result.columns().size(); i++) {
            if (result.columns().get(i).equalsIgnoreCase("object_name")) {
                nameCol = i;
                break;
            }
        }
        int col = nameCol;
        assertThat(result.rows().stream().map(r -> r.get(col))).doesNotContain("בדיקה");
    }

    @Test
    void returnTop() {
        DqlResult result = bridge.query("SELECT object_name FROM dm_sysobject ENABLE(RETURN_TOP 1)", QueryMode.READ);
        assertThat(result.rowCount()).isEqualTo(1);
    }

    @Test
    void resetReloadsFixtures() {
        bridge.iapi("apply,c,0800000180000001,RUN_NOW");
        bridge.reset();
        assertThat(bridge.getObject("0800000180000001").attr("run_now")).isEqualTo("F");
    }
}
