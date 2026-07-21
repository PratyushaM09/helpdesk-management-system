package com.helpdesk.user.entity;

/**
 * Account lifecycle state (05-Database.md's {@code account_status} column;
 * values named in 07-Security-Architecture.md §3.1/§3.8 and
 * 04-API-Design.md's admin status endpoint). Only {@code UNVERIFIED},
 * {@code VERIFIED}, and {@code DEACTIVATED} are reachable through this
 * milestone's plain CRUD — {@code LOCKED} is reserved for the Authentication
 * milestone's failed-login lockout mechanism, included now so that milestone
 * needs no schema change.
 */
public enum UserStatus {
    UNVERIFIED,
    VERIFIED,
    LOCKED,
    DEACTIVATED
}
