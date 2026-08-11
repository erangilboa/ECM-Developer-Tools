package com.dctm.workbench.server.api;

import com.dctm.workbench.core.BrowseFilter;
import com.dctm.workbench.core.BrowseNode;
import com.dctm.workbench.core.BusinessWorkspace;
import com.dctm.workbench.core.ConnectionProfile;
import com.dctm.workbench.core.ContentPayload;
import com.dctm.workbench.core.DocumentumSession;
import com.dctm.workbench.core.DqlRequest;
import com.dctm.workbench.core.DqlResult;
import com.dctm.workbench.core.FolderContents;
import com.dctm.workbench.core.IapiResult;
import com.dctm.workbench.core.JobDetail;
import com.dctm.workbench.core.JobFilter;
import com.dctm.workbench.core.JobList;
import com.dctm.workbench.core.ObjectDump;
import com.dctm.workbench.core.OtcsSession;
import com.dctm.workbench.core.QueryMode;
import com.dctm.workbench.core.RepositorySession;
import com.dctm.workbench.core.SearchRequest;
import com.dctm.workbench.core.SearchResult;
import com.dctm.workbench.core.SessionException;
import com.dctm.workbench.core.TypeDictionary;
import com.dctm.workbench.server.config.CompositeSessionFactory;
import com.dctm.workbench.server.session.SessionRegistry;
import com.dctm.workbench.server.store.ProfileStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final ProfileStore profiles;
    private final CompositeSessionFactory sessions;
    private final SessionRegistry registry;

    public SessionController(ProfileStore profiles, CompositeSessionFactory sessions, SessionRegistry registry) {
        this.profiles = profiles;
        this.sessions = sessions;
        this.registry = registry;
    }

    @PostMapping("/sessions")
    public Dto.SessionView connect(@RequestBody Dto.ConnectBody body) {
        ConnectionProfile profile = profiles.get(body.profileId())
                .orElseThrow(() -> new SessionException("Unknown profile"));
        char[] secret = body.secret() != null ? body.secret().toCharArray() : profiles.secretFor(profile);
        RepositorySession session = sessions.connect(profile, secret);
        SessionRegistry.Handle handle = registry.put(profile, session);
        return Dto.SessionView.of(handle.id(), profile, session.serverInfo());
    }

    @GetMapping("/sessions/{id}")
    public Dto.SessionView get(@PathVariable String id) {
        SessionRegistry.Handle handle = registry.require(id);
        return Dto.SessionView.of(handle.id(), handle.profile(), handle.session().serverInfo());
    }

    @DeleteMapping("/sessions/{id}")
    public void close(@PathVariable String id) {
        registry.close(id);
    }

    @GetMapping("/sessions/{id}/browse/roots")
    public List<BrowseNode> roots(@PathVariable String id) {
        return registry.require(id).session().listRoots();
    }

    @GetMapping("/sessions/{id}/browse/{nodeId}/children")
    public FolderContents children(@PathVariable String id, @PathVariable String nodeId,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(required = false) Integer subtype) {
        return registry.require(id).session().listChildren(nodeId, new BrowseFilter(q, subtype));
    }

    @GetMapping("/sessions/{id}/objects/{objectId}/dump")
    public ObjectDump dump(@PathVariable String id, @PathVariable String objectId) {
        return registry.require(id).session().dump(objectId);
    }

    @PutMapping("/sessions/{id}/objects/{objectId}/dump")
    public void saveDump(@PathVariable String id, @PathVariable String objectId, @RequestBody ObjectDump dump) {
        ObjectDump withId = new ObjectDump(objectId, dump.typeName(), dump.objectName(), dump.attributes(),
                dump.categories(), dump.extra(), dump.sapLinked());
        registry.require(id).session().saveDump(withId);
    }

    @GetMapping("/sessions/{id}/objects/{objectId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String id, @PathVariable String objectId,
                                          @RequestParam(defaultValue = "attachment") String disposition) {
        ContentPayload payload = registry.require(id).session().getContent(objectId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(payload.mimeType() == null || payload.mimeType().isBlank()
                    ? "application/octet-stream" : payload.mimeType());
            if ("text".equalsIgnoreCase(mediaType.getType())) {
                mediaType = new MediaType(mediaType, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String fileName = payload.fileName() == null || payload.fileName().isBlank() ? "content" : payload.fileName();
        fileName = fileName.replace("\"", "");
        String disp = "inline".equalsIgnoreCase(disposition) ? "inline" : "attachment";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disp + "; filename=\"" + fileName + "\"")
                .contentType(mediaType)
                .body(payload.bytes() == null ? new byte[0] : payload.bytes());
    }

    @PostMapping("/sessions/{id}/dql")
    public DqlResult dql(@PathVariable String id, @RequestBody Dto.DqlBody body) {
        DocumentumSession session = dctm(id);
        QueryMode mode = "EXEC".equalsIgnoreCase(body.mode()) ? QueryMode.EXEC : QueryMode.READ;
        return session.executeDql(new DqlRequest(body.dql(), mode, body.maxRows() <= 0 ? 500 : body.maxRows()));
    }

    @PostMapping("/sessions/{id}/search")
    public SearchResult search(@PathVariable String id, @RequestBody Dto.SearchBody body) {
        return registry.require(id).session().search(new SearchRequest(body.query(), body.limit() <= 0 ? 100 : body.limit()));
    }

    @GetMapping("/sessions/{id}/jobs")
    public JobList jobs(@PathVariable String id, @RequestParam(required = false) String q) {
        JobFilter filter = new JobFilter(q, null);
        RepositorySession session = registry.require(id).session();
        if (session instanceof DocumentumSession dctm) {
            return dctm.listJobs(filter);
        }
        if (session instanceof OtcsSession otcs) {
            return otcs.listJobs(filter);
        }
        throw new SessionException("Jobs are not available on this session.");
    }

    @GetMapping("/sessions/{id}/jobs/{jobId}")
    public JobDetail job(@PathVariable String id, @PathVariable String jobId) {
        RepositorySession session = registry.require(id).session();
        if (session instanceof DocumentumSession dctm) {
            return dctm.getJob(jobId);
        }
        if (session instanceof OtcsSession otcs) {
            return otcs.getJob(jobId);
        }
        throw new SessionException("Jobs are not available on this session.");
    }

    @PostMapping("/sessions/{id}/jobs/{jobId}/run")
    public void runJob(@PathVariable String id, @PathVariable String jobId) {
        RepositorySession session = registry.require(id).session();
        if (session instanceof DocumentumSession dctm) {
            dctm.runJob(jobId);
            return;
        }
        if (session instanceof OtcsSession otcs) {
            otcs.runJob(jobId);
            return;
        }
        throw new SessionException("Jobs are not available on this session.");
    }

    @GetMapping("/sessions/{id}/types")
    public TypeDictionary types(@PathVariable String id) {
        return dctm(id).types();
    }

    @PostMapping("/sessions/{id}/iapi")
    public IapiResult iapi(@PathVariable String id, @RequestBody Dto.IapiBody body) {
        return dctm(id).iapi(body.command());
    }

    @GetMapping("/sessions/{id}/workspaces")
    public List<BusinessWorkspace> workspaces(@PathVariable String id) {
        return otcs(id).listBusinessWorkspaces();
    }

    @GetMapping("/sessions/{id}/workspaces/{wsId}")
    public BusinessWorkspace workspace(@PathVariable String id, @PathVariable String wsId) {
        return otcs(id).getWorkspace(wsId);
    }

    @PostMapping("/sessions/{id}/reset-mock")
    public void reset(@PathVariable String id) {
        RepositorySession session = registry.require(id).session();
        if (session instanceof DocumentumSession dctm) {
            dctm.resetMock();
        } else if (session instanceof OtcsSession otcs) {
            otcs.resetMock();
        } else {
            throw new SessionException("Reset is only available on mock sessions");
        }
    }

    @PostMapping("/sessions/{id}/checkout/{objectId}")
    public void checkout(@PathVariable String id, @PathVariable String objectId) {
        dctm(id).checkout(objectId);
    }

    @PostMapping("/sessions/{id}/checkin/{objectId}")
    public void checkin(@PathVariable String id, @PathVariable String objectId) {
        dctm(id).checkin(objectId);
    }

    private DocumentumSession dctm(String id) {
        RepositorySession session = registry.require(id).session();
        if (session instanceof DocumentumSession dctm) {
            return dctm;
        }
        throw new SessionException("This session is not Documentum. DQL/IAPI are Documentum-only.");
    }

    private OtcsSession otcs(String id) {
        RepositorySession session = registry.require(id).session();
        if (session instanceof OtcsSession otcs) {
            return otcs;
        }
        throw new SessionException("This session is not Extended ECM. Business Workspaces are OTCS-only.");
    }
}
