# SDR-001: BCrypt Password Hashing with Cost Factor 12

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [07-Security-Architecture.md §6](../07-Security-Architecture.md#6-password-policy)

## Decision
Store passwords exclusively as BCrypt hashes (Spring Security's `BCryptPasswordEncoder`), cost factor 12 in `prod`, lower in `test` for suite speed. Never store, log, or transmit a reversible or plaintext form at any point after the initial hashing call.

## Reason
Passwords are the highest-value credential in the system and the asset most likely to be targeted in any database-exposure scenario. A purpose-built, adaptive-cost password hash is the accepted industry control (OWASP Password Storage Cheat Sheet) for making an offline brute-force attack against a stolen hash set computationally expensive, while remaining fast enough for interactive login latency.

## Alternatives Considered
- **Fast general-purpose hash (SHA-256/SHA-512), even salted:** rejected — these are deliberately fast, which is exactly the wrong property for password storage; modern GPU/ASIC hardware makes offline brute-forcing a salted-SHA hash set practical at scale.
- **Reversible encryption (AES) of the password:** rejected — there is no legitimate need to ever recover the plaintext password server-side; encryption implies a decryption key exists somewhere, which becomes a new high-value secret to protect and a single point of catastrophic failure if compromised.
- **Argon2id:** a strong, arguably stronger modern alternative (winner of the Password Hashing Competition), seriously considered. Not selected for this phase because BCrypt has broader battle-tested production history within the Spring Security ecosystem specifically and simpler operational tuning (a single cost-factor parameter vs. Argon2's memory/parallelism/time triad). Recorded as a viable future upgrade, not a rejected-forever alternative — Spring Security's `DelegatingPasswordEncoder` supports exactly this kind of algorithm migration with old hashes verified under their original algorithm and re-hashed under the new one on next successful login.

## Pros
- Purpose-built for this exact threat; adaptive cost factor scales with hardware improvements over time.
- Native, well-supported Spring Security integration — no custom cryptographic code to maintain or get wrong.
- Embeds its own cost factor and salt in the stored hash, so a cost-factor increase over time doesn't require a bulk data migration — existing hashes still verify correctly.

## Cons
- Slightly higher CPU cost per login than a fast hash (deliberate, not a defect) — bounded by keeping the cost factor tuned to interactive-latency budgets, monitored as hardware evolves.
- BCrypt truncates input at 72 bytes — not a practical concern given the password policy's length/complexity rules (Section 6 of the SecAD) stay well under that limit, but noted as a known algorithm characteristic.

## Future Impact
A future migration to Argon2id (if justified by an evolving threat landscape or compliance requirement) is a `PasswordEncoder` swap behind Spring Security's delegating-encoder pattern — existing BCrypt hashes remain verifiable and are opportunistically re-hashed under the new algorithm as each user next logs in, requiring no forced mass password reset.
