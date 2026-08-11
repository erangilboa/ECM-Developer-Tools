package com.dctm.workbench.server.api;

import com.dctm.workbench.core.SavedQuery;
import com.dctm.workbench.server.store.ProfileStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/queries")
public class QueryLibraryController {

    private final ProfileStore store;

    public QueryLibraryController(ProfileStore store) {
        this.store = store;
    }

    @GetMapping
    public List<SavedQuery> list() {
        return store.queries();
    }

    @PostMapping
    public SavedQuery save(@RequestBody SavedQuery query) {
        return store.saveQuery(query);
    }
}
