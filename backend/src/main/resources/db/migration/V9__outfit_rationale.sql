-- Persist the AI's plain-language rationale with the saved look, so the detail
-- view can show the same styling explanation the user saw when it was generated.
ALTER TABLE outfits ADD COLUMN rationale VARCHAR(2000);
