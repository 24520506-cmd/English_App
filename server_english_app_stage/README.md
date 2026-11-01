# English App backend

Java 21, Spring Boot 4.1.2, PostgreSQL, Flyway, and Spring Security are configured. The User API currently includes registration, login, refresh-token rotation and reuse revocation, current session/profile, display-name update, password change, session list and revocation, logout, and an explicit activity signal. Password recovery, contact verification, Google/Zalo integration, admin endpoints, and lesson APIs remain separate later slices.

Run `mvn test` for unit tests. To also run HTTP and database integration tests, provide a disposable PostgreSQL database with `TEST_DATABASE_URL=jdbc:postgresql://host:port/database`, plus `TEST_DATABASE_USER` and `TEST_DATABASE_PASSWORD` if different from the defaults, then run `mvn test`. Flyway applies migrations to that test database. The integration tests are skipped when `TEST_DATABASE_URL` is absent.

Audio assets should use private object storage for files and PostgreSQL for metadata, approval state, checksums, and lesson-version references. The storage provider, signed URL policy, and retention settings need an operational decision before audio endpoints are added.

## SMS phone OTP

Phone verification uses an approved eSMS brandname/template sent to the saved
phone number. Configure these environment variables before starting the server:

- `OTP_HMAC_SECRET`: at least 32 random bytes, used to protect OTP verification values.
- `ESMS_API_KEY` and `ESMS_SECRET_KEY`: credentials from the eSMS account.
- `ESMS_BRANDNAME`: registered SMS sender name.
- `ESMS_OTP_CONTENT_TEMPLATE`: registered content containing `{code}`.

The API never returns or logs the raw OTP. Without the eSMS credentials, creation
returns `503 OTP_DELIVERY_FAILED` and leaves the phone unverified.

## Gmail email OTP

Email verification uses Gmail SMTP. Set `GMAIL_USERNAME`, a 16-character
`GMAIL_APP_PASSWORD`, and `OTP_HMAC_SECRET` (at least 32 random bytes). The
endpoints create, resend, and verify a six-digit code without returning or
logging the raw value.
