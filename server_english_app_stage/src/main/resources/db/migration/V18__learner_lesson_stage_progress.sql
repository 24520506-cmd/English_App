CREATE TABLE lesson_stage_progress (
    user_id UUID NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES lesson_content(content_id) ON DELETE CASCADE,
    stage_id VARCHAR(32) NOT NULL CHECK (stage_id IN ('stage_1', 'stage_2')),
    completed BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, content_id, stage_id)
);
