package com.dctm.workbench.dfc.live;

import com.dctm.workbench.core.AttributeValue;
import com.dctm.workbench.core.CapabilitySets;
import com.dctm.workbench.core.DfcBridge;
import com.dctm.workbench.core.DfcConnectRequest;
import com.dctm.workbench.core.DqlResult;
import com.dctm.workbench.core.IapiResult;
import com.dctm.workbench.core.ObjectDump;
import com.dctm.workbench.core.Protocol;
import com.dctm.workbench.core.QueryMode;
import com.dctm.workbench.core.ServerInfo;
import com.dctm.workbench.core.SessionException;
import com.dctm.workbench.core.TypeDictionary;
import com.dctm.workbench.core.TypeInfo;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reflective DFC access via an isolated classloader. Compiles without dfc.jar.
 */
public class LiveDfcBridge implements DfcBridge {

    private URLClassLoader loader;
    private Object session;
    private ServerInfo info;

    @Override
    public ServerInfo connect(DfcConnectRequest request) {
        if (request.dfcLibDir() == null || request.dfcLibDir().isBlank()) {
            throw new SessionException("Live DFC requires dfcLibDir pointing at a Documentum DFC lib folder");
        }
        Path lib = Path.of(request.dfcLibDir());
        if (!Files.isDirectory(lib)) {
            throw new SessionException("DFC lib dir not found: " + lib);
        }
        if (request.dfcPropertiesPath() != null && !request.dfcPropertiesPath().isBlank()) {
            System.setProperty("dfc.properties.file", request.dfcPropertiesPath());
        }
        try {
            List<URL> urls = new ArrayList<>();
            try (var stream = Files.list(lib)) {
                stream.filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                        .forEach(p -> {
                            try {
                                urls.add(p.toUri().toURL());
                            } catch (Exception e) {
                                throw new SessionException("Cannot load " + p, e);
                            }
                        });
            }
            if (urls.isEmpty()) {
                throw new SessionException("No JARs in DFC lib dir: " + lib);
            }
            loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
            Class<?> dfClient = load("com.documentum.fc.client.DfClient");
            Object client = dfClient.getMethod("getLocalClient").invoke(null);
            Class<?> loginClz = load("com.documentum.fc.common.DfLoginInfo");
            Object login = loginClz.getDeclaredConstructor().newInstance();
            loginClz.getMethod("setUser", String.class).invoke(login, request.username());
            loginClz.getMethod("setPassword", String.class).invoke(login,
                    request.password() == null ? "" : new String(request.password()));
            session = client.getClass().getMethod("newSession", String.class, load("com.documentum.fc.common.IDfLoginInfo"))
                    .invoke(client, request.repository(), login);
            String version = String.valueOf(invoke(session, "getServerVersion"));
            String user = String.valueOf(invoke(session, "getLoginUserName"));
            info = ServerInfo.documentum(Protocol.LIVE_DFC, request.repository(), version, user, CapabilitySets.liveDfc());
            if (request.reportedVersion() != null && !request.reportedVersion().isBlank()
                    && !version.contains(request.reportedVersion())) {
                // still connect; UI shows both via serverInfo.version
            }
            return info;
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("Live DFC connect failed: " + rootMessage(e)
                    + ". Ensure dfc.jar matches the server and Java 17 --add-opens are set.", e);
        }
    }

    @Override
    public DqlResult query(String dql, QueryMode mode) {
        long start = System.currentTimeMillis();
        try {
            Class<?> queryClz = load("com.documentum.fc.client.DfQuery");
            Object query = queryClz.getDeclaredConstructor().newInstance();
            queryClz.getMethod("setDQL", String.class).invoke(query, dql);
            int qtype = mode == QueryMode.EXEC
                    ? queryClz.getField("DF_EXEC_QUERY").getInt(null)
                    : queryClz.getField("DF_READ_QUERY").getInt(null);
            Object collection = queryClz.getMethod("execute", load("com.documentum.fc.client.IDfSession"), int.class)
                    .invoke(query, session, qtype);
            try {
                int attrCount = (int) invoke(collection, "getAttrCount");
                List<String> columns = new ArrayList<>();
                for (int i = 0; i < attrCount; i++) {
                    Object attr = collection.getClass().getMethod("getAttr", int.class).invoke(collection, i);
                    columns.add(String.valueOf(invoke(attr, "getName")));
                }
                List<List<String>> rows = new ArrayList<>();
                Method next = collection.getClass().getMethod("next");
                while (Boolean.TRUE.equals(next.invoke(collection))) {
                    List<String> row = new ArrayList<>();
                    for (String col : columns) {
                        row.add(String.valueOf(collection.getClass().getMethod("getString", String.class)
                                .invoke(collection, col)));
                    }
                    rows.add(row);
                }
                return new DqlResult(columns, rows, rows.size(), dql, System.currentTimeMillis() - start);
            } finally {
                try {
                    invoke(collection, "close");
                } catch (Exception ignored) {
                    // ignore
                }
            }
        } catch (SessionException e) {
            throw e;
        } catch (Exception e) {
            throw new SessionException("DFC query failed: " + rootMessage(e), e);
        }
    }

    @Override
    public ObjectDump getObject(String id) {
        try {
            Object sys = session.getClass().getMethod("getObject", load("com.documentum.fc.common.IDfId"))
                    .invoke(session, idOf(id));
            int count = (int) invoke(sys, "getAttrCount");
            List<AttributeValue> attrs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Object attr = sys.getClass().getMethod("getAttr", int.class).invoke(sys, i);
                String name = String.valueOf(invoke(attr, "getName"));
                boolean repeating = Boolean.TRUE.equals(invoke(attr, "isRepeating"));
                List<String> values = new ArrayList<>();
                if (repeating) {
                    int vc = (int) sys.getClass().getMethod("getValueCount", String.class).invoke(sys, name);
                    for (int v = 0; v < vc; v++) {
                        values.add(String.valueOf(sys.getClass().getMethod("getRepeatingString", String.class, int.class)
                                .invoke(sys, name, v)));
                    }
                } else {
                    values.add(String.valueOf(sys.getClass().getMethod("getString", String.class).invoke(sys, name)));
                }
                attrs.add(new AttributeValue(name, "string", repeating, values, name.startsWith("r_")));
            }
            String type = String.valueOf(invoke(sys, "getTypeName"));
            String name = String.valueOf(sys.getClass().getMethod("getObjectName").invoke(sys));
            return new ObjectDump(id, type, name, attrs, List.of(), Map.of(), false);
        } catch (Exception e) {
            throw new SessionException("DFC getObject failed: " + rootMessage(e), e);
        }
    }

    @Override
    public void saveObject(ObjectDump dump) {
        try {
            Object sys = session.getClass().getMethod("getObject", load("com.documentum.fc.common.IDfId"))
                    .invoke(session, idOf(dump.id()));
            if (dump.attributes() != null) {
                for (AttributeValue attr : dump.attributes()) {
                    if (attr.readOnly()) {
                        continue;
                    }
                    if (attr.repeating()) {
                        sys.getClass().getMethod("removeAll", String.class).invoke(sys, attr.name());
                        for (String v : attr.values()) {
                            sys.getClass().getMethod("appendString", String.class, String.class)
                                    .invoke(sys, attr.name(), v);
                        }
                    } else {
                        sys.getClass().getMethod("setString", String.class, String.class)
                                .invoke(sys, attr.name(), attr.first());
                    }
                }
            }
            invoke(sys, "save");
        } catch (Exception e) {
            throw new SessionException("DFC save failed: " + rootMessage(e), e);
        }
    }

    @Override
    public void checkout(String id) {
        try {
            Object sys = session.getClass().getMethod("getObject", load("com.documentum.fc.common.IDfId"))
                    .invoke(session, idOf(id));
            invoke(sys, "checkout");
        } catch (Exception e) {
            throw new SessionException("DFC checkout failed: " + rootMessage(e), e);
        }
    }

    @Override
    public void checkin(String id) {
        try {
            Object sys = session.getClass().getMethod("getObject", load("com.documentum.fc.common.IDfId"))
                    .invoke(session, idOf(id));
            sys.getClass().getMethod("checkin", boolean.class, String.class).invoke(sys, false, "");
        } catch (Exception e) {
            throw new SessionException("DFC checkin failed: " + rootMessage(e), e);
        }
    }

    @Override
    public IapiResult iapi(String command) {
        try {
            boolean ok = Boolean.TRUE.equals(session.getClass().getMethod("apiExec", String.class, String.class)
                    .invoke(session, "exec", command));
            String out = String.valueOf(invoke(session, "apiGet", "result", ""));
            if (out == null || "null".equals(out)) {
                out = ok ? "OK" : "ERROR";
            }
            return new IapiResult(ok, out, null);
        } catch (NoSuchMethodException e) {
            // fall back: treat as execquery-style not available
            throw new SessionException("This DFC build does not expose apiExec: " + e.getMessage(), e);
        } catch (Exception e) {
            return IapiResult.error(rootMessage(e), null);
        }
    }

    @Override
    public TypeDictionary types() {
        DqlResult result = query("SELECT name, super_name FROM dm_type ORDER BY name", QueryMode.READ);
        List<TypeInfo> types = new ArrayList<>();
        for (List<String> row : result.rows()) {
            String name = row.isEmpty() ? "" : row.get(0);
            String superName = row.size() > 1 ? row.get(1) : null;
            types.add(new TypeInfo(name, superName == null || superName.isBlank() ? null : superName, List.of()));
        }
        return new TypeDictionary(types);
    }

    @Override
    public byte[] getContent(String id) {
        try {
            Object sys = session.getClass().getMethod("getObject", load("com.documentum.fc.common.IDfId"))
                    .invoke(session, idOf(id));
            Object content = sys.getClass().getMethod("getContent").invoke(sys);
            if (content instanceof byte[] bytes) {
                return bytes;
            }
            return new byte[0];
        } catch (Exception e) {
            throw new SessionException("DFC getContent failed: " + rootMessage(e), e);
        }
    }

    @Override
    public void reset() {
        throw new SessionException("reset is only available on mock DFC");
    }

    @Override
    public void disconnect() {
        try {
            if (session != null) {
                invoke(session, "disconnect");
            }
        } catch (Exception ignored) {
            // ignore
        }
        session = null;
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    private Object idOf(String id) throws Exception {
        Class<?> dfId = load("com.documentum.fc.common.DfId");
        return dfId.getConstructor(String.class).newInstance(id);
    }

    private Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader);
    }

    private static Object invoke(Object target, String method, Object... args) throws Exception {
        if (args.length == 0) {
            return target.getClass().getMethod(method).invoke(target);
        }
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        return target.getClass().getMethod(method, types).invoke(target);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? e.getClass().getSimpleName() : t.getMessage();
    }
}
