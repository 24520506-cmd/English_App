-- These functions are transaction primitives. Callers must hash OTPs with a
-- server-held HMAC key and must never pass raw OTP, token, or password values.

CREATE FUNCTION reserve_otp_send(
    p_challenge_id UUID, p_user_id UUID, p_contact_kind VARCHAR,
    p_contact_value VARCHAR, p_channel VARCHAR, p_request_ip INET,
    p_ip_hour_limit INTEGER
) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
    checked_at TIMESTAMPTZ;
    latest_send TIMESTAMPTZ;
    challenge otp_challenge%ROWTYPE;
    account_count INTEGER;
    contact_count INTEGER;
    ip_count INTEGER;
BEGIN
    IF p_ip_hour_limit IS NULL OR p_ip_hour_limit < 1 OR p_request_ip IS NULL THEN
        RETURN FALSE;
    END IF;
    PERFORM 1 FROM app_user WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    -- Same contact and IP are serialized even across accounts and channels.
    PERFORM pg_advisory_xact_lock(hashtextextended(p_contact_kind || ':' || p_contact_value, 1));
    PERFORM pg_advisory_xact_lock(hashtextextended(host(p_request_ip), 2));
    SELECT * INTO challenge FROM otp_challenge WHERE challenge_id = p_challenge_id AND user_id = p_user_id
        AND contact_kind = p_contact_kind AND contact_value = p_contact_value
        AND channel = p_channel FOR UPDATE;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    checked_at := clock_timestamp();
    IF challenge.used_at IS NOT NULL OR challenge.invalidated_at IS NOT NULL
        OR checked_at >= challenge.expires_at THEN RETURN FALSE; END IF;
    SELECT max(attempted_at) INTO latest_send FROM otp_send_attempt
        WHERE user_id = p_user_id AND contact_kind = p_contact_kind
            AND contact_value = p_contact_value;
    IF latest_send IS NOT NULL AND checked_at < latest_send + interval '60 seconds' THEN
        RETURN FALSE;
    END IF;
    SELECT count(*) INTO account_count FROM otp_send_attempt
        WHERE user_id = p_user_id AND attempted_at > checked_at - interval '1 hour';
    SELECT count(*) INTO contact_count FROM otp_send_attempt
        WHERE contact_kind = p_contact_kind AND contact_value = p_contact_value
            AND attempted_at > checked_at - interval '1 hour';
    SELECT count(*) INTO ip_count FROM otp_send_attempt
        WHERE request_ip = p_request_ip AND attempted_at > checked_at - interval '1 hour';
    IF account_count >= 5 OR contact_count >= 5 OR ip_count >= p_ip_hour_limit THEN
        RETURN FALSE;
    END IF;
    INSERT INTO otp_send_attempt(send_id, challenge_id, user_id, contact_kind,
        contact_value, channel, request_ip, attempted_at, delivery_state)
        VALUES (gen_random_uuid(), p_challenge_id, p_user_id, p_contact_kind,
            p_contact_value, p_channel, p_request_ip, checked_at, 'reserved');
    RETURN TRUE;
END;
$$;

CREATE FUNCTION resend_user_otp(
    p_challenge_id UUID, p_user_id UUID, p_new_verification_hash CHAR(64),
    p_request_ip INET, p_ip_hour_limit INTEGER
) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
    challenge otp_challenge%ROWTYPE;
    reserved BOOLEAN;
    issued TIMESTAMPTZ;
BEGIN
    IF p_new_verification_hash IS NULL OR p_new_verification_hash !~ '^[0-9a-f]{64}$' THEN
        RETURN FALSE;
    END IF;
    SELECT * INTO challenge FROM otp_challenge
        WHERE challenge_id = p_challenge_id AND user_id = p_user_id;
    IF NOT FOUND OR challenge.verification_hash = p_new_verification_hash THEN RETURN FALSE; END IF;
    reserved := reserve_otp_send(p_challenge_id, p_user_id, challenge.contact_kind,
        challenge.contact_value, challenge.channel, p_request_ip, p_ip_hour_limit);
    IF NOT reserved THEN RETURN FALSE; END IF;
    issued := clock_timestamp();
    UPDATE otp_challenge SET verification_hash = p_new_verification_hash,
        issued_at = issued, expires_at = issued + interval '5 minutes'
        WHERE challenge_id = p_challenge_id AND user_id = p_user_id;
    RETURN TRUE;
END;
$$;

CREATE FUNCTION consume_user_otp(
    p_user_id UUID, p_challenge_id UUID, p_purpose VARCHAR,
    p_contact_kind VARCHAR, p_contact_value VARCHAR, p_channel VARCHAR,
    p_verification_hash CHAR(64)
) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
    challenge otp_challenge%ROWTYPE;
    checked_at TIMESTAMPTZ;
    next_attempts SMALLINT;
BEGIN
    PERFORM 1 FROM app_user WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND THEN RETURN 'invalid'; END IF;
    SELECT * INTO challenge FROM otp_challenge
        WHERE challenge_id = p_challenge_id AND user_id = p_user_id FOR UPDATE;
    IF NOT FOUND THEN RETURN 'invalid'; END IF;
    checked_at := clock_timestamp();
    IF challenge.purpose <> p_purpose OR challenge.contact_kind <> p_contact_kind
       OR challenge.contact_value <> p_contact_value OR challenge.channel <> p_channel THEN
        RETURN 'invalid';
    END IF;
    IF challenge.used_at IS NOT NULL THEN RETURN 'used'; END IF;
    IF challenge.invalidated_at IS NOT NULL OR checked_at >= challenge.expires_at THEN
        RETURN 'expired';
    END IF;
    IF challenge.wrong_attempts >= 5 THEN RETURN 'attempts_exhausted'; END IF;
    IF p_verification_hash IS NULL OR challenge.verification_hash <> p_verification_hash THEN
        next_attempts := challenge.wrong_attempts + 1;
        UPDATE otp_challenge SET wrong_attempts = next_attempts,
            invalidated_at = CASE WHEN next_attempts = 5 THEN checked_at ELSE NULL END
            WHERE challenge_id = p_challenge_id;
        IF next_attempts = 5 THEN RETURN 'attempts_exhausted'; END IF;
        RETURN 'invalid';
    END IF;
    UPDATE otp_challenge SET used_at = checked_at WHERE challenge_id = p_challenge_id;
    RETURN 'consumed';
END;
$$;

CREATE FUNCTION complete_phone_verification(
    p_user_id UUID, p_challenge_id UUID, p_phone VARCHAR, p_verification_hash CHAR(64)
) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
    result TEXT;
BEGIN
    PERFORM 1 FROM app_user WHERE user_id = p_user_id AND phone = p_phone FOR UPDATE;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    result := consume_user_otp(p_user_id, p_challenge_id, 'phone_verification',
        'phone', p_phone, 'zalo', p_verification_hash);
    IF result <> 'consumed' THEN RETURN FALSE; END IF;
    UPDATE app_user SET phone_verified_at = clock_timestamp() WHERE user_id = p_user_id;
    RETURN TRUE;
EXCEPTION WHEN unique_violation THEN
    RETURN FALSE;
END;
$$;

CREATE FUNCTION complete_password_reset(
    p_grant_hash CHAR(64), p_new_password_hash VARCHAR
) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
    owner_id UUID;
    grant_row password_reset_grant%ROWTYPE;
    checked_at TIMESTAMPTZ;
BEGIN
    IF p_new_password_hash IS NULL OR length(p_new_password_hash) = 0 THEN RETURN FALSE; END IF;
    SELECT user_id INTO owner_id FROM password_reset_grant WHERE grant_hash = p_grant_hash;
    IF owner_id IS NULL THEN RETURN FALSE; END IF;
    PERFORM 1 FROM app_user WHERE user_id = owner_id FOR UPDATE;
    SELECT * INTO grant_row FROM password_reset_grant
        WHERE grant_hash = p_grant_hash FOR UPDATE;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    checked_at := clock_timestamp();
    IF grant_row.consumed_at IS NOT NULL OR grant_row.invalidated_at IS NOT NULL
       OR checked_at >= grant_row.expires_at THEN RETURN FALSE; END IF;
    PERFORM 1 FROM otp_challenge WHERE challenge_id = grant_row.challenge_id
        AND user_id = owner_id AND purpose = 'password_reset' AND used_at IS NOT NULL;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    UPDATE app_user SET password_hash = p_new_password_hash WHERE user_id = owner_id;
    UPDATE password_reset_grant SET consumed_at = checked_at WHERE grant_id = grant_row.grant_id;
    UPDATE password_reset_grant SET invalidated_at = checked_at
        WHERE user_id = owner_id AND consumed_at IS NULL AND invalidated_at IS NULL;
    UPDATE otp_challenge SET invalidated_at = checked_at
        WHERE user_id = owner_id AND used_at IS NULL AND invalidated_at IS NULL;
    UPDATE user_session SET revoked_at = checked_at, revoke_reason = 'password_reset'
        WHERE user_id = owner_id AND revoked_at IS NULL;
    INSERT INTO security_event(user_id, event_type, outcome, occurred_at)
        VALUES (owner_id, 'password_reset', 'success', checked_at);
    RETURN TRUE;
END;
$$;

CREATE FUNCTION complete_contact_change(
    p_change_id UUID, p_challenge_id UUID, p_verification_hash CHAR(64)
) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
    owner_id UUID;
    change_row pending_contact_change%ROWTYPE;
    challenge otp_challenge%ROWTYPE;
    otp_result TEXT;
    checked_at TIMESTAMPTZ;
BEGIN
    SELECT user_id INTO owner_id FROM pending_contact_change WHERE change_id = p_change_id;
    IF owner_id IS NULL THEN RETURN FALSE; END IF;
    PERFORM 1 FROM app_user WHERE user_id = owner_id FOR UPDATE;
    SELECT * INTO change_row FROM pending_contact_change
        WHERE change_id = p_change_id FOR UPDATE;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    checked_at := clock_timestamp();
    IF change_row.completed_at IS NOT NULL OR change_row.invalidated_at IS NOT NULL
       OR checked_at >= change_row.expires_at THEN RETURN FALSE; END IF;
    PERFORM 1 FROM reauth_proof WHERE proof_id = change_row.reauth_proof_id
        AND user_id = owner_id AND operation = 'contact_change'
        AND consumed_at IS NOT NULL AND consumed_at <= expires_at;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    SELECT * INTO challenge FROM otp_challenge WHERE challenge_id = p_challenge_id FOR UPDATE;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    checked_at := clock_timestamp();
    IF checked_at >= change_row.expires_at THEN RETURN FALSE; END IF;
    IF challenge.user_id <> owner_id OR challenge.change_id <> p_change_id
       OR challenge.purpose <> 'contact_change' OR challenge.channel <> change_row.channel
       OR challenge.contact_kind <> change_row.contact_kind
       OR challenge.contact_value <> change_row.candidate_value THEN
        RETURN FALSE;
    END IF;
    IF change_row.contact_kind = 'email' THEN
        IF EXISTS (SELECT 1 FROM app_user WHERE user_id <> owner_id
            AND email_verified_at IS NOT NULL
            AND lower(btrim(email)) = change_row.candidate_value) THEN RETURN FALSE; END IF;
    ELSE
        IF EXISTS (SELECT 1 FROM app_user WHERE user_id <> owner_id
            AND phone_verified_at IS NOT NULL AND phone = change_row.candidate_value) THEN
            RETURN FALSE;
        END IF;
    END IF;
    otp_result := consume_user_otp(owner_id, p_challenge_id, 'contact_change',
        change_row.contact_kind, change_row.candidate_value, change_row.channel,
        p_verification_hash);
    IF otp_result <> 'consumed' THEN RETURN FALSE; END IF;
    IF change_row.contact_kind = 'email' THEN
        UPDATE app_user SET email = change_row.candidate_value,
            email_verified_at = checked_at WHERE user_id = owner_id;
    ELSE
        UPDATE app_user SET phone = change_row.candidate_value,
            phone_verified_at = checked_at WHERE user_id = owner_id;
    END IF;
    UPDATE pending_contact_change SET completed_at = checked_at WHERE change_id = p_change_id;
    UPDATE pending_contact_change SET invalidated_at = checked_at
        WHERE user_id = owner_id AND change_id <> p_change_id
            AND completed_at IS NULL AND invalidated_at IS NULL;
    UPDATE otp_challenge SET invalidated_at = checked_at
        WHERE user_id = owner_id AND used_at IS NULL AND invalidated_at IS NULL;
    UPDATE password_reset_grant SET invalidated_at = checked_at
        WHERE user_id = owner_id AND consumed_at IS NULL AND invalidated_at IS NULL;
    UPDATE user_session SET revoked_at = checked_at, revoke_reason = 'contact_change'
        WHERE user_id = owner_id AND revoked_at IS NULL;
    INSERT INTO security_event(user_id, event_type, outcome, occurred_at)
        VALUES (owner_id, 'contact_change', 'success', checked_at);
    RETURN TRUE;
EXCEPTION WHEN unique_violation THEN
    RETURN FALSE;
END;
$$;
