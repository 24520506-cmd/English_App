ALTER TABLE otp_challenge DROP CONSTRAINT otp_challenge_purpose_check;
ALTER TABLE otp_challenge ADD CONSTRAINT ck_otp_challenge_purpose CHECK (
    purpose IN ('password_reset', 'phone_verification', 'email_verification', 'contact_change'));

CREATE FUNCTION complete_email_verification(
    p_user_id UUID, p_challenge_id UUID, p_email VARCHAR, p_verification_hash CHAR(64)
) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
    result TEXT;
BEGIN
    PERFORM 1 FROM app_user
        WHERE user_id = p_user_id AND lower(btrim(email)) = lower(btrim(p_email)) FOR UPDATE;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    result := consume_user_otp(p_user_id, p_challenge_id, 'email_verification',
        'email', lower(btrim(p_email)), 'gmail', p_verification_hash);
    IF result <> 'consumed' THEN RETURN FALSE; END IF;
    UPDATE app_user SET email = lower(btrim(email)), email_verified_at = clock_timestamp()
        WHERE user_id = p_user_id;
    RETURN TRUE;
EXCEPTION WHEN unique_violation THEN
    RETURN FALSE;
END;
$$;
