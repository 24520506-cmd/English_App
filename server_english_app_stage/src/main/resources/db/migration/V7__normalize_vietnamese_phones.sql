-- Verification requires E.164. Normalize legacy Vietnamese mobile numbers that
-- registration previously accepted in their local 0xxxxxxxxx form.
UPDATE app_user
SET phone = '+84' || substring(phone FROM 2)
WHERE phone ~ '^0[0-9]{9}$';
