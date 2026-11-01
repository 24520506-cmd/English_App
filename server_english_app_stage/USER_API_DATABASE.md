# User API database design

**Version:** 0.1 | **Owner:** Backend Engineering | **Date:** 2026-10-26 | **Status:** Draft implementation

## Goal

Keep `app_user.user_id` stable while adding durable state for recovery, contact verification, Google linking, audit and idempotency. Only the User API is in scope.

## V1–V3 gap analysis

| Requirement | Existing schema | Gap in V4–V6 |
|---|---|---|
| ACC-01–04, VER-03/09 | `app_user` UUID, contacts, verified timestamps; V3 display name | Role/status/contact checks, pending candidate, shared verified-contact ownership |
| AUTH-01–04 | `login_failure`, `login_ip_request` | Security event audit and indexes |
| SES-01–11 | `user_session`, `retired_refresh_token` | Token lineage, time/revocation checks, account-first lock protocol |
| VER-04–08, REC-01–05 | None | Purpose-bound OTP/send ledger, reset grant, reauth proof, pending change |
| VER-01–03/10 | None | Unique Google `sub`, OAuth state/nonce/PKCE storage |
| API-06 | None | Scoped idempotency receipts with at least 24-hour expiry |

## Relations

```text
app_user (user_id PK)
  ├── user_session (session_id PK) ── retired_refresh_token (token_hash PK)
  ├── otp_challenge (challenge_id PK) ── otp_send_attempt (send_id PK)
  ├── password_reset_grant (grant_id PK, challenge_id FK)
  ├── pending_contact_change (change_id PK)
  ├── reauth_proof (proof_id PK)
  ├── google_identity (google_sub PK)
  ├── verified_contact_claim (kind, normalized value PK)
  ├── google_link_attempt (attempt_id PK)
  ├── security_event (event_id PK)
  └── idempotency_receipt (user_id, endpoint, idempotency_key PK)
```

Current email/phone remain on `app_user`; a candidate lives separately until proof succeeds. Verified current contacts have partial unique indexes and a shared `verified_contact_claim` registry synchronized with `google_identity` by triggers. Unverified contacts may be shared and recovery must disambiguate by login identifier. Google `sub` is globally unique. The existing service creates learners only; admin provisioning is operational.

## Transaction and security protocol

Lock `app_user` first, then the relevant session, challenge, grant, change or OAuth row. Recheck expiry, owner, purpose, contact and consumed/revoked state after locking. Refresh, revoke-all, password change, reset and contact completion use this order. A reset consumes the grant, invalidates all recovery challenges and grants, and revokes all sessions in one transaction. `start_contact_change` consumes a fresh session-bound reauthentication proof and rejects an already claimed candidate; completion checks ownership again, replaces the current contact, invalidates old-contact challenges, and revokes all sessions. V5/V6 database routines implement these transitions.

The application computes OTP HMAC with a server-held key; PostgreSQL sees only the hex digest. It stores only a reset-grant hash. PKCE verifier must be encrypted by the application before insertion; the encryption key stays outside PostgreSQL. Provider issuer/audience/signature/nonce checks stay in the application.

`reserve_otp_send` locks the account and transaction advisory locks for canonical contact and IP, checks indexed `otp_send_attempt` rows over the rolling hour, enforces at least 60 seconds since last send, then records the send. It caps sends at five per account and contact across channels and accepts an IP limit from the service. `resend_user_otp` rotates the HMAC and starts a new five minute life in the same transaction; it retains the wrong-attempt counter and hourly send ledger. The service must use canonical contact values, choose the IP policy, and deliver the message.

Idempotency receipts are scoped by user, endpoint and UUID key. Body SHA-256 mismatches yield `409`; matching retries return the stored response. Credential-bearing responses must be encrypted before storage. The proposed minimum retention is 24 hours; purge and longer audit retention await DEC-U07.

## Traceability

| HTTP contract | Persistent state | Status of HTTP code |
|---|---|---|
| API-U01 | `app_user` / stable UUID | Existing |
| API-U02 | `login_failure`, `login_ip_request`, `user_session`, `security_event` | Existing, audit/locking extended |
| API-U03 | `user_session`, `retired_refresh_token`, `security_event` | Existing, lineage/locking extended |
| API-U04–U05 | `user_session`, `security_event` | Existing |
| API-U06–U07 | `app_user`, `pending_contact_change` | Existing current profile only; pending state not yet wired |
| API-U08–U11 | `app_user`, `user_session`, `security_event` | Existing |
| API-U12–U15 | `otp_challenge`, `otp_send_attempt`, `password_reset_grant` | Schema/routines added; HTTP not yet implemented |
| API-U16–U18 | `otp_challenge`, `otp_send_attempt`, `app_user.phone_verified_at` | Schema/routines added; HTTP not yet implemented |
| API-U19–U20 | `reauth_proof`, `pending_contact_change`, verified claims | Schema/routines added; HTTP not yet implemented |
| API-U21–U22 | `reauth_proof`, `google_link_attempt`, `google_identity`, verified claims | Schema added; provider/HTTP not yet implemented |
| API-U23 | `user_session.last_activity_at` | Existing |

| Requirement / acceptance | Schema / transition | Integration test |
|---|---|---|
| ACC-01–04, AC-USR-19–21 | Stable `app_user.user_id`, independent contacts | `legacyUpgradeAndConstraints` |
| SES-01–11, AC-USR-03/23 | Session/token lineage, account lock | `refreshAndRevokeAllRace`, `logoutAndSingleRevokeDoNotDeadlockWithRevokeAll`, `refreshReuseRevokesSessionAndWritesSecurityEvent` |
| VER-04–06, REC-03–04, AC-USR-08/17/26 | OTP challenge, send ledger, `consume_user_otp` | `concurrentOtpConsume`, `otpPurposeAndFifthWrongAttemptAreEnforced`, `concurrentOtpSendRespectsHourlyLimit`, `resendReplacesOldOtpWithoutResettingHourlyLedger`, `blockedResendCannotReviveExpiredChallenge` |
| REC-05, AC-USR-18 | Reset grant, `complete_password_reset` | `concurrentReset` |
| VER-03/08/10, AC-USR-10/24 | Pending change, verified unique indexes/claims, `start_contact_change`, `complete_contact_change`, Google identity | `contactStartRequiresFreshOneTimeReauthAndChecksExistingClaim`, `concurrentContactClaim`, `blockedContactCompletionCannotPassExpiredPendingChange`, `legacyUpgradeAndConstraints` |
| VER-01–03, API-U21–22 | Reauth proof, OAuth attempt, Google identity | Database constraints; provider checks remain untested |
| API-06 | Idempotency receipt | Database constraints; HTTP replay remains unimplemented |

## Open decisions and limits

| ID | Status | Working assumption |
|---|---|---|
| DEC-U02 | Open | Keep existing username/password/display-name validation. This schema requires E.164 for a **verified** phone and lowercase canonical email for new proof records; existing unverified local phone values remain untouched and need normalization or replacement before verification. Exact formats need Product Owner approval. |
| DEC-U05 | Open | Schema can represent email candidate proof by OTP or Google, but does not choose non-Gmail delivery or Gmail sequence. A saved email is never proof. |
| DEC-U07 | Open | Apply draft API-06 minimum 24-hour receipt retention; agree audit/receipt purge and privacy policy before production. |
| API-06 | Draft contract | Idempotency semantics are proposed, not a Product Owner approved rule. |

V4–V6 provide persistence and atomic database operations. API-U12–U22 controllers, OTP delivery, Google provider verification, and HTTP idempotency replay are later service work. Existing API-U01–U11 and U23 remain the HTTP slice. Existing duplicate verified contacts would make V4/V6 stop safely for manual remediation; the migration never chooses an owner or deletes data.

## Verification record

`mvn test -DtrimStackTrace=false` compiled the project: 20 tests discovered, two unit tests passed, 18 PostgreSQL integration tests skipped because `TEST_DATABASE_URL` was not set. Disposable PostgreSQL 16.15 clusters under `/tmp` accepted V1→V6 SQL and a separate V3→V6 upgrade with a preserved legacy user/session; the old access expiry was clamped to its absolute expiry. SQL smoke checks showed one-time OTP, resend, reset and contact completion in sequence, contact-start reauthentication, plus a cross-table Google email claim rejection. The workspace sandbox prevents TCP and Unix socket binding, so Flyway over JDBC and concurrent integration tests remain unverified here. Run `TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/<disposable-db> TEST_DATABASE_USER=<user> TEST_DATABASE_PASSWORD=<password> mvn test` in an environment with PostgreSQL socket access. Do not point this at the active `english_app` database.
