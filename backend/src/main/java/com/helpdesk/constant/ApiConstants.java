package com.helpdesk.constant;

/**
 * API-wide, infrastructure-level constants — never a business value
 * (ticket statuses, category names, etc. belong to their own module once
 * it exists, per 11-Development-Rules.md §2.1).
 */
public final class ApiConstants {

    /** Matches ADR-0012 (URI-based API versioning) — {@code /api/v1/...}. */
    public static final String API_VERSION = "v1";

    public static final String API_BASE_PATH = "/api/" + API_VERSION;

    private ApiConstants() {
    }
}
