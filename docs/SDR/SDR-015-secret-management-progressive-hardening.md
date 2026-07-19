# SDR-015: Progressive Secret Management (Env Vars Now, Managed Secret Store Later)

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [09-Security-Operations.md §17.1](../09-Security-Operations.md#171-environment-variables--secret-management), [§17.2](../09-Security-Operations.md#172-jwt-signing-key-management)

## Decision
All secrets (database credentials, JWT signing key, future mail/OAuth2 credentials) are injected exclusively via environment variables in this phase, sourced from the deployment platform's native mechanism (CI secret store for pipelines, a gitignored `.env` locally). The application reads only `${ENV_VAR}` placeholders in every `application-*.yml` — never a literal secret — specifically so a future migration to a dedicated secret manager (AWS Secrets Manager, HashiCorp Vault) requires zero application code change.

## Reason
A dedicated secret manager is the ideal end-state (centralized rotation, fine-grained access auditing, automatic injection without the secret ever touching a CI log or a developer's shell history) but is infrastructure this phase's deployment context (SRS §12, C2 — single-team, portfolio-scale, no cloud environment yet provisioned) does not yet have or need. Rather than either (a) skip proper secret handling now and retrofit it later, or (b) over-invest in cloud secret-manager infrastructure before there's a deployment target for it, this decision fixes the *application-facing contract* (secrets are always environment-variable-sourced, never literal) immediately, so the *supply side* of that contract (where the environment variable's value ultimately comes from) can evolve from "manually set" to "Vault-injected" without the application ever needing to change.

## Alternatives Considered
- **Secrets committed directly in `application-prod.yml`, gitignored only at deploy time (i.e., a real prod config file that's just excluded from the public repo):** rejected outright — fragile (one accidental `git add -f` or a misconfigured `.gitignore` and a secret is permanently in history), and doesn't scale to multiple deployers/environments cleanly.
- **Full secret-manager integration (Vault/AWS Secrets Manager) from day one:** rejected for this phase — adds infrastructure dependency and setup cost disproportionate to a system that doesn't yet have a defined cloud deployment target (the AWS roadmap item, [02-Architecture.md §21](../02-Architecture.md#21-future-architecture-roadmap), is explicitly future-phase); the environment-variable contract is deliberately chosen so this isn't a wasted decision, only a deferred one.
- **Encrypted secrets file checked into the repo (e.g., git-crypt/SOPS), decrypted at deploy time:** a reasonable middle-ground alternative. Not selected as the primary approach for this phase, mainly because it still couples secret storage to the application repository itself, whereas environment-variable injection cleanly separates "what secret does the app need" (a name) from "where does its value live" (an external, evolving concern) — but noted as a viable interim step if a team wants stronger-than-`.env` handling before a full secret manager is justified.

## Pros
- Zero-risk-of-accidental-commit by construction — there is no secret value anywhere in the repository to accidentally commit, only variable *names*.
- The eventual Vault/Secrets Manager migration (Section 21 of [10-Security-Assurance.md](../10-Security-Assurance.md#21-future-security-roadmap)) is a deployment-configuration change, not an application code change — directly satisfies the Future-Proof security goal.
- Pre-commit secret scanning (Section 17.1 of [09-Security-Operations.md](../09-Security-Operations.md#171-environment-variables--secret-management)) is a meaningful CI gate specifically *because* the contract guarantees no legitimate reason for a secret-shaped literal to ever appear in a diff.

## Cons
- Environment variables alone (without a secret manager) don't provide fine-grained access auditing ("who read this secret and when") or automatic rotation — accepted as an interim limitation for this phase's scale, explicitly named as the reason a secret manager is on the roadmap rather than dismissed as unnecessary forever.
- Local `.env` files are still a manual, human-managed artifact with some risk of accidental exposure (e.g., an improperly configured `.gitignore`) — mitigated by using clearly non-sensitive, throwaway values in `dev` specifically so a `.env` leak in that environment has no real-world consequence.

## Future Impact
Adopting AWS Secrets Manager (or Vault) is purely a deployment-pipeline change: the CI/CD process resolves `${ENV_VAR}` values by querying the secret manager instead of reading a manually configured environment variable, at the exact same injection point this design already uses — no `application-*.yml` file, and no application code, changes as part of that migration.
