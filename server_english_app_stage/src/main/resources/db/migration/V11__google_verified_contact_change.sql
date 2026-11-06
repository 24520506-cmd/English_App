ALTER TABLE google_link_attempt
    ADD COLUMN pending_change_id UUID;

ALTER TABLE google_link_attempt
    ADD CONSTRAINT fk_google_attempt_pending_contact
    FOREIGN KEY (pending_change_id, user_id)
    REFERENCES pending_contact_change(change_id, user_id);

CREATE FUNCTION complete_google_contact_change(
    p_attempt_id UUID, p_change_id UUID, p_google_sub VARCHAR, p_verified_email VARCHAR
) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
    attempt google_link_attempt%ROWTYPE;
    change_row pending_contact_change%ROWTYPE;
    checked_at TIMESTAMPTZ;
BEGIN
    checked_at := clock_timestamp();
    SELECT * INTO attempt FROM google_link_attempt
        WHERE attempt_id = p_attempt_id FOR UPDATE;
    IF NOT FOUND OR attempt.pending_change_id IS DISTINCT FROM p_change_id
        OR attempt.consumed_at IS NULL OR attempt.consumed_at > attempt.expires_at
        OR checked_at >= attempt.expires_at THEN
        RETURN FALSE;
    END IF;
    PERFORM 1 FROM app_user WHERE user_id = attempt.user_id AND status = 'active' FOR UPDATE;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    PERFORM 1 FROM user_session WHERE session_id = attempt.session_id AND user_id = attempt.user_id
        AND revoked_at IS NULL AND checked_at < access_expires_at
        AND checked_at < absolute_expires_at
        AND checked_at < last_activity_at + (idle_timeout_seconds * interval '1 second') FOR UPDATE;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    PERFORM 1 FROM reauth_proof WHERE proof_id = attempt.reauth_proof_id
        AND user_id = attempt.user_id AND session_id = attempt.session_id
        AND operation = 'google_link' AND consumed_at IS NOT NULL AND consumed_at <= expires_at
        AND checked_at < expires_at;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    SELECT * INTO change_row FROM pending_contact_change
        WHERE change_id = p_change_id AND user_id = attempt.user_id FOR UPDATE;
    checked_at := clock_timestamp();
    IF NOT FOUND OR change_row.contact_kind <> 'email' OR change_row.channel <> 'gmail'
        OR change_row.candidate_value <> lower(btrim(p_verified_email))
        OR change_row.completed_at IS NOT NULL OR change_row.invalidated_at IS NOT NULL
        OR checked_at >= change_row.expires_at THEN RETURN FALSE; END IF;
    PERFORM 1 FROM reauth_proof WHERE proof_id=change_row.reauth_proof_id
        AND user_id=attempt.user_id AND session_id=attempt.session_id
        AND operation='contact_change' AND consumed_at IS NOT NULL AND consumed_at <= expires_at;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    IF EXISTS (SELECT 1 FROM google_identity WHERE google_sub = p_google_sub
        AND user_id <> attempt.user_id) THEN RETURN FALSE; END IF;
    IF EXISTS (SELECT 1 FROM google_identity WHERE verified_email = lower(btrim(p_verified_email))
        AND user_id <> attempt.user_id) THEN RETURN FALSE; END IF;
    INSERT INTO google_identity(google_sub,user_id,verified_email)
        VALUES (p_google_sub,attempt.user_id,lower(btrim(p_verified_email)))
        ON CONFLICT DO NOTHING;
    IF NOT EXISTS (SELECT 1 FROM google_identity WHERE google_sub = p_google_sub
        AND user_id = attempt.user_id AND verified_email = lower(btrim(p_verified_email))) THEN
        RETURN FALSE;
    END IF;
    UPDATE app_user SET email = lower(btrim(p_verified_email)), email_verified_at = checked_at
        WHERE user_id = attempt.user_id;
    UPDATE pending_contact_change SET completed_at = checked_at WHERE change_id = p_change_id;
    UPDATE pending_contact_change SET invalidated_at = checked_at
        WHERE user_id = attempt.user_id AND change_id <> p_change_id
            AND completed_at IS NULL AND invalidated_at IS NULL;
    UPDATE otp_challenge SET invalidated_at = checked_at
        WHERE user_id = attempt.user_id AND used_at IS NULL AND invalidated_at IS NULL;
    UPDATE password_reset_grant SET invalidated_at = checked_at
        WHERE user_id = attempt.user_id AND consumed_at IS NULL AND invalidated_at IS NULL;
    UPDATE user_session SET revoked_at = checked_at, revoke_reason = 'contact_change'
        WHERE user_id = attempt.user_id AND revoked_at IS NULL;
    INSERT INTO security_event(user_id, session_id, event_type, outcome, occurred_at)
        VALUES (attempt.user_id, attempt.session_id, 'contact_change', 'success', checked_at);
    INSERT INTO security_event(user_id, session_id, event_type, outcome, occurred_at)
        VALUES (attempt.user_id, attempt.session_id, 'google_link', 'success', checked_at);
    RETURN TRUE;
EXCEPTION WHEN unique_violation THEN
    RETURN FALSE;
END;
$$;

CREATE FUNCTION complete_google_link(
    p_attempt_id UUID, p_google_sub VARCHAR, p_verified_email VARCHAR
) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
    attempt google_link_attempt%ROWTYPE;
    account_row app_user%ROWTYPE;
    checked_at TIMESTAMPTZ;
BEGIN
    checked_at := clock_timestamp();
    SELECT * INTO attempt FROM google_link_attempt WHERE attempt_id=p_attempt_id FOR UPDATE;
    IF NOT FOUND OR attempt.pending_change_id IS NOT NULL OR attempt.consumed_at IS NULL
        OR attempt.consumed_at > attempt.expires_at OR checked_at >= attempt.expires_at THEN RETURN FALSE; END IF;
    SELECT * INTO account_row FROM app_user WHERE user_id=attempt.user_id AND status='active' FOR UPDATE;
    IF NOT FOUND OR account_row.email IS NULL
        OR lower(btrim(account_row.email)) <> lower(btrim(p_verified_email)) THEN RETURN FALSE; END IF;
    PERFORM 1 FROM user_session WHERE session_id=attempt.session_id AND user_id=attempt.user_id
        AND revoked_at IS NULL AND checked_at < access_expires_at
        AND checked_at < absolute_expires_at
        AND checked_at < last_activity_at + (idle_timeout_seconds * interval '1 second') FOR UPDATE;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    PERFORM 1 FROM reauth_proof WHERE proof_id=attempt.reauth_proof_id
        AND user_id=attempt.user_id AND session_id=attempt.session_id
        AND operation='google_link' AND consumed_at IS NOT NULL AND consumed_at <= expires_at
        AND checked_at < expires_at;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    IF EXISTS (SELECT 1 FROM google_identity WHERE google_sub=p_google_sub AND user_id<>attempt.user_id)
        OR EXISTS (SELECT 1 FROM google_identity WHERE verified_email=lower(btrim(p_verified_email))
            AND user_id<>attempt.user_id)
        OR EXISTS (SELECT 1 FROM google_identity WHERE user_id=attempt.user_id AND google_sub<>p_google_sub) THEN
        RETURN FALSE;
    END IF;
    INSERT INTO google_identity(google_sub,user_id,verified_email)
        VALUES (p_google_sub,attempt.user_id,lower(btrim(p_verified_email))) ON CONFLICT DO NOTHING;
    IF NOT EXISTS (SELECT 1 FROM google_identity WHERE google_sub=p_google_sub
        AND user_id=attempt.user_id AND verified_email=lower(btrim(p_verified_email))) THEN RETURN FALSE; END IF;
    UPDATE app_user SET email_verified_at=coalesce(email_verified_at,checked_at)
        WHERE user_id=attempt.user_id AND lower(btrim(email))=lower(btrim(p_verified_email));
    INSERT INTO security_event(user_id,session_id,event_type,outcome,occurred_at)
        VALUES (attempt.user_id,attempt.session_id,'google_link','success',checked_at);
    RETURN TRUE;
EXCEPTION WHEN unique_violation THEN
    RETURN FALSE;
END;
$$;
