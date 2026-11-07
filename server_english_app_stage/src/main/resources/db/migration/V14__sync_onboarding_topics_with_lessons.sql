-- Lesson imports activate catalog topics that are actually present in lesson_content.
-- Keep old selections intact, but hide and reject seeded topics until a lesson uses them.
ALTER TABLE onboarding_topic
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE learner_preference_topic
    DROP CONSTRAINT learner_preference_topic_topic_id_fkey;

ALTER TABLE onboarding_topic
    ALTER COLUMN topic_id TYPE VARCHAR(80);

ALTER TABLE learner_preference_topic
    ALTER COLUMN topic_id TYPE VARCHAR(80);

ALTER TABLE learner_preference_topic
    ADD CONSTRAINT learner_preference_topic_topic_id_fkey
    FOREIGN KEY (topic_id) REFERENCES onboarding_topic(topic_id);

WITH lesson_topics AS (
    SELECT DISTINCT ON (topic_id) topic_id, label
    FROM (
        SELECT trim(both '_' from regexp_replace(lower(btrim(topic)), '[^a-z0-9]+', '_', 'g')) AS topic_id,
               btrim(topic) AS label
        FROM lesson_content
        WHERE topic IS NOT NULL AND btrim(topic) <> '' AND length(btrim(topic)) <= 80
    ) AS normalized
    WHERE topic_id <> ''
    ORDER BY topic_id, label
), numbered_topics AS (
    SELECT topic_id, label,
           ((SELECT COALESCE(MAX(sort_order), 0) FROM onboarding_topic)
               + row_number() OVER (ORDER BY topic_id))::smallint AS sort_order
    FROM lesson_topics
)
INSERT INTO onboarding_topic(topic_id, label, sort_order, active)
SELECT topic_id, label, sort_order, TRUE
FROM numbered_topics
ON CONFLICT (topic_id) DO UPDATE SET label = EXCLUDED.label, active = TRUE;
