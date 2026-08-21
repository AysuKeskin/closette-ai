-- Switch embeddings to 768-dim to match the real model (Marqo-FashionSigLIP,
-- ViT-B-16-SigLIP). Old 512-dim mock vectors were not meaningful, so drop them.
DROP INDEX IF EXISTS idx_wardrobe_embedding;

ALTER TABLE wardrobe_items DROP COLUMN IF EXISTS embedding;
ALTER TABLE wardrobe_items ADD COLUMN embedding vector(768);

ALTER TABLE beauty_items DROP COLUMN IF EXISTS embedding;
ALTER TABLE beauty_items ADD COLUMN embedding vector(768);

CREATE INDEX idx_wardrobe_embedding ON wardrobe_items USING hnsw (embedding vector_cosine_ops);
