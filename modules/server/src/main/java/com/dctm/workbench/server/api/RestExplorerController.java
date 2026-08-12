package com.dctm.workbench.server.api;

import com.dctm.workbench.core.OtcsRepositorySession;
import com.dctm.workbench.core.RestCapable;
import com.dctm.workbench.core.RestProxyRequest;
import com.dctm.workbench.core.RestProxyResponse;
import com.dctm.workbench.core.RepositorySession;
import com.dctm.workbench.core.SessionRestProxy;
import com.dctm.workbench.server.session.SessionRegistry;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{id}/rest")
public class RestExplorerController {

    private final SessionRegistry registry;

    public RestExplorerController(SessionRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/proxy")
    public RestProxyResponse proxy(@PathVariable String id, @RequestBody RestProxyRequest body) {
        RepositorySession session = registry.require(id).session();
        RestCapable capable = restCapable(session);
        return capable.restProxy(body);
    }

    private static RestCapable restCapable(RepositorySession session) {
        if (session instanceof RestCapable capable) {
            return capable;
        }
        if (session instanceof OtcsRepositorySession otcs && otcs.bridge() instanceof RestCapable capable) {
            return capable;
        }
        return new SessionRestProxy(session);
    }
}
