CREATE TABLE lesson_content (
    content_id UUID PRIMARY KEY,
    source_key VARCHAR(500) NOT NULL UNIQUE,
    lesson_id VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    title VARCHAR(300) NOT NULL,
    band VARCHAR(100),
    topic VARCHAR(150),
    status VARCHAR(30) NOT NULL,
    payload JSONB NOT NULL,
    audio_relative_path VARCHAR(500),
    payload_checksum_sha256 CHAR(64) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lesson_content_band_topic ON lesson_content(band, topic);
CREATE INDEX idx_lesson_content_lesson_id ON lesson_content(lesson_id);
