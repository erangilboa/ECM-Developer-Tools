package com.dctm.workbench.server.api;

import com.dctm.workbench.core.Capability;
import com.dctm.workbench.core.UnsupportedCapabilityException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stubs")
public class StubController {

    @GetMapping("/{module}")
    public Map<String, Object> describe(@PathVariable String module) {
        return switch (module) {
            case "scriptrunner" -> stub("ScriptRunner", "DQL, IAPI and JavaScript chaining with custom JARs",
                    Capability.IAPI);
            case "iapi" -> stub("IAPI console", "Use mock/live DFC session POST /api/sessions/{id}/iapi for a thin REPL",
                    Capability.IAPI);
            case "rest-explorer" -> stub("DCTM REST explorer", "HAL-aware request builder", Capability.DQL_SELECT);
            case "dfs" -> stub("DFS explorer", "SOAP operations against a DFS endpoint", Capability.DFS_INVOKE);
            case "cws" -> stub("CWS explorer", "Content Web Services (OTCS SOAP)", Capability.CWS_INVOKE);
            case "acl" -> stub("ACL browser", "Permission sets and objects using an ACL", Capability.ACL_READ);
            case "users" -> stub("Users and groups", "Including group member structure", Capability.USER_ADMIN);
            case "workflows" -> stub("Workflow monitor", "Filtered list and live progress", Capability.WORKFLOW);
            case "otds-sso" -> stub("OTDS SSO", "Browser login / login tickets", Capability.OTDS_AUTH);
            case "ecmlink" -> stub("ECMLink create workspace", "Will not provision SAP-linked workspaces in MVP",
                    Capability.BUSINESS_WORKSPACE);
            default -> stub(module, "Not implemented", Capability.BROWSE);
        };
    }

    @PostMapping("/{module}")
    public void invoke(@PathVariable String module) {
        describe(module);
        throw new UnsupportedCapabilityException(Capability.BROWSE, module + " is stubbed");
    }

    private static Map<String, Object> stub(String title, String summary, Capability capability) {
        return Map.of(
                "stub", true,
                "title", title,
                "summary", summary,
                "capability", capability.name()
        );
    }
}
