package com.helpdesk.user.entity;

/**
 * Account lifecycle state only (05-Database.md's {@code account_status}
 * column; values named in 07-Security-Architecture.md §3.1/§3.8 and
 * 04-API-Design.md's admin status endpoint) — deliberately independent of
 * email verification, which is tracked on {@link User#isEmailVerified()}
 * instead (Milestone 4 design, Change 3). {@code UNVERIFIED}/{@code
 * VERIFIED} collapsed into the single {@code ACTIVE} value that migration
 * introduced: both previously meant "no lifecycle restriction," which is
 * exactly what {@code ACTIVE} now says without conflating it with whether
 * the account's email was ever confirmed.
 */
public enum UserStatus {
    ACTIVE,
    LOCKED,
    DEACTIVATED
}
