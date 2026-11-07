-- The current database contains test onboarding state; reset it before exposing
-- preferences against the cleaned lesson-backed catalogs. Child topic selections
-- cascade from learner_preferences.
DELETE FROM learner_preferences;

DELETE FROM onboarding_topic WHERE active = FALSE;
DELETE FROM onboarding_band WHERE active = FALSE;
