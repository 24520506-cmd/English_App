# English App backend

Java 21, Spring Boot 4.1.2, PostgreSQL, Flyway, and Spring Security are configured. The User API currently includes registration, login, refresh-token rotation and reuse revocation, current session/profile, display-name update, password change, session list and revocation, logout, and an explicit activity signal. Password recovery, contact verification, Google/Zalo integration, admin endpoints, and lesson APIs remain separate later slices.

Run `mvn test` for unit tests. To also run HTTP and database integration tests, provide a disposable PostgreSQL database with `TEST_DATABASE_URL=jdbc:postgresql://host:port/database`, plus `TEST_DATABASE_USER` and `TEST_DATABASE_PASSWORD` if different from the defaults, then run `mvn test`. Flyway applies migrations to that test database. The integration tests are skipped when `TEST_DATABASE_URL` is absent.

Audio assets should use private object storage for files and PostgreSQL for metadata, approval state, checksums, and lesson-version references. The storage provider, signed URL policy, and retention settings need an operational decision before audio endpoints are added.
