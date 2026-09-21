CREATE TABLE stored_images (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    bucket VARCHAR(255) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    delete_after TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(bucket, object_key)
);
CREATE INDEX idx_stored_images_cleanup ON stored_images(delete_after);
CREATE INDEX idx_stored_images_user ON stored_images(user_id);

-- Old uploads without a product row are discovered by the reconciliation job.
INSERT INTO stored_images (id, user_id, bucket, object_key, created_at, updated_at)
SELECT gen_random_uuid(), user_id, bucket, image_key, now(), now()
FROM (
    SELECT user_id, 'wardrobe' AS bucket, image_key FROM wardrobe_items
    UNION SELECT user_id, 'wardrobe', image_key FROM wishlist_items
    UNION SELECT user_id, 'beauty', image_key FROM beauty_items
) owned
WHERE image_key LIKE user_id::text || '/%'
ON CONFLICT (bucket, object_key) DO NOTHING;
