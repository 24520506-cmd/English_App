CREATE TABLE onboarding_band (
    band_id VARCHAR(80) PRIMARY KEY,
    label VARCHAR(80) NOT NULL UNIQUE,
    sort_order SMALLINT NOT NULL UNIQUE CHECK (sort_order > 0),
    active BOOLEAN NOT NULL DEFAULT FALSE
);

-- Preserve existing saved values as inactive legacy rows until learners choose a current lesson band.
INSERT INTO onboarding_band(band_id, label, sort_order, active) VALUES
    ('BELOW_4_0', 'Below 4.0', 30000, FALSE),
    ('4_0_5_0', '4.0–5.0', 30001, FALSE),
    ('5_5_6_5', '5.5–6.5', 30002, FALSE),
    ('7_0_PLUS', '7.0+', 30003, FALSE);

ALTER TABLE learner_preferences
    DROP CONSTRAINT learner_preferences_band_check;

ALTER TABLE learner_preferences
    ALTER COLUMN band TYPE VARCHAR(80);

ALTER TABLE learner_preferences
    ADD CONSTRAINT learner_preferences_band_fkey
    FOREIGN KEY (band) REFERENCES onboarding_band(band_id);

ALTER TABLE lesson_content
    ADD COLUMN band_id VARCHAR(80);

UPDATE lesson_content
SET band_id = COALESCE(NULLIF(payload->>'band_id', ''),
    CASE
        WHEN right(band, 1) = '+'
            THEN replace(left(band, length(band)-1), '.', '_') || '_PLUS'
        ELSE replace(replace(band, '.', '_'), '-', '_')
    END)
WHERE band IS NOT NULL AND btrim(band) <> '';

CREATE INDEX idx_lesson_content_band_id ON lesson_content(band_id);

WITH lesson_bands AS (
    SELECT DISTINCT band_id, band AS label
    FROM lesson_content
    WHERE band_id IS NOT NULL AND band IS NOT NULL AND length(band) <= 80
), numbered_bands AS (
    SELECT band_id, label, row_number() OVER (ORDER BY label, band_id)::smallint AS sort_order
    FROM lesson_bands
)
INSERT INTO onboarding_band(band_id, label, sort_order, active)
SELECT band_id, label, sort_order, TRUE
FROM numbered_bands
ON CONFLICT (band_id) DO UPDATE SET label = EXCLUDED.label, active = TRUE;
