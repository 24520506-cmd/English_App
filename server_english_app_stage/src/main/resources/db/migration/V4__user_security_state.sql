-- Additive User API state. V1-V3 and existing rows remain untouched.
ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_role CHECK (role IN ('learner', 'admin')),
    ADD CONSTRAINT ck_app_user_status CHECK (status IN ('active', 'locked', 'disabled')),
    ADD CONSTRAINT ck_app_user_email_proof CHECK (email_verified_at IS NULL OR email IS NOT NULL),
    ADD CONSTRAINT ck_app_user_phone_proof CHECK (phone_verified_at IS NULL OR phone IS NOT NULL),
    ADD CONSTRAINT ck_app_user_verified_phone_format CHECK (
        phone_verified_at IS NULL OR phone ~ '^\+[1-9][0-9]{8,14}$');

-- Unverified contacts may repeat; recovery must identify one account before sending.
CREATE UNIQUE INDEX uq_app_user_username_folded ON app_user (lower(username));
CREATE UNIQUE INDEX uq_app_user_verified_email ON app_user (lower(btrim(email)))
    WHERE email_verified_at IS NOT NULL;
CREATE UNIQUE INDEX uq_app_user_verified_phone ON app_user (phone)
    WHERE phone_verified_at IS NOT NULL;

ALTER TABLE user_session
    ADD COLUMN login_ip INET,
    ADD COLUMN idle_timeout_seconds INTEGER DEFAULT 604800;
UPDATE user_session s SET idle_timeout_seconds = CASE WHEN u.role = 'admin' THEN 1800 ELSE 604800 END
    FROM app_user u WHERE u.user_id = s.user_id;
-- Legacy refresh could issue a 15-minute access token close to absolute expiry.
UPDATE user_session SET access_expires_at = absolute_expires_at
    WHERE access_expires_at > absolute_expires_at;
ALTER TABLE user_session
    ALTER COLUMN idle_timeout_seconds SET NOT NULL,
    ADD CONSTRAINT ck_user_session_idle CHECK (idle_timeout_seconds IN (1800, 604800)),
    ADD CONSTRAINT ck_user_session_times CHECK (
        last_activity_at >= created_at AND absolute_expires_at > created_at
        AND access_expires_at > created_at AND access_expires_at <= absolute_expires_at),
    ADD CONSTRAINT ck_user_session_revocation CHECK (
        (revoked_at IS NULL AND revoke_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revoke_reason IS NOT NULL));
CREATE UNIQUE INDEX uq_user_session_id_owner ON user_session(session_id, user_id);
CREATE INDEX idx_user_session_live_owner ON user_session(user_id, created_at DESC)
    WHERE revoked_at IS NULL;

ALTER TABLE retired_refresh_token ADD COLUMN replaced_by_hash CHAR(64);
CREATE UNIQUE INDEX uq_retired_refresh_replacement ON retired_refresh_token(replaced_by_hash)
    WHERE replaced_by_hash IS NOT NULL;
CREATE INDEX idx_retired_refresh_session ON retired_refresh_token(session_id, retired_at DESC);

CREATE TABLE pending_contact_change (
    change_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(user_id),
    contact_kind VARCHAR(10) NOT NULL CHECK (contact_kind IN ('email', 'phone')),
    candidate_value VARCHAR(320) NOT NULL,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('gmail', 'zalo')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    CHECK ((contact_kind = 'email' AND channel = 'gmail' AND candidate_value = lower(btrim(candidate_value)))
        OR (contact_kind = 'phone' AND channel = 'zalo' AND candidate_value ~ '^\+[1-9][0-9]{8,14}$')),
    CHECK (expires_at > created_at),
    CHECK (completed_at IS NULL OR invalidated_at IS NULL),
    CHECK (completed_at IS NULL OR
        (completed_at >= created_at AND completed_at <= expires_at))
);
CREATE UNIQUE INDEX uq_pending_contact_kind ON pending_contact_change(user_id, contact_kind)
    WHERE completed_at IS NULL AND invalidated_at IS NULL;
CREATE UNIQUE INDEX uq_pending_change_owner ON pending_contact_change(change_id, user_id);
CREATE INDEX idx_pending_contact_candidate ON pending_contact_change(contact_kind, candidate_value)
    WHERE completed_at IS NULL AND invalidated_at IS NULL;

CREATE TABLE otp_challenge (
    challenge_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(user_id),
    change_id UUID REFERENCES pending_contact_change(change_id),
    purpose VARCHAR(25) NOT NULL CHECK (purpose IN
        ('password_reset', 'phone_verification', 'contact_change')),
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('gmail', 'zalo')),
    contact_kind VARCHAR(10) NOT NULL CHECK (contact_kind IN ('email', 'phone')),
    contact_value VARCHAR(320) NOT NULL,
    verification_hash CHAR(64) NOT NULL CHECK (verification_hash ~ '^[0-9a-f]{64}$'),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    wrong_attempts SMALLINT NOT NULL DEFAULT 0 CHECK (wrong_attempts BETWEEN 0 AND 5),
    used_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    CHECK ((channel = 'gmail' AND contact_kind = 'email')
        OR (channel = 'zalo' AND contact_kind = 'phone')),
    CHECK ((contact_kind = 'email' AND contact_value = lower(btrim(contact_value)))
        OR (contact_kind = 'phone' AND contact_value ~ '^\+[1-9][0-9]{8,14}$')),
    CHECK ((purpose = 'contact_change' AND change_id IS NOT NULL)
        OR (purpose <> 'contact_change' AND change_id IS NULL)),
    CHECK (purpose <> 'phone_verification' OR contact_kind = 'phone'),
    CHECK (expires_at > issued_at AND expires_at <= issued_at + interval '5 minutes'),
    CHECK (used_at IS NULL OR invalidated_at IS NULL),
    CHECK (used_at IS NULL OR (used_at >= issued_at AND used_at <= expires_at)),
    CHECK (wrong_attempts < 5 OR invalidated_at IS NOT NULL)
);
CREATE INDEX idx_otp_challenge_user_live ON otp_challenge(user_id, purpose, expires_at)
    WHERE used_at IS NULL AND invalidated_at IS NULL;
CREATE UNIQUE INDEX uq_otp_challenge_owner ON otp_challenge(challenge_id, user_id);
CREATE UNIQUE INDEX uq_otp_challenge_delivery ON otp_challenge(
    challenge_id, user_id, contact_kind, contact_value, channel);
ALTER TABLE otp_challenge ADD CONSTRAINT fk_otp_change_owner
    FOREIGN KEY (change_id, user_id) REFERENCES pending_contact_change(change_id, user_id);
CREATE INDEX idx_otp_challenge_contact ON otp_challenge(user_id, contact_kind, contact_value);

CREATE TABLE otp_send_attempt (
    send_id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES otp_challenge(challenge_id),
    user_id UUID NOT NULL REFERENCES app_user(user_id),
    contact_kind VARCHAR(10) NOT NULL CHECK (contact_kind IN ('email', 'phone')),
    contact_value VARCHAR(320) NOT NULL,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('gmail', 'zalo')),
    request_ip INET NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivery_state VARCHAR(10) NOT NULL CHECK (delivery_state IN ('reserved', 'sent', 'failed')),
    CHECK ((channel = 'gmail' AND contact_kind = 'email')
        OR (channel = 'zalo' AND contact_kind = 'phone')),
    CHECK ((contact_kind = 'email' AND contact_value = lower(btrim(contact_value)))
        OR (contact_kind = 'phone' AND contact_value ~ '^\+[1-9][0-9]{8,14}$'))
);
CREATE INDEX idx_otp_send_user_window ON otp_send_attempt(user_id, attempted_at DESC);
CREATE INDEX idx_otp_send_contact_window ON otp_send_attempt(contact_kind, contact_value, attempted_at DESC);
CREATE INDEX idx_otp_send_ip_window ON otp_send_attempt(request_ip, attempted_at DESC);
ALTER TABLE otp_send_attempt ADD CONSTRAINT fk_otp_send_owner
    FOREIGN KEY (challenge_id, user_id) REFERENCES otp_challenge(challenge_id, user_id);
ALTER TABLE otp_send_attempt ADD CONSTRAINT fk_otp_send_delivery
    FOREIGN KEY (challenge_id, user_id, contact_kind, contact_value, channel)
    REFERENCES otp_challenge(challenge_id, user_id, contact_kind, contact_value, channel);

CREATE TABLE password_reset_grant (
    grant_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(user_id),
    challenge_id UUID NOT NULL UNIQUE REFERENCES otp_challenge(challenge_id),
    grant_hash CHAR(64) NOT NULL UNIQUE CHECK (grant_hash ~ '^[0-9a-f]{64}$'),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    CHECK (expires_at > issued_at AND expires_at <= issued_at + interval '5 minutes'),
    CHECK (consumed_at IS NULL OR invalidated_at IS NULL),
    CHECK (consumed_at IS NULL OR (consumed_at >= issued_at AND consumed_at <= expires_at))
);
CREATE INDEX idx_reset_grant_user_live ON password_reset_grant(user_id, expires_at)
    WHERE consumed_at IS NULL AND invalidated_at IS NULL;
ALTER TABLE password_reset_grant ADD CONSTRAINT fk_reset_grant_owner
    FOREIGN KEY (challenge_id, user_id) REFERENCES otp_challenge(challenge_id, user_id);

CREATE TABLE reauth_proof (
    proof_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    operation VARCHAR(20) NOT NULL CHECK (operation IN ('contact_change', 'google_link')),
    proof_hash CHAR(64) NOT NULL UNIQUE CHECK (proof_hash ~ '^[0-9a-f]{64}$'),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    FOREIGN KEY (session_id, user_id) REFERENCES user_session(session_id, user_id),
    CHECK (expires_at > issued_at AND expires_at <= issued_at + interval '5 minutes'),
    CHECK (consumed_at IS NULL OR (consumed_at >= issued_at AND consumed_at <= expires_at))
);
CREATE INDEX idx_reauth_proof_session ON reauth_proof(session_id, operation, expires_at);
CREATE UNIQUE INDEX uq_reauth_proof_owner ON reauth_proof(proof_id, user_id, session_id);
ALTER TABLE pending_contact_change ADD COLUMN reauth_proof_id UUID NOT NULL UNIQUE
    REFERENCES reauth_proof(proof_id);

CREATE TABLE google_identity (
    google_sub VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_user(user_id),
    verified_email VARCHAR(320) NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (verified_email = lower(btrim(verified_email))),
    CHECK (length(btrim(google_sub)) > 0)
);
CREATE UNIQUE INDEX uq_google_identity_email ON google_identity(verified_email);

CREATE TABLE google_link_attempt (
    attempt_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    reauth_proof_id UUID NOT NULL UNIQUE REFERENCES reauth_proof(proof_id),
    state_hash CHAR(64) NOT NULL UNIQUE CHECK (state_hash ~ '^[0-9a-f]{64}$'),
    nonce_hash CHAR(64) NOT NULL CHECK (nonce_hash ~ '^[0-9a-f]{64}$'),
    pkce_challenge CHAR(43) NOT NULL,
    pkce_verifier_ciphertext BYTEA NOT NULL,
    pkce_encryption_nonce BYTEA NOT NULL,
    redirect_uri VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    FOREIGN KEY (session_id, user_id) REFERENCES user_session(session_id, user_id),
    CHECK (expires_at > created_at AND expires_at <= created_at + interval '5 minutes'),
    CHECK (consumed_at IS NULL OR (consumed_at >= created_at AND consumed_at <= expires_at)),
    CHECK (octet_length(pkce_verifier_ciphertext) > 0 AND octet_length(pkce_encryption_nonce) > 0)
);
CREATE INDEX idx_google_link_session ON google_link_attempt(session_id, expires_at);
ALTER TABLE google_link_attempt ADD CONSTRAINT fk_google_attempt_proof_owner
    FOREIGN KEY (reauth_proof_id, user_id, session_id)
    REFERENCES reauth_proof(proof_id, user_id, session_id);

CREATE TABLE security_event (
    event_id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES app_user(user_id),
    session_id UUID REFERENCES user_session(session_id),
    event_type VARCHAR(60) NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('success', 'failure', 'limited', 'blocked')),
    login_identifier VARCHAR(30),
    request_ip INET,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (length(btrim(event_type)) > 0)
);
CREATE INDEX idx_security_event_user_time ON security_event(user_id, occurred_at DESC);
CREATE INDEX idx_security_event_identifier_time ON security_event(login_identifier, occurred_at DESC);
CREATE INDEX idx_security_event_ip_time ON security_event(request_ip, occurred_at DESC);

CREATE TABLE idempotency_receipt (
    user_id UUID NOT NULL REFERENCES app_user(user_id),
    endpoint VARCHAR(200) NOT NULL,
    idempotency_key UUID NOT NULL,
    body_sha256 CHAR(64) NOT NULL CHECK (body_sha256 ~ '^[0-9a-f]{64}$'),
    response_status SMALLINT,
    response_ciphertext BYTEA,
    response_nonce BYTEA,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, endpoint, idempotency_key),
    CHECK (length(btrim(endpoint)) > 0),
    CHECK (expires_at >= created_at + interval '24 hours'),
    CHECK ((completed_at IS NULL AND response_status IS NULL
            AND response_ciphertext IS NULL AND response_nonce IS NULL)
        OR (completed_at IS NOT NULL AND response_status BETWEEN 100 AND 599
            AND response_ciphertext IS NOT NULL AND response_nonce IS NOT NULL)),
    CHECK (response_ciphertext IS NULL OR octet_length(response_ciphertext) > 0),
    CHECK (response_nonce IS NULL OR octet_length(response_nonce) > 0)
);
CREATE INDEX idx_idempotency_expiry ON idempotency_receipt(expires_at);
