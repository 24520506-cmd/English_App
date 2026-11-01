# User REST API contract — draft for backend and native Android client

**Version:** 0.1  
**Date:** 2026-10-25  
**Owner:** Product/Engineering  
**Status:** Draft contract; User API partly implemented in Spring Boot and Android Retrofit

## 1. Purpose and authority

The goal is to let the Java Android app authenticate a learner, restore the same session after process death, and manage that learner's account without relying on prototype navigation. Success means the backend and Retrofit client implement the same requests, responses, error states, and session rules, then pass `AC-USR-01`–`AC-USR-27` in [product-spec.md](product-spec.md). The backend currently implements `API-U01`–`API-U11` and `API-U23`; recovery and contact/provider verification remain open. Backend PostgreSQL integration tests have been written but have not yet run against a reachable database.

The product spec is authoritative for business behavior. Paths, field names, status codes, and JSON examples in this document are **proposed interface choices** because no backend implementation or existing endpoint contract is present in this workspace. If a proposed interface conflicts with a product rule, the product rule wins. This contract covers `USR`, `ACC`, `AUTH`, `SES`, `VER`, and `REC`; onboarding preferences and public ranking names have their own API domain.

The current app is native Android Java with XML layouts. `LoginActivity` currently navigates to Home without a request, and `RegisterActivity` navigates to onboarding without saving an account. Those are UI placeholders, not API behavior.

## 2. Common HTTP conventions

| ID | Rule | Priority | Source |
|---|---|---|---|
| API-01 | Base path is `/api/v1`. All production traffic uses HTTPS and JSON UTF-8, except the Google redirect callback. | Must | Proposed interface |
| API-02 | Authenticated calls use `Authorization: Bearer <access_token>`. The server derives `user_id`, role, and session from the validated token and live session record, never a client supplied owner ID. | Must | `SES-03`–`SES-04`, `USR-08` |
| API-03 | `access_token` expires 15 minutes after issue. Refresh rotates its token on every successful call; session idle and absolute limits are those in `SES-08`. Tokens are opaque to UI code. | Must | `SES-08`–`SES-15` |
| API-04 | All timestamps are ISO 8601 UTC strings such as `2026-10-25T12:00:00Z`. IDs are opaque strings; clients do not infer account or session details from them. | Must | Proposed interface |
| API-05 | Success responses contain the resource directly. Errors use the envelope in §4, with a stable `code` for app behavior and a Vietnamese `message` for display. Passwords, OTPs, and tokens never appear in logs or error responses; tokens appear only in the intended success response. | Must | `AUTH-02`–`AUTH-04`, `VER-04`, `REC-03` |
| API-06 | Mutating requests that can be retried after a lost response accept `Idempotency-Key` (UUID string). A repeated key with the same authenticated user, endpoint, and body returns the original result; a changed body returns `409 IDEMPOTENCY_KEY_REUSED`. The server retains keys for at least 24 hours. | Must | Proposed control for `SES-12`, `REC-05` |
| API-07 | For an endpoint with an active session, `401 SESSION_EXPIRED`, `SESSION_REVOKED`, or `SESSION_INVALID` means reauthentication; `401 REFRESH_REUSE_DETECTED` means a separate security message. Network/5xx errors do not prove that the session ended. | Must | `SES-11`–`SES-16` |

The `Idempotency-Key` retention period is an interface proposal, not a business rule already approved. A refresh request is **not** generically retried: after an uncertain refresh result, replaying the old token risks the reuse response under `SES-11`.

## 3. Shared representations

### 3.1 Account and session

```json
{
  "user_id": "usr_7f82",
  "username": "minh_learner",
  "role": "learner",
  "status": "active",
  "profile": { "display_name": "Minh" },
  "contacts": {
    "email": { "masked_value": "m***@gmail.com", "verified_at": null },
    "phone": null,
    "pending_change": null
  }
}
```

`user_id` is stable across sessions and contact changes. `role` is `learner` or `admin`; registration can create only `learner`. `contacts.email` and `.phone` are `null` if never saved; otherwise they expose a masked value and a nullable verification timestamp. A pending contact is shown separately and never replaces the current contact until confirmed. The public ranking name is not `profile.display_name`.

```json
{
  "session_id": "ses_d04b",
  "device_label": "Samsung A54",
  "platform": "android",
  "created_at": "2026-10-25T12:00:00Z",
  "last_activity_at": "2026-10-25T12:00:00Z",
  "idle_expires_at": "2026-11-01T12:00:00Z",
  "absolute_expires_at": "2026-11-24T12:00:00Z",
  "is_current": true
}
```

Only live sessions appear in the session list. A device label is descriptive, not proof of device identity. `last_activity_at` advances only for authenticated active interactions defined in `SES-09`–`SES-10`; refresh, polling, push, and merely opening the app do not advance it. Admin idle/absolute limits are 30 minutes/8 hours; learner limits are 7 days/30 days.

### 3.2 Token response

```json
{
  "access_token": "opaque-access-token",
  "access_expires_at": "2026-10-25T12:15:00Z",
  "refresh_token": "opaque-refresh-token",
  "session": { "session_id": "ses_d04b", "absolute_expires_at": "2026-11-24T12:00:00Z" },
  "user": { "user_id": "usr_7f82", "role": "learner" }
}
```

Token strings above are illustrative. Login and refresh return this shape over HTTPS with `Cache-Control: no-store`. The Android app keeps the access token in memory and the refresh token in secure device storage excluded from backup. The app must not put tokens in `SharedPreferences` without encryption, logs, intents, or saved Activity state. The product spec names Expo SecureStore, which does not apply to this native Java project; the storage implementation needs a native Android choice while preserving `SES-13`'s security intent.

## 4. Error contract

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Vui lòng kiểm tra thông tin đã nhập.",
    "fields": { "username": "Giá trị không hợp lệ." },
    "retry_after_at": null,
    "request_id": "req_1f8a"
  }
}
```

`fields` and `retry_after_at` are omitted when irrelevant. `request_id` is for support and contains no secret. No endpoint returns an OTP, password, refresh hash, or another user's account data.

| HTTP | Stable code | Client action |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Show field errors; keep entered values except passwords when leaving the flow. |
| 401 | `INVALID_CREDENTIALS` | Show exactly “Thông tin đăng nhập không hợp lệ” for unknown account, wrong password, lock, or login rate limit. The FE code, message, and response length must not distinguish these causes. A generic `retry_after_at` is allowed only when it does not disclose account status. |
| 401 | `SESSION_EXPIRED`, `SESSION_REVOKED`, `SESSION_INVALID` | Clear local tokens and ask for login. |
| 401 | `REFRESH_REUSE_DETECTED` | Clear local tokens and show the separate security state. |
| 403 | `FORBIDDEN` | Do not show or permit the protected operation. |
| 404 | `NOT_FOUND` | Use for a session that is not owned or is not visible, without exposing its owner. |
| 409 | `CONTACT_ALREADY_LINKED`, `IDEMPOTENCY_KEY_REUSED`, `STATE_CONFLICT` | Show an action specific message; never merge accounts automatically. |
| 410 | `CHALLENGE_EXPIRED`, `CHALLENGE_USED` | Restart the applicable verification step. |
| 422 | `OTP_INVALID`, `OTP_ATTEMPTS_EXHAUSTED` | Keep the same account flow; after the fifth failure require a new challenge. |
| 429 | `RATE_LIMITED` | Show retry time for OTP/contact requests; login still uses `INVALID_CREDENTIALS` per `AUTH-02`. |
| 503 | `DELIVERY_UNAVAILABLE` | Keep contact unverified or reset unfinished; retry under the existing limits. |

For password recovery **before OTP verification**, do not expose whether an account or contact exists. The same generic accepted response is returned for valid and invalid identity input. Codes for wrong OTP are returned only after a challenge has been established. All authorization checks and rate limits run in the backend.

## 5. Endpoint inventory

All paths below are relative to `/api/v1`. `public` means no bearer token; it does not mean no rate limit. `self` means the bearer token's user. All `POST`, `PATCH`, and `DELETE` calls require `Content-Type: application/json` when they have a JSON body.

| ID | Method and path | Auth | Success | Purpose and spec mapping |
|---|---|---|---|---|
| API-U01 | `POST /users` | Public | `201` account | Register learner; `USR-01`, `ACC-01`–`ACC-04`. |
| API-U02 | `POST /auth/login` | Public | `200` token response | Authenticate and create session; `USR-02`, `AUTH-01`–`AUTH-04`, `SES-01`. |
| API-U03 | `POST /auth/refresh` | Refresh token | `200` token response | Rotate tokens on the same session; `USR-12`, `SES-11`. |
| API-U04 | `GET /auth/session` | Self | `200` session + user | Validate current access token and session, useful after restart if access token is still in memory; `SES-04`, `SES-13`–`SES-16`. |
| API-U05 | `POST /auth/logout` | Self | `204` | Revoke current session and token family; `USR-03`. |
| API-U06 | `GET /me` | Self | `200` account | Read own account and contact states; `USR-06`. |
| API-U07 | `PATCH /me` | Self | `200` account | Edit allowed non-contact profile fields; `USR-07`, `VER-09`. |
| API-U08 | `POST /me/password-change` | Self + current password | `204` | Change password and revoke all sessions; `USR-05`, `SES-06`. |
| API-U09 | `GET /me/sessions` | Self | `200` sessions | List live sessions; `USR-09`, `SES-01`–`SES-03`. |
| API-U10 | `DELETE /me/sessions/{session_id}` | Self | `204` | Revoke owned session; `USR-10`, `SES-03`–`SES-04`. |
| API-U11 | `POST /me/sessions/revoke-all` | Self | `204` | Atomically revoke all sessions; `USR-11`, `SES-05`. |
| API-U12 | `POST /auth/password-reset/challenges` | Public | `202` generic result | Send Gmail email OTP or Zalo phone OTP; `USR-04`, `REC-01`–`REC-03`. |
| API-U13 | `POST /auth/password-reset/challenges/{challenge_id}/resend` | Public | `202` generic result | Resend under shared channel/account/contact limits; `REC-03`, `REC-06`. |
| API-U14 | `POST /auth/password-reset/challenges/{challenge_id}/verify` | Public | `200` reset grant | Consume OTP, grant 5 minute one time reset right; `REC-04`. |
| API-U15 | `POST /auth/password-reset/complete` | Reset grant | `204` | Set password and atomically revoke all sessions and recovery challenges; `REC-05`. |
| API-U16 | `POST /me/phone-verifications` | Self | `202` challenge | Send Zalo OTP to current saved phone; `USR-14`, `VER-04`–`VER-05`, `VER-07`. |
| API-U17 | `POST /me/phone-verifications/{challenge_id}/resend` | Self | `202` challenge | Resend phone verification OTP; `VER-05`. |
| API-U18 | `POST /me/phone-verifications/{challenge_id}/verify` | Self | `200` account | Consume OTP and set `phone_verified_at`; `USR-15`, `VER-06`. |
| API-U19 | `POST /me/contact-changes` | Self + current password | `202` pending change | Start verified replacement of email/phone; `USR-16`, `VER-01`, `VER-08`. |
| API-U20 | `POST /me/contact-changes/{change_id}/verify` | Self | `200` account | Verify new contact, replace current contact, revoke all sessions; `VER-08`. |
| API-U21 | `POST /me/google-link/start` | Self + current password | `200` authorization URL | Start Google link with backend state/nonce/PKCE; `USR-13`, `VER-01`–`VER-03`. |
| API-U22 | `GET /auth/google-link/callback` | Google redirect | Redirect result | Backend exchanges code, verifies ID token, links Google `sub` only to initiating account; `VER-01`–`VER-03`, `VER-10`. |
| API-U23 | `POST /auth/activity` | Self | `204` | Signal an active foreground interaction, at most once per minute while reading/typing without another request; `SES-09`–`SES-10`. |

There is no public admin registration endpoint. Admin creation/lock/unlock is an operational interface outside this first learner contract. Any admin route must still check `role` and live session at the backend. Google and Zalo are **verification channels only**, not login methods.

## 6. Payloads and behavior by flow

### 6.1 Registration and login (`API-U01`–`API-U05`)

`POST /users` request:

```json
{
  "username": "minh_learner",
  "password": "<user-entered password>",
  "email": null,
  "phone": null
}
```

The backend normalizes and validates identifiers, checks uniqueness, creates a stable `user_id` and learner profile, and returns the account representation. Contact fields are optional; if supplied they start unverified. Registration does **not** create a session in this draft; the learner then logs in before onboarding data is saved under `user_id`. The existing Register screen's immediate jump to onboarding is a prototype behavior to replace.

`POST /auth/login` request:

```json
{
  "username": "minh_learner",
  "password": "<user-entered password>",
  "device_label": "Samsung A54",
  "platform": "android"
}
```

The success response is §3.2. Backend account/IP throttling is exactly `AUTH-01`: 5 consecutive failures per account in 15 minutes cause a 15 minute pause; at most 20 requests per IP in 10 minutes. Failure uses the indistinguishable response in §4. The app routes to onboarding or Home according to the separate onboarding preference state after login, not directly from a local button tap.

`POST /auth/refresh` request:

```json
{ "refresh_token": "opaque-refresh-token" }
```

It returns a new access token and **new refresh token** for the same session, invalidating the old refresh token. The server checks the session before rotating and atomically handles concurrent refresh/revoke. Reuse of an old refresh token revokes its family and session. The Android client coordinates simultaneous 401s into one refresh attempt and persists the replacement refresh token before releasing waiting requests. `GET /auth/session` does not create a new session or update activity. `POST /auth/logout` needs server confirmation before the UI claims logout succeeded; after confirmation the app deletes its tokens.

### 6.2 Profile, sessions, and password change (`API-U06`–`API-U11`)

`PATCH /me` accepts only non-contact profile fields, initially `display_name`. The final set and validation policy are an open decision in §8. Email/phone or `verified_at` sent here receives `400 VALIDATION_ERROR`; contact changes use `API-U19`/`API-U20`. `POST /me/password-change` accepts `{ "current_password": "...", "new_password": "..." }`. A successful change invalidates even the current session, so the Android app clears tokens and returns to login.

`GET /me/sessions` returns `{ "sessions": [<§3.1 session>] }`. `DELETE /me/sessions/{session_id}` returns `404 NOT_FOUND` for an unowned/nonvisible ID. If the chosen ID is the current session, the app clears its tokens after success. `POST /me/sessions/revoke-all` must be atomic against a simultaneous refresh and ends the current session as well.

### 6.3 Password recovery (`API-U12`–`API-U15`)

Start request:

```json
{
  "channel": "gmail",
  "login_identifier": "minh_learner",
  "contact": "minh@gmail.com"
}
```

`channel` is `gmail` (email OTP) or `zalo` (phone OTP), never Google login or SMS. The user enters the actual email/phone. When more than one account could match a contact, `login_identifier` disambiguates it. The server uses only a **current saved** contact, even if unverified, and responds generically before verification: `{ "message": "Nếu thông tin phù hợp, mã khôi phục sẽ được gửi qua kênh đã chọn", "challenge_id": "opaque-handle" }`. The server issues an indistinguishable opaque handle for unmatched requests too; it cannot be used to obtain a valid OTP or reset grant. No recovery channel exists for an account with no saved contact.

Verify request is `{ "otp": "123456" }`. A successful `API-U14` response is `{ "reset_grant": "opaque-one-time-grant", "expires_at": "2026-10-25T12:05:00Z" }`. The grant is held only in memory and can call **only** `API-U15`; it cannot authorize `/me` or learning APIs. Complete request is `{ "reset_grant": "...", "new_password": "..." }`. Completion consumes the grant, changes the password, invalidates all recovery challenges from both channels, and revokes all sessions in one transaction. The app then asks for login; it does not log in automatically.

OTP rules: six digits, five minute life, at most five wrong attempts; resend after at least 60 seconds, no more than five sends per hour aggregated by account and contact across both channels, plus IP limit. Resend replaces the previous OTP and does not reset hourly counters. A fifth wrong attempt invalidates the challenge. The server stores only a secret protected verification value, never the raw OTP in logs or response.

### 6.4 Contact and Google verification (`API-U16`–`API-U22`)

Phone verification is for a saved current phone. The server sends a `phone_verification` OTP through Zalo; only that purpose can set `phone_verified_at`. The request returns `{ "challenge_id": "phv_...", "masked_phone": "*******678", "resend_after_at": "..." }`. Verification consumes the OTP atomically. Zalo delivery failure leaves the phone unverified.

Changing a contact first checks the current password and creates a pending candidate. `API-U19` request is `{ "kind": "email", "new_value": "new@gmail.com", "current_password": "..." }` or the phone equivalent. The backend checks ownership uniqueness at start and completion, sends a purpose bound verification to the **new** contact, and keeps the old contact current until `API-U20` succeeds. The candidate cannot receive password reset OTP. Successful replacement invalidates old-contact recovery/verification requests and all sessions, then requires login. For a Gmail candidate the exact verification mechanism needs confirmation in §8; a saved non-Gmail email cannot be marked verified by Google.

Google link starts only after password reauthentication. The backend creates a five minute proof tied to the current session and operation, plus state/nonce/PKCE. After the Google callback it verifies issuer, audience, signature, expiry, nonce, and `email_verified = true`; identity is based on Google `sub`. A Google account already linked elsewhere returns `409 CONTACT_ALREADY_LINKED` without partial linking or showing the other account. Linking Google confirms a Gmail only when it matches the applicable current/pending contact under the approved contact policy. The Android return link is handled only after the session and OAuth state are revalidated, including process death.

### 6.5 Activity signal (`API-U23`)

The app sends this only while foregrounded and after a new active interaction during reading/typing when no other authenticated active API request was sent, at most once per minute. The backend uses **server receipt time** and the current session. Opening the app, background sync, push, refresh, or passive audio does not extend idle expiry. A failed/offline signal is not queued indefinitely to extend a session later.

## 7. Android Retrofit integration boundary

The Java app has a `UserApi` Retrofit interface for implemented routes and DTOs for their responses. The current `AuthRepository` supports registration, login, refresh, profile read, and logout; other implemented backend routes are not wired to UI flows yet. An OkHttp interceptor and a single refresh coordinator remain planned for general authenticated requests. That coordinator must never retry a non-idempotent submission unless the endpoint explicitly supports an idempotency key. The app restores access only after `API-U03` or `API-U04` succeeds against the backend. Missing token, revoked/expired session, and refresh reuse clear local tokens; network failure keeps the refresh token but shows a retry state without claiming authentication succeeded.

This section describes the client boundary; the contract document itself does not provide a deployed endpoint.

## 8. Assumptions, open decisions, and delivery risks

| ID | Type | Item | Impact / action |
|---|---|---|---|
| DEC-U01 | Partly decided | Backend uses Java Spring Boot and PostgreSQL; deployment address is open. | Configure the production HTTPS address when deploying the server. |
| DEC-U02 | Open decision | Username/password validation policy, allowed `profile` fields, and exact contact formats are not specified. | Agree rules before coding validation or FE error text. |
| DEC-U03 | Open decision | Registration currently proposes **no automatic login**. | Confirm whether registration should instead establish a session; either choice must preserve `SES-01` and authenticated onboarding. |
| DEC-U04 | Open decision | Native Android secure refresh-token storage choice and backup exclusion implementation. | Choose and test against `SES-13`–`SES-15`; Expo SecureStore wording in the spec is stale for this Java app. |
| DEC-U05 | Open decision | Email verification for non-Gmail addresses and exact Gmail candidate-change sequence. | Specify before implementing `API-U19`/`API-U20`; never equate saved email with verified ownership. |
| DEC-U06 | Open decision | Google OAuth mobile redirect URI, Android app link handling, and Zalo OA/provider credentials. | Needed to complete real provider integrations; mocks cannot prove delivery. |
| DEC-U07 | Open decision | Backend retention of idempotency keys and audit events beyond the proposed minimum, plus privacy/retention policy. | Agree operational policy before production. |
| RISK-U01 | High × High | Refresh race or reuse handling can wrongly sign users out or permit revoked sessions. | Atomic backend token-family updates; client single-flight refresh; test `AC-USR-03`, `AC-USR-11`, `AC-USR-23`, `AC-USR-27`. |
| RISK-U02 | Medium × High | Login and recovery differences can reveal account existence. | Same login failure code/body behavior and generic preverification recovery result; test `AC-USR-25` and `AC-USR-17`. |
| RISK-U03 | Medium × High | Zalo or Google integration may be unavailable or misconfigured. | Keep contact unverified; test real provider flow before release; test `AC-USR-07`–`AC-USR-10`. |

## 9. Acceptance map and next implementation slice

| Contract area | Product acceptance references | First integration check |
|---|---|---|
| Registration/login | `AC-USR-19`, `AC-USR-22`, `AC-USR-25` | Register creates learner only; login returns one live session; identical failure presentation. |
| Session lifecycle | `AC-USR-01`–`AC-USR-06`, `AC-USR-11`–`AC-USR-15`, `AC-USR-23`, `AC-USR-27` | Restart restores the **same** session; revoke and refresh race cannot restore it. |
| Profile/contact | `AC-USR-07`–`AC-USR-10`, `AC-USR-20`–`AC-USR-24` | Contact stays pending until verified; another user's contact cannot be claimed. |
| Recovery | `AC-USR-16`–`AC-USR-18`, `AC-USR-26` | OTP purpose/expiry/attempt limits hold; reset revokes both channels and all sessions. |

The first executable slice after agreeing this contract is `POST /users`, `POST /auth/login`, `POST /auth/refresh`, `GET /me`, and `POST /auth/logout`, wired into the existing Java login/register UI. The remaining endpoints then extend the same session and error model.
