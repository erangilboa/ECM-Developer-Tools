package com.dctm.workbench.server.web;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

@Controller
public class SpaController {

    @GetMapping(value = {"/", "/workbench", "/workbench/**"})
    public ResponseEntity<Resource> index() {
        ClassPathResource page = new ClassPathResource("static/index.html");
        if (page.exists()) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(page);
        }
        String html = """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><title>ECM-Dev-Workbench</title></head>
                <body style="font-family:Segoe UI,sans-serif;background:#12161f;color:#e7ebf4;padding:48px;max-width:40rem">
                  <h1>UI not packaged</h1>
                  <p>The API is running, but the web UI is not on the classpath.</p>
                  <ul>
                    <li>From source: <code>gradlew.bat :server:bootRun</code> (builds the UI first), or open
                      <a href="http://localhost:5173">http://localhost:5173</a> while this server is up.</li>
                    <li>Installed app: quit this Java process and launch <strong>ECM-Dev-Workbench</strong>.</li>
                  </ul>
                </body>
                </html>
                """;
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8)));
    }
}
