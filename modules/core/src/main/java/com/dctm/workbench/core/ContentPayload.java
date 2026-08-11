package com.dctm.workbench.core;

public record ContentPayload(String fileName, String mimeType, byte[] bytes) {
}
