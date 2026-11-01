CREATE TABLE login_failure (
    username VARCHAR(30) PRIMARY KEY,
    failures INTEGER NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    blocked_until TIMESTAMPTZ
);

CREATE TABLE login_ip_request (
    request_id BIGSERIAL PRIMARY KEY,
    ip_address VARCHAR(64) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_ip_window ON login_ip_request(ip_address, requested_at);
