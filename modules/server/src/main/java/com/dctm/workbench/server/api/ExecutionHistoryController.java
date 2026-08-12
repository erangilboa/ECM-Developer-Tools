package com.dctm.workbench.server.api;

import com.dctm.workbench.core.ExecutionHistoryEntry;
import com.dctm.workbench.core.Product;
import com.dctm.workbench.server.store.ProfileStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/execution-history")
public class ExecutionHistoryController {

    private final ProfileStore store;

    public ExecutionHistoryController(ProfileStore store) {
        this.store = store;
    }

    @GetMapping
    public List<ExecutionHistoryEntry> list(@RequestParam(required = false) Product product) {
        return store.executionHistory(product);
    }

    @PostMapping
    public ExecutionHistoryEntry append(@RequestBody ExecutionHistoryEntry entry) {
        return store.appendExecutionHistory(entry);
    }
}
