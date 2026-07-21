package com.helpdesk.role.entity;

/**
 * The three fixed roles (01-SRS.md §6, 03-Security.md §8). Identifiers match
 * what every security/authorization doc already names — never re-derive a
 * different casing/spelling at the persistence layer.
 */
public enum RoleName {
    USER,
    SUPPORT_ENGINEER,
    ADMIN
}
