# User API Database Implementation Plan

**Goal:** Extend V3 without data loss to support User API security and contact flows.

**Architecture:** Retain account/session tables; add constrained tables in V4 and atomic transition routines in V5. Lock account rows first for competing account operations.

**Tech Stack:** PostgreSQL 16, Flyway, Java 21, Spring Boot 4.1.2, JUnit 5.

**Spec:** `USER_API_DATABASE.md`

**Constraints:** Do not edit V1–V3 or reset the active database. Keep `app_user.user_id`. Store no raw OTP, password or token in audit tables.

- [x] Add integration tests for V3 upgrade, constraints, and concurrent one-time transitions.
- [ ] Run a failing test against a disposable PostgreSQL database (socket access blocked in this environment).
- [x] Add V4 schema and indexes, V5 atomic routines, and V6 cross-table verified-contact claims.
- [x] Fix refresh/revoke-all lock order and add concurrent API test.
- [ ] Run Flyway over JDBC on scratch V3 and empty schemas, then all integration tests (socket access blocked).
- [x] Run PostgreSQL 16.15 single-user SQL migration and sequential smoke checks; document results and limits.
