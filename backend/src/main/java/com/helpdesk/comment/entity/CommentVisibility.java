package com.helpdesk.comment.entity;

/**
 * PUBLIC is visible to the ticket's creator, assigned agent, and admins;
 * INTERNAL is staff-only (SUPPORT_ENGINEER/ADMIN) - never visible to the
 * USER, enforced by a Service-layer visibility filter (not by this enum).
 */
public enum CommentVisibility {
    PUBLIC,
    INTERNAL
}
