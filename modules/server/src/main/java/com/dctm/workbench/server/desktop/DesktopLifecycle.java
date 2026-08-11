package com.dctm.workbench.server.desktop;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DesktopLifecycle {
    private final boolean desktop;
    private final boolean openBrowser;

    public DesktopLifecycle(
            @Value("${workbench.desktop:false}") boolean desktop,
            @Value("${workbench.open-browser:false}") boolean openBrowser) {
        this.desktop = desktop;
        this.openBrowser = openBrowser;
    }

    @EventListener
    public void onReady(WebServerInitializedEvent event) {
        if (!desktop && !openBrowser) {
            return;
        }
        int port = event.getWebServer().getPort();
        String url = "http://127.0.0.1:" + port + "/";
        if (desktop) {
            DesktopShell.showRunningWindow(url, openBrowser || desktop);
        } else if (openBrowser) {
            DesktopShell.openBrowser(url);
        }
    }
}
