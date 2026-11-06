# English App backend

Java 21, Spring Boot 4.1.2, PostgreSQL, Flyway, and Spring Security are configured. The User API includes registration, authentication/session management, profile/preferences, OTP password recovery, phone contact verification/change, Gmail contact change through Google OAuth, and Google account linking. Admin endpoints and lesson APIs remain separate slices.

Run `mvn test` for unit tests. To also run HTTP and database integration tests, provide a disposable PostgreSQL database with `TEST_DATABASE_URL=jdbc:postgresql://host:port/database`, plus `TEST_DATABASE_USER` and `TEST_DATABASE_PASSWORD` if different from the defaults, then run `mvn test`. Flyway applies migrations to that test database. The integration tests are skipped when `TEST_DATABASE_URL` is absent.

The current test/development setup serves audio from a read-only mounted directory.
Before production scale-out, move those files to private object storage and keep
metadata, approval state, checksums, and lesson-version references in PostgreSQL.
The storage provider, signed-URL policy, and retention settings still need an
operational decision.

## Docker, local data, and VPS deployment

The repository includes a multi-stage Java 21 `Dockerfile` and `compose.yaml` for
the API plus PostgreSQL 17. Flyway creates and upgrades the database. Lesson JSON
is stored as `jsonb`; WAV files remain on the mounted filesystem and are streamed
by the API.

Download the current test dataset (12 JSON and 12 WAV files) and start the stack:

```bash
cd server_english_app_stage
./scripts/download-test-data.sh
cp .env.example .env
# Set strong POSTGRES_PASSWORD and OTP_HMAC_SECRET values in .env.
docker compose up --build -d
docker compose logs -f api
```

By default Compose mounts `../test-data/drive` read-only at `/data/lessons`.
Override the host location with `HOST_CONTENT_DATA_DIR=/absolute/path`. The API
imports or refreshes every JSON file idempotently at startup. Source-relative
paths are used as stable keys because the supplied dataset contains a duplicate
`lesson_id` and several schema variants.

Authenticated learner endpoints are:

- `GET /api/v1/lessons?band=...&topic=...`
- `GET /api/v1/lessons/{content_id}`
- `GET /api/v1/lessons/{content_id}/audio`

For a VPS, install Docker Engine with the Compose plugin, clone the repository,
download or copy the lesson directory, create `.env` from `.env.example`, then
run `docker compose up --build -d`. Keep PostgreSQL unexposed (the supplied
Compose file does), allow only the API/reverse-proxy ports through the firewall,
and terminate HTTPS through a reverse proxy. Back up the named
`postgres_data` volume and the external lesson directory separately.

To run all tests without installing Maven locally, while the Compose database is
running, create a disposable test database and run Maven on the Compose network:

```bash
docker compose exec db createdb -U english_app english_app_test
docker run --rm --network server_english_app_stage_default \
  -v "$PWD:/workspace" -w /workspace \
  -e TEST_DATABASE_URL=jdbc:postgresql://db:5432/english_app_test \
  -e TEST_DATABASE_USER=english_app \
  -e TEST_DATABASE_PASSWORD="$POSTGRES_PASSWORD" \
  maven:3.9.11-eclipse-temurin-21 mvn -B clean test
```

## Learner onboarding preferences

`GET /api/v1/me/preferences` and `PUT /api/v1/me/preferences` require a learner bearer token. PUT replaces the learner's band and ordered topic selection atomically. Before the first save, GET returns `{"band":null,"topic_ids":[]}`. The allowed band IDs are `BELOW_4_0`, `4_0_5_0`, `5_5_6_5`, and `7_0_PLUS`.

Topic IDs are stable API identifiers; clients should display localized labels separately:

| Topic ID | English label | Vietnamese label |
|---|---|---|
| `daily_life` | Daily Life | Đời sống hằng ngày |
| `education` | Education | Giáo dục |
| `technology` | Technology | Công nghệ |
| `environment` | Environment | Môi trường |
| `finance` | Finance | Tài chính |
| `travel` | Travel | Du lịch |
| `health` | Health | Sức khỏe |
| `work` | Work | Công việc |

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

## Password recovery

`POST /api/v1/auth/password-reset/challenges` accepts `channel`,
`login_identifier`, and the saved `contact`. It always returns the same `202`
message and an opaque `challenge_id`, whether the account matches or not.
Resend and verify use that challenge ID; successful verification returns a
five-minute, one-use `reset_grant`, which can only call
`POST /api/v1/auth/password-reset/complete`. A successful reset revokes all
sessions. Phone recovery currently uses the configured eSMS sender adapter;
the `zalo` API channel does not configure a Zalo OA integration.

## Contact changes and Google OAuth

Phone changes use `POST /api/v1/me/contact-changes` with `kind: "phone"`,
`new_value`, and `current_password`, then verify the OTP at
`POST /api/v1/me/contact-changes/{change_id}/verify`. Email changes currently
accept Gmail addresses only. Start one with `kind: "email"`; pass its returned
`change_id` and `current_password` to `POST /api/v1/me/google-link/start` to get
the Google authorization URL. Google's verified address must exactly match
the pending candidate. The callback then commits the contact change and
revokes all sessions.

Configure these deployment variables for Google OAuth:

- `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` from the Google OAuth client.
- `GOOGLE_CALLBACK_URI`, an HTTPS redirect URI registered for that client.
- `GOOGLE_MOBILE_RETURN_URI`, the app link/scheme opened after callback.
- `GOOGLE_PKCE_ENCRYPTION_KEY`, a Base64-encoded random 32-byte AES key for
  encrypting PKCE verifiers stored temporarily in PostgreSQL.

Google OAuth remains unavailable until all variables are set. Do not commit
provider secrets or encryption keys.
