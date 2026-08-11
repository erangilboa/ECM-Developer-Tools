package com.dctm.workbench.otcs.rest;

import com.dctm.workbench.core.AuthMode;
import com.dctm.workbench.core.BrowseFilter;
import com.dctm.workbench.core.OtcsConnectRequest;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class OtcsRestBridgeTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void authAndBrowse() {
        wm.stubFor(post(urlEqualTo("/otcs/cs/api/v1/auth"))
                .willReturn(okJson("{\"ticket\":\"t-1\"}")));
        wm.stubFor(get(urlEqualTo("/otcs/cs/api/v1/volumes/141"))
                .willReturn(okJson("{\"properties\":{\"id\":2000,\"name\":\"Enterprise\",\"type\":141}}")
                        .withHeader("OTCSTicket", "t-2")));
        wm.stubFor(get(urlEqualTo("/otcs/cs/api/v1/volumes/142"))
                .willReturn(okJson("{\"properties\":{\"id\":1000,\"name\":\"Personal\",\"type\":142}}")));
        wm.stubFor(get(urlPathEqualTo("/otcs/cs/api/v2/nodes/2000/nodes"))
                .willReturn(okJson("{\"results\":[{\"data\":{\"properties\":{\"id\":3000,\"name\":\"Projects\",\"type\":0}}}]}")));
        wm.stubFor(get(urlPathEqualTo("/otcs/cs/api/v2/nodes/2000"))
                .willReturn(okJson("{\"results\":{\"data\":{\"properties\":{\"id\":2000,\"name\":\"Enterprise\",\"type\":141}}}}")));

        OtcsRestBridge bridge = new OtcsRestBridge();
        bridge.connect(new OtcsConnectRequest(wm.getRuntimeInfo().getHttpBaseUrl() + "/otcs/cs",
                "Admin", "pw".toCharArray(), null, AuthMode.PASSWORD, null, "24.2"));
        assertThat(bridge.volumes()).extracting(n -> n.name()).contains("Enterprise");
        assertThat(bridge.children(2000, BrowseFilter.none()).children()).extracting(n -> n.name()).contains("Projects");
    }
}
