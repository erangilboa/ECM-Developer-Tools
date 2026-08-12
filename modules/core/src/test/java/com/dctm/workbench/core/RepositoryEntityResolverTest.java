package com.dctm.workbench.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryEntityResolverTest {

    @Test
    void documentumObjectId() {
        ResolveResult r = RepositoryEntityResolver.resolve("0900000180000001", Product.DOCUMENTUM);
        assertThat(r.kind()).isEqualTo(ResolveResult.KIND_OBJECT);
        assertThat(r.id()).isEqualTo("0900000180000001");
        assertThat(r.action()).isEqualTo(ResolveResult.ACTION_DUMP);
    }

    @Test
    void documentumRestUrl() {
        ResolveResult r = RepositoryEntityResolver.resolve(
                "https://host/dctm-rest/repositories/repo/objects/0900000180000001",
                Product.DOCUMENTUM);
        assertThat(r.kind()).isEqualTo(ResolveResult.KIND_OBJECT);
        assertThat(r.id()).isEqualTo("0900000180000001");
    }

    @Test
    void otcsNodeId() {
        ResolveResult r = RepositoryEntityResolver.resolve("5100", Product.EXTENDED_ECM);
        assertThat(r.kind()).isEqualTo(ResolveResult.KIND_NODE);
        assertThat(r.id()).isEqualTo("5100");
    }

    @Test
    void otcsUrl() {
        ResolveResult r = RepositoryEntityResolver.resolve(
                "https://cs.example.com/api/v2/nodes/5100",
                Product.EXTENDED_ECM);
        assertThat(r.kind()).isEqualTo(ResolveResult.KIND_NODE);
        assertThat(r.id()).isEqualTo("5100");
    }

    @Test
    void unknownInput() {
        ResolveResult r = RepositoryEntityResolver.resolve("not-an-id", Product.DOCUMENTUM);
        assertThat(r.kind()).isEqualTo(ResolveResult.KIND_UNKNOWN);
        assertThat(r.id()).isEmpty();
    }
}
