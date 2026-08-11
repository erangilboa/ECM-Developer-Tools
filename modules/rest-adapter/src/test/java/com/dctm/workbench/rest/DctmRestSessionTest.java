package com.dctm.workbench.rest;

import com.dctm.workbench.core.DqlRequest;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class DctmRestSessionTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void rewritesJobDql() {
        assertThat(DctmRestSession.rewriteJobQuery("SELECT * FROM dm_job"))
                .containsIgnoringCase("method_id");
        assertThat(DctmRestSession.rewriteJobQuery("SELECT r_object_id FROM dm_document"))
                .doesNotContain("method_id");
    }

    @Test
    void executesSelect() {
        wm.stubFor(get(urlPathEqualTo("/dctm-rest/"))
                .willReturn(okJson("{\"properties\":{\"product-version\":\"21.2\"}}")
                        .withHeader("X-CSRF-TOKEN", "csrf-1")));
        wm.stubFor(get(urlPathEqualTo("/dctm-rest/repositories/REPO"))
                .willReturn(okJson("""
                        {"entries":[{"content":{"properties":{"r_object_id":"0c1","object_name":"Temp","r_object_type":"dm_cabinet"}}}]}
                        """)));
        DctmRestSession session = new DctmRestSession(wm.getRuntimeInfo().getHttpBaseUrl() + "/dctm-rest",
                "REPO", "dmadmin", "pw".toCharArray(), null);
        var result = session.executeDql(DqlRequest.select("SELECT r_object_id, object_name, r_object_type FROM dm_cabinet"));
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.rows().get(0)).contains("Temp");
    }
}
