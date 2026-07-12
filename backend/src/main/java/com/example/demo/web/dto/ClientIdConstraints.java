package com.example.demo.web.dto;

/**
 * Validation pattern for the X-Client-Id header. It is client-supplied and unauthenticated
 * (see CLAUDE.md), so this only rejects malformed values early — it does not prove ownership.
 */
public final class ClientIdConstraints {
    public static final String UUID_REGEX =
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private ClientIdConstraints() {}
}
