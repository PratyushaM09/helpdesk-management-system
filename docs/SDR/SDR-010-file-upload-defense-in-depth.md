# SDR-010: File Upload Defense in Depth (Magic-Byte Validation + Virus Scanning)

**Status:** Accepted
**Date:** 2026-07-19
**Related:** [08-Security-Controls.md §11](../08-Security-Controls.md#11-file-upload-security), ADR-0008

## Decision
Validate every uploaded file at three independent points before it is durably stored: (1) declared `Content-Type`/extension against the allow-list (a cheap, immediate rejection for the common case), (2) actual file-content magic-byte signature against the same allow-list (the authoritative check — closes spoofed-extension/type attacks), and (3) a virus/malware scan (ClamAV via daemon-socket integration) — all three must pass, and storage uses a randomly generated key never derived from client input.

## Reason
File Upload Attacks (threat #10) is one of the highest-impact vectors available to any authenticated user in this system (every role can upload attachments), because a successful malicious upload could later be downloaded and opened by a different, potentially higher-privileged user (e.g., a `USER`-uploaded attachment later opened by a `SUPPORT_ENGINEER` or `ADMIN` reviewing the ticket) — making this a realistic path to a client-side compromise that doesn't require finding a web-application vulnerability at all. A single check (type validation alone) is insufficient because MIME type and extension are both attacker-controlled and easily spoofed; a scan alone is insufficient because it doesn't stop non-malware-but-still-inappropriate file types (an `.exe` isn't necessarily flagged as a known virus signature but should never be accepted regardless).

## Alternatives Considered
- **Extension/MIME-type check only, no content sniffing:** rejected — trivially bypassed by renaming a malicious file's extension or spoofing the `Content-Type` header, both fully within an attacker's control on a client-supplied upload.
- **Virus scanning only, no type allow-list:** rejected — a scanner only catches *known* malware signatures; it does nothing to prevent upload of an unwanted-but-not-malware-flagged executable, script, or macro-enabled document, all of which are excluded by this system's allow-list regardless of scan result.
- **No virus scanning (type/size validation only):** seriously considered as a simpler `dev`-friendly design, since content-type/magic-byte validation alone closes most of the structural attack surface (executables, scripts). Rejected as the final `prod` posture because it leaves zero-day/known-signature malware embedded *within* an otherwise-allow-listed format (e.g., a malicious macro-free PDF exploit, or a ZIP containing something never meant to be extracted safely) undetected — accepted instead as the interim `dev`-profile behavior (Section 17.6 of [09-Security-Operations.md](../09-Security-Operations.md#176-profiles)) via a no-op scanner implementation, with real scanning mandatory and fail-closed in `test`/`prod`.

## Pros
- Three independent layers mean a bypass of any single one (a novel content-sniffing edge case, a scanner signature gap) still leaves the others in place — direct application of the Defense in Depth goal.
- Randomized storage keys eliminate path traversal (threat #11) structurally, independent of the type/scan checks.
- Fail-closed scanning in `prod` (scan-unavailable ⇒ reject) means a scanning-infrastructure outage degrades to "uploads temporarily unavailable," never to "uploads silently unscanned."

## Cons
- Adds a runtime dependency (ClamAV or equivalent) to the `prod`/`test` deployment topology, and adds upload latency for the scan step — accepted as proportionate given the threat's severity and the low latency cost of a well-provisioned scanning daemon for the file sizes this system allows (10 MB cap, Section 11.3 of [08-Security-Controls.md](../08-Security-Controls.md#113-maximum-size)).
- The `AttachmentScanner` abstraction (interface-based, per Section 11.4 of [08-Security-Controls.md](../08-Security-Controls.md#114-virus-scanning-strategy)) must be kept genuinely swappable/no-op for `dev`, or local development friction would create pressure to weaken the `prod` control over time — an explicit design discipline, not just an implementation detail.

## Future Impact
The scanner abstraction (an interface, not a hardcoded ClamAV call) allows a future swap to a cloud-based scanning service (e.g., as part of the AWS roadmap item, [02-Architecture.md §21](../02-Architecture.md#21-future-architecture-roadmap)) without touching the upload flow's business logic — a second `AttachmentScanner` implementation, selected by configuration, exactly mirroring the `FileStorageService` extensibility pattern already established by ADR-0008.
