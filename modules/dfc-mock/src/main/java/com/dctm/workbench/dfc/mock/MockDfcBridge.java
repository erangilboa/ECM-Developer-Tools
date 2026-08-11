package com.dctm.workbench.dfc.mock;

import com.dctm.workbench.core.CapabilitySets;
import com.dctm.workbench.core.DfcBridge;
import com.dctm.workbench.core.DfcConnectRequest;
import com.dctm.workbench.core.DqlResult;
import com.dctm.workbench.core.IapiResult;
import com.dctm.workbench.core.ObjectDump;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.QueryMode;
import com.dctm.workbench.core.ServerInfo;
import com.dctm.workbench.core.TypeDictionary;

public class MockDfcBridge implements DfcBridge {

    private final FakeDocbase docbase;
    private final SubsetDqlEngine dql;
    private final SubsetIapi iapi;
    private ServerInfo info;

    public MockDfcBridge() {
        this.docbase = FakeDocbase.fromClasspath();
        this.dql = new SubsetDqlEngine(docbase);
        this.iapi = new SubsetIapi(docbase, dql);
    }

    public FakeDocbase docbase() {
        return docbase;
    }

    @Override
    public ServerInfo connect(DfcConnectRequest request) {
        String version = request.reportedVersion() == null || request.reportedVersion().isBlank()
                ? docbase.version() : request.reportedVersion();
        String user = request.username() == null ? "dmadmin" : request.username();
        String repo = request.repository() == null ? docbase.repository() : request.repository();
        info = ServerInfo.documentum(Protocol.MOCK_DFC, repo, version, user, CapabilitySets.mockDfc());
        return info;
    }

    @Override
    public DqlResult query(String dqlText, QueryMode mode) {
        return dql.execute(dqlText, mode);
    }

    @Override
    public ObjectDump getObject(String id) {
        return docbase.dump(id);
    }

    @Override
    public void saveObject(ObjectDump dump) {
        docbase.saveDump(dump);
    }

    @Override
    public void checkout(String id) {
        docbase.checkout(id);
    }

    @Override
    public void checkin(String id) {
        docbase.checkin(id);
    }

    @Override
    public IapiResult iapi(String command) {
        return iapi.execute(command);
    }

    @Override
    public TypeDictionary types() {
        return docbase.typeDictionary();
    }

    @Override
    public byte[] getContent(String id) {
        return docbase.content(id);
    }

    @Override
    public void reset() {
        docbase.reset();
    }

    @Override
    public void disconnect() {
        // in-memory
    }
}
