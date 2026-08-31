-- Beauty products added via name search carry an external image URL (Makeup API /
-- Open Beauty Facts CDN) rather than a MinIO upload, so persist it for display.
ALTER TABLE beauty_items ADD COLUMN image_url VARCHAR(1000);
