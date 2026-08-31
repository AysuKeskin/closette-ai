-- Persist the raw onboarding selections so the app can show a faithful recap:
-- which aesthetic cards the user loved, and their "dressing up" answer. The
-- existing preferred_styles column keeps the derived tags used for recommendations.
ALTER TABLE style_preferences ADD COLUMN loved_aesthetics VARCHAR(1000);
ALTER TABLE style_preferences ADD COLUMN dress_up VARCHAR(60);
