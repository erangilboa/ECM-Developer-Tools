package com.dctm.workbench.core;

import java.io.InputStream;
import java.net.URL;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClasspathFixtures {

    private ClasspathFixtures() {
    }

    public static byte[] read(Class<?> owner, String resource, String... fallbacks) {
        URL url = owner.getResource(resource);
        if (url != null) {
            try (InputStream in = url.openStream()) {
                return in.readAllBytes();
            } catch (IOException e) {
                byte[] fallback = readFallbacks(fallbacks);
                if (fallback != null) {
                    return fallback;
                }
                throw new SessionException("Failed to read " + resource + ": " + e.getMessage(), e);
            }
        }
        byte[] fallback = readFallbacks(fallbacks);
        if (fallback != null) {
            return fallback;
        }
        throw new SessionException("Missing classpath resource " + resource);
    }

    private static byte[] readFallbacks(String... fallbacks) {
        Path cwd = Path.of("").toAbsolutePath();
        for (String rel : fallbacks) {
            Path[] candidates = {
                    cwd.resolve(rel),
                    cwd.getParent() != null ? cwd.getParent().resolve(rel) : null,
                    cwd.resolve("modules").resolve(rel)
            };
            for (Path path : candidates) {
                if (path == null) {
                    continue;
                }
                try {
                    if (Files.isRegularFile(path)) {
                        return Files.readAllBytes(path);
                    }
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        return null;
    }
}
