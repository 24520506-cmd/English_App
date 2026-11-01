-- A single ownership registry closes the cross-table race between profile
-- verification and Google linking. Existing verified contacts are backfilled.
CREATE TABLE verified_contact_claim (
    contact_kind VARCHAR(10) NOT NULL CHECK (contact_kind IN ('email', 'phone')),
    normalized_value VARCHAR(320) NOT NULL,
    user_id UUID NOT NULL REFERENCES app_user(user_id),
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (contact_kind, normalized_value),
    CHECK (length(normalized_value) > 0)
);
CREATE INDEX idx_verified_claim_owner ON verified_contact_claim(user_id);

INSERT INTO verified_contact_claim(contact_kind, normalized_value, user_id)
    SELECT 'email', lower(btrim(email)), user_id FROM app_user
    WHERE email_verified_at IS NOT NULL;
INSERT INTO verified_contact_claim(contact_kind, normalized_value, user_id)
    SELECT 'phone', phone, user_id FROM app_user
    WHERE phone_verified_at IS NOT NULL;
INSERT INTO verified_contact_claim(contact_kind, normalized_value, user_id)
    SELECT 'email', verified_email, user_id FROM google_identity
    ON CONFLICT (contact_kind, normalized_value) DO NOTHING;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM google_identity g JOIN verified_contact_claim c
        ON c.contact_kind = 'email' AND c.normalized_value = g.verified_email
        WHERE c.user_id <> g.user_id) THEN
        RAISE EXCEPTION 'Conflicting verified Google email ownership requires manual remediation';
    END IF;
END;
$$;

CREATE FUNCTION claim_verified_contact(p_kind VARCHAR, p_value VARCHAR, p_user_id UUID)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    holder UUID;
BEGIN
    INSERT INTO verified_contact_claim(contact_kind, normalized_value, user_id)
        VALUES (p_kind, p_value, p_user_id)
        ON CONFLICT (contact_kind, normalized_value) DO NOTHING;
    SELECT user_id INTO holder FROM verified_contact_claim
        WHERE contact_kind = p_kind AND normalized_value = p_value;
    IF holder IS DISTINCT FROM p_user_id THEN
        RAISE unique_violation USING MESSAGE = 'Verified contact belongs to another account';
    END IF;
END;
$$;

CREATE FUNCTION release_verified_contact(p_kind VARCHAR, p_value VARCHAR, p_user_id UUID)
RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    IF p_value IS NULL THEN RETURN; END IF;
    IF p_kind = 'email' THEN
        IF EXISTS (SELECT 1 FROM app_user WHERE user_id = p_user_id
            AND email_verified_at IS NOT NULL AND lower(btrim(email)) = p_value)
            OR EXISTS (SELECT 1 FROM google_identity WHERE user_id = p_user_id
                AND verified_email = p_value) THEN RETURN; END IF;
    ELSE
        IF EXISTS (SELECT 1 FROM app_user WHERE user_id = p_user_id
            AND phone_verified_at IS NOT NULL AND phone = p_value) THEN RETURN; END IF;
    END IF;
    DELETE FROM verified_contact_claim WHERE contact_kind = p_kind
        AND normalized_value = p_value AND user_id = p_user_id;
END;
$$;

CREATE FUNCTION sync_app_user_verified_contacts() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.email_verified_at IS NOT NULL THEN
        PERFORM claim_verified_contact('email', lower(btrim(NEW.email)), NEW.user_id);
    END IF;
    IF NEW.phone_verified_at IS NOT NULL THEN
        PERFORM claim_verified_contact('phone', NEW.phone, NEW.user_id);
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.email_verified_at IS NOT NULL THEN
            PERFORM release_verified_contact('email', lower(btrim(OLD.email)), OLD.user_id);
        END IF;
        IF OLD.phone_verified_at IS NOT NULL THEN
            PERFORM release_verified_contact('phone', OLD.phone, OLD.user_id);
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_app_user_verified_contacts
    AFTER INSERT OR UPDATE OF email, email_verified_at, phone, phone_verified_at
    ON app_user FOR EACH ROW EXECUTE FUNCTION sync_app_user_verified_contacts();

CREATE FUNCTION sync_google_verified_email() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'DELETE' THEN
        PERFORM claim_verified_contact('email', NEW.verified_email, NEW.user_id);
    END IF;
    IF TG_OP <> 'INSERT' THEN
        PERFORM release_verified_contact('email', OLD.verified_email, OLD.user_id);
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_google_verified_email
    AFTER INSERT OR UPDATE OF verified_email, user_id OR DELETE
    ON google_identity FOR EACH ROW EXECUTE FUNCTION sync_google_verified_email();

-- Start checks ownership before sending a candidate OTP. Completion rechecks
-- after proof, because another account may claim the contact in the meantime.
CREATE FUNCTION start_contact_change(
    p_user_id UUID, p_session_id UUID, p_proof_hash CHAR(64),
    p_contact_kind VARCHAR, p_candidate_value VARCHAR, p_channel VARCHAR
) RETURNS UUID LANGUAGE plpgsql AS $$
DECLARE
    proof reauth_proof%ROWTYPE;
    account app_user%ROWTYPE;
    session_row user_session%ROWTYPE;
    new_change_id UUID := gen_random_uuid();
    checked_at TIMESTAMPTZ;
BEGIN
    SELECT * INTO account FROM app_user WHERE user_id = p_user_id FOR UPDATE;
    IF NOT FOUND OR account.status <> 'active' THEN RETURN NULL; END IF;
    SELECT * INTO session_row FROM user_session
        WHERE session_id = p_session_id AND user_id = p_user_id FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;
    checked_at := clock_timestamp();
    IF session_row.revoked_at IS NOT NULL
        OR checked_at >= session_row.absolute_expires_at
        OR checked_at >= session_row.last_activity_at
            + (session_row.idle_timeout_seconds * interval '1 second') THEN RETURN NULL; END IF;
    SELECT * INTO proof FROM reauth_proof WHERE proof_hash = p_proof_hash
        AND user_id = p_user_id AND session_id = p_session_id
        AND operation = 'contact_change' FOR UPDATE;
    IF NOT FOUND THEN RETURN NULL; END IF;
    checked_at := clock_timestamp();
    IF proof.consumed_at IS NOT NULL OR checked_at >= proof.expires_at THEN
        RETURN NULL;
    END IF;
    IF session_row.revoked_at IS NOT NULL
        OR checked_at >= session_row.absolute_expires_at
        OR checked_at >= session_row.last_activity_at
            + (session_row.idle_timeout_seconds * interval '1 second') THEN RETURN NULL; END IF;
    IF p_contact_kind = 'email' AND lower(btrim(account.email)) IS NOT DISTINCT FROM p_candidate_value
        OR p_contact_kind = 'phone' AND account.phone IS NOT DISTINCT FROM p_candidate_value THEN
        RETURN NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM verified_contact_claim WHERE contact_kind = p_contact_kind
        AND normalized_value = p_candidate_value AND user_id <> p_user_id) THEN
        RETURN NULL;
    END IF;
    UPDATE pending_contact_change SET invalidated_at = checked_at
        WHERE user_id = p_user_id AND contact_kind = p_contact_kind
        AND completed_at IS NULL AND invalidated_at IS NULL;
    UPDATE reauth_proof SET consumed_at = checked_at WHERE proof_id = proof.proof_id;
    INSERT INTO pending_contact_change(change_id, user_id, contact_kind, candidate_value,
        channel, created_at, expires_at, reauth_proof_id)
        VALUES (new_change_id, p_user_id, p_contact_kind, p_candidate_value,
            p_channel, checked_at, checked_at + interval '10 minutes', proof.proof_id);
    RETURN new_change_id;
END;
$$;
