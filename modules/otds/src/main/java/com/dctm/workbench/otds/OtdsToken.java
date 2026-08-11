package com.dctm.workbench.otds;

public record OtdsToken(
        String accessToken,
        String tokenType,
        String otdsTicket,
        long expiresInSeconds
) {
    public String bearerHeader() {
        String type = tokenType == null || tokenType.isBlank() ? "Bearer" : tokenType;
        return type + " " + accessToken;
    }
}
