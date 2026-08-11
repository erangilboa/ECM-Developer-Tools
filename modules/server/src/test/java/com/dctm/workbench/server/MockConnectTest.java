package com.dctm.workbench.server;

import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.ContentPayload;
import com.dctm.workbench.core.DocumentumSession;
import com.dctm.workbench.core.JobDetail;
import com.dctm.workbench.core.JobFilter;
import com.dctm.workbench.core.OtcsSession;
import com.dctm.workbench.core.Product;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.dfc.mock.MockDfcSessionFactory;
import com.dctm.workbench.otcs.mock.MockOtcsSessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockConnectTest {

    @Test
    void documentumMock() {
        ConnectionProfile p = new ConnectionProfile();
        p.setProtocol(Protocol.MOCK_DFC);
        p.setProduct(Product.DOCUMENTUM);
        p.setUsername("dmadmin");
        var session = new MockDfcSessionFactory().connect(p, new char[0]);
        assertThat(session.listRoots()).isNotEmpty();
        assertThat(session.dump("0900000180000001").objectName()).contains("Contract");
        ContentPayload content = session.getContent("0900000180000001");
        assertThat(content.mimeType()).isEqualTo("text/plain");
        assertThat(new String(content.bytes())).contains("SAMPLE CONTRACT");
        DocumentumSession dctm = (DocumentumSession) session;
        JobDetail job = dctm.getJob("0800000180000001");
        assertThat(job.info().status()).isEqualTo("SUCCESS");
        assertThat(job.reports()).extracting(r -> r.objectName())
                .anyMatch(name -> name.contains("dm_DataDictionaryPublisher"));
    }

    @Test
    void otcsMock() {
        ConnectionProfile p = new ConnectionProfile();
        p.setProtocol(Protocol.MOCK_OTCS);
        p.setProduct(Product.EXTENDED_ECM);
        p.setUsername("Admin");
        var session = new MockOtcsSessionFactory().connect(p, new char[0]);
        assertThat(session.listRoots()).extracting(n -> n.name()).contains("Enterprise");
        ContentPayload notes = session.getContent("5100");
        assertThat(notes.mimeType()).isEqualTo("text/plain");
        assertThat(new String(notes.bytes())).contains("Pump-100");
        OtcsSession otcs = (OtcsSession) session;
        assertThat(otcs.listJobs(JobFilter.none()).jobs()).isNotEmpty();
        JobDetail agent = otcs.getJob("8002");
        assertThat(agent.info().objectName()).contains("Workspace Sync");
        assertThat(agent.reports()).isNotEmpty();
    }
}
