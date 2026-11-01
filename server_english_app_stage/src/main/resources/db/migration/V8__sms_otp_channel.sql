-- Preserve already-applied V4/V5 checksums while extending phone OTP delivery
-- from the original Zalo-only design to SMS.
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT conrelid::regclass AS table_name, conname
        FROM pg_constraint
        WHERE contype = 'c'
          AND conrelid IN ('pending_contact_change'::regclass,
                           'otp_challenge'::regclass,
                           'otp_send_attempt'::regclass)
          AND pg_get_constraintdef(oid) ILIKE '%channel%'
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', c.table_name, c.conname);
    END LOOP;
END $$;

ALTER TABLE pending_contact_change
    ADD CONSTRAINT ck_pending_contact_channel CHECK (channel IN ('gmail', 'zalo', 'sms')),
    ADD CONSTRAINT ck_pending_contact_pair CHECK (
        (contact_kind = 'email' AND channel = 'gmail' AND candidate_value = lower(btrim(candidate_value)))
        OR (contact_kind = 'phone' AND channel IN ('zalo', 'sms')
            AND candidate_value ~ '^\+[1-9][0-9]{8,14}$'));

ALTER TABLE otp_challenge
    ADD CONSTRAINT ck_otp_challenge_channel CHECK (channel IN ('gmail', 'zalo', 'sms')),
    ADD CONSTRAINT ck_otp_challenge_pair CHECK (
        (channel = 'gmail' AND contact_kind = 'email')
        OR (channel IN ('zalo', 'sms') AND contact_kind = 'phone'));

ALTER TABLE otp_send_attempt
    ADD CONSTRAINT ck_otp_send_channel CHECK (channel IN ('gmail', 'zalo', 'sms')),
    ADD CONSTRAINT ck_otp_send_pair CHECK (
        (channel = 'gmail' AND contact_kind = 'email')
        OR (channel IN ('zalo', 'sms') AND contact_kind = 'phone'));

CREATE OR REPLACE FUNCTION complete_phone_verification(
    p_user_id UUID, p_challenge_id UUID, p_phone VARCHAR, p_verification_hash CHAR(64)
) RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
    result TEXT;
BEGIN
    PERFORM 1 FROM app_user WHERE user_id = p_user_id AND phone = p_phone FOR UPDATE;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    result := consume_user_otp(p_user_id, p_challenge_id, 'phone_verification',
        'phone', p_phone, 'sms', p_verification_hash);
    IF result <> 'consumed' THEN RETURN FALSE; END IF;
    UPDATE app_user SET phone_verified_at = clock_timestamp() WHERE user_id = p_user_id;
    RETURN TRUE;
EXCEPTION WHEN unique_violation THEN
    RETURN FALSE;
END;
$$;
