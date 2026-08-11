package com.dctm.workbench.core;

public interface DfcBridge {

    ServerInfo connect(DfcConnectRequest request);

    DqlResult query(String dql, QueryMode mode);

    ObjectDump getObject(String id);

    void saveObject(ObjectDump dump);

    void checkout(String id);

    void checkin(String id);

    IapiResult iapi(String command);

    TypeDictionary types();

    byte[] getContent(String id);

    void reset();

    void disconnect();
}
