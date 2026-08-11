package com.dctm.workbench.server;

import com.dctm.workbench.server.desktop.DesktopShell;
import java.net.BindException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.PortInUseException;

@SpringBootApplication
public class WorkbenchApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(WorkbenchApplication.class, args);
        } catch (Exception ex) {
            if (isPortInUse(ex)) {
                int port = Integer.getInteger("server.port", 18080);
                boolean desktop = Boolean.parseBoolean(System.getProperty("workbench.desktop", "false"));
                DesktopShell.notifyAlreadyRunning("http://127.0.0.1:" + port + "/", desktop);
                return;
            }
            throw ex;
        }
    }

    static boolean isPortInUse(Throwable ex) {
        while (ex != null) {
            if (ex instanceof PortInUseException || ex instanceof BindException) {
                return true;
            }
            String message = ex.getMessage();
            if (message != null && message.toLowerCase().contains("port") && message.toLowerCase().contains("in use")) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }
}
