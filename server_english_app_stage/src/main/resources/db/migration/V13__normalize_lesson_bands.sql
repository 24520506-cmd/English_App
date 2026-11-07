WITH dataset_bands AS (
    SELECT content_id,
           CASE split_part(source_key, '/', 1)
               WHEN '5.5-6.0' THEN '5.5-6.0'
               WHEN '6.0-6.5' THEN '6.0-6.5'
               WHEN '6.5-7.0' THEN '6.5-7.0'
               WHEN '7.0-7.5' THEN '7.0-7.5'
               WHEN '7.5-8.0' THEN '7.5-8.0'
               WHEN '8+' THEN '8+'
           END AS band,
           CASE split_part(source_key, '/', 1)
               WHEN '5.5-6.0' THEN '5_5_6_0'
               WHEN '6.0-6.5' THEN '6_0_6_5'
               WHEN '6.5-7.0' THEN '6_5_7_0'
               WHEN '7.0-7.5' THEN '7_0_7_5'
               WHEN '7.5-8.0' THEN '7_5_8_0'
               WHEN '8+' THEN '8_0_PLUS'
           END AS band_id
    FROM lesson_content
    WHERE split_part(source_key, '/', 1) IN (
        '5.5-6.0', '6.0-6.5', '6.5-7.0', '7.0-7.5', '7.5-8.0', '8+'
    )
)
UPDATE lesson_content AS lesson
SET band = dataset_bands.band,
    payload = (lesson.payload - 'level')
        || jsonb_build_object('band', dataset_bands.band, 'band_id', dataset_bands.band_id)
        || CASE
            WHEN lesson.payload ? 'level'
                 AND NOT (lesson.payload ? 'cefr_reference')
                 AND lesson.payload->>'level' !~ '^[0-9]'
            THEN jsonb_build_object('cefr_reference', lesson.payload->'level')
            ELSE '{}'::jsonb
        END,
    updated_at = now()
FROM dataset_bands
WHERE lesson.content_id = dataset_bands.content_id;
