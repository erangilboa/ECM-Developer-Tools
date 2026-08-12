package com.dctm.workbench.server.api;

import com.dctm.workbench.core.Product;
import com.dctm.workbench.core.QueryHistoryEntry;
import com.dctm.workbench.server.store.ProfileStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/query-history")
public class QueryHistoryController {

    private final ProfileStore store;

    public QueryHistoryController(ProfileStore store) {
        this.store = store;
    }

    @GetMapping
    public List<QueryHistoryEntry> list(@RequestParam(required = false) Product product) {
        return store.queryHistory(product);
    }

    @PostMapping
    public QueryHistoryEntry append(@RequestBody QueryHistoryBody body) {
        return store.appendQueryHistory(body.text(), body.product());
    }

    public record QueryHistoryBody(String text, Product product) {
    }
}
