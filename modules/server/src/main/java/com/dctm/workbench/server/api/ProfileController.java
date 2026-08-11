package com.dctm.workbench.server.api;

import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.server.store.ProfileStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileStore store;

    public ProfileController(ProfileStore store) {
        this.store = store;
    }

    @GetMapping
    public List<ConnectionProfile> list() {
        return store.list();
    }

    @PostMapping
    public ConnectionProfile save(@RequestBody Dto.ProfileSave body) {
        return store.save(body.profile(), body.secret());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        store.delete(id);
    }
}
