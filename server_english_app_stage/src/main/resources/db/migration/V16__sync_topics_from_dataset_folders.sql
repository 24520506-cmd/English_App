ALTER TABLE lesson_content
    ADD COLUMN topic_id VARCHAR(80);

UPDATE lesson_content
SET topic_id = trim(both '_' from regexp_replace(lower(
        CASE
            WHEN array_length(string_to_array(source_key, '/'), 1) >= 3 THEN split_part(source_key, '/', 2)
            WHEN array_length(string_to_array(source_key, '/'), 1) = 2 THEN split_part(source_key, '/', 1)
            ELSE ''
        END), '[^a-z0-9]+', '_', 'g'))
WHERE source_key IS NOT NULL;

CREATE INDEX idx_lesson_content_topic_id ON lesson_content(topic_id);

UPDATE onboarding_topic SET active = FALSE;

WITH dataset_topics AS (
    SELECT DISTINCT topic_id,
           initcap(replace(replace(folder_name, '_', ' '), '-', ' ')) AS label
    FROM (
        SELECT lesson.topic_id,
               CASE
                   WHEN array_length(string_to_array(lesson.source_key, '/'), 1) >= 3
                       THEN split_part(lesson.source_key, '/', 2)
                   WHEN array_length(string_to_array(lesson.source_key, '/'), 1) = 2
                       THEN split_part(lesson.source_key, '/', 1)
                   ELSE ''
               END AS folder_name
        FROM lesson_content lesson
        WHERE lesson.topic_id IS NOT NULL AND lesson.topic_id <> ''
    ) folders
    WHERE length(topic_id) <= 80 AND length(folder_name) <= 80
), numbered_topics AS (
    SELECT topic_id, label,
           ((SELECT COALESCE(MAX(sort_order), 0) FROM onboarding_topic)
               + row_number() OVER (ORDER BY label, topic_id))::smallint AS sort_order
    FROM dataset_topics
)
INSERT INTO onboarding_topic(topic_id, label, sort_order, active)
SELECT topic_id, label, sort_order, TRUE
FROM numbered_topics
ON CONFLICT (topic_id) DO UPDATE SET label = EXCLUDED.label, active = TRUE;
