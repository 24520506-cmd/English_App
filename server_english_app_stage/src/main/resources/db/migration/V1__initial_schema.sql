CREATE TABLE app_user (
    user_id UUID PRIMARY KEY,
    username VARCHAR(30) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(320),
    phone VARCHAR(20),
    email_verified_at TIMESTAMPTZ,
    phone_verified_at TIMESTAMPTZ,
    role VARCHAR(20) NOT NULL DEFAULT 'learner',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_session (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(user_id),
    device_label VARCHAR(80) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    access_token_hash CHAR(64) NOT NULL UNIQUE,
    access_expires_at TIMESTAMPTZ NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL UNIQUE,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(50)
);

CREATE INDEX idx_user_session_owner ON user_session(user_id);

CREATE TABLE retired_refresh_token (
    token_hash CHAR(64) PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES user_session(session_id),
    retired_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audio_asset (
    asset_id UUID PRIMARY KEY,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    kind VARCHAR(30) NOT NULL,
    source VARCHAR(20) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    duration_ms INTEGER NOT NULL CHECK (duration_ms > 0),
    checksum_sha256 CHAR(64) NOT NULL,
    text_source TEXT,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
