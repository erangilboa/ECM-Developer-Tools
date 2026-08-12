package com.dctm.workbench.server;

import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.Product;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.RestProxyRequest;
import com.dctm.workbench.core.RestProxyResponse;
import com.dctm.workbench.core.SessionRestProxy;
import com.dctm.workbench.dfc.mock.MockDfcSessionFactory;
import com.dctm.workbench.otcs.mock.MockOtcsSessionFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRestProxyTest {

    @Test
    void documentumMockObjectGet() {
        ConnectionProfile p = new ConnectionProfile();
        p.setProtocol(Protocol.MOCK_DFC);
        p.setProduct(Product.DOCUMENTUM);
        var session = new MockDfcSessionFactory().connect(p, new char[0]);
        RestProxyResponse res = new SessionRestProxy(session).restProxy(new RestProxyRequest(
                "GET",
                "/repositories/mock/objects/0900000180000001",
                Map.of(),
                null));
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.body()).contains("Contract");
    }

    @Test
    void otcsMockNodeGet() {
        ConnectionProfile p = new ConnectionProfile();
        p.setProtocol(Protocol.MOCK_OTCS);
        p.setProduct(Product.EXTENDED_ECM);
        var session = new MockOtcsSessionFactory().connect(p, new char[0]);
        RestProxyResponse res = new SessionRestProxy(session).restProxy(new RestProxyRequest(
                "GET",
                "/api/v2/nodes/5100",
                Map.of(),
                null));
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.body()).contains("5100");
    }
}
