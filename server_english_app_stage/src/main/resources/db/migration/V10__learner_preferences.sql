-- Stable machine IDs are independent of labels shown by the Android client.
CREATE TABLE onboarding_topic (
    topic_id VARCHAR(32) PRIMARY KEY,
    label VARCHAR(80) NOT NULL UNIQUE,
    sort_order SMALLINT NOT NULL UNIQUE CHECK (sort_order > 0)
);

INSERT INTO onboarding_topic(topic_id, label, sort_order) VALUES
    ('daily_life', 'Daily Life', 1),
    ('education', 'Education', 2),
    ('technology', 'Technology', 3),
    ('environment', 'Environment', 4),
    ('finance', 'Finance', 5),
    ('travel', 'Travel', 6),
    ('health', 'Health', 7),
    ('work', 'Work', 8);

CREATE TABLE learner_preferences (
    user_id UUID PRIMARY KEY REFERENCES app_user(user_id) ON DELETE CASCADE,
    band VARCHAR(16) NOT NULL CHECK (band IN ('BELOW_4_0', '4_0_5_0', '5_5_6_5', '7_0_PLUS')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE learner_preference_topic (
    user_id UUID NOT NULL REFERENCES learner_preferences(user_id) ON DELETE CASCADE,
    topic_id VARCHAR(32) NOT NULL REFERENCES onboarding_topic(topic_id),
    position SMALLINT NOT NULL CHECK (position >= 0),
    PRIMARY KEY (user_id, topic_id),
    UNIQUE (user_id, position)
);
