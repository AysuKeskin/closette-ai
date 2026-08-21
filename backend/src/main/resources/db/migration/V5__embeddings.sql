-- Visual-similarity vectors (FR "do I own something like this?"). 512-dim to match
-- the AI service's embedding contract (mock now, Marqo-FashionSigLIP later).
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE wardrobe_items ADD COLUMN embedding vector(512);
ALTER TABLE beauty_items ADD COLUMN embedding vector(512);

-- HNSW cosine index — no training needed, good for incremental inserts.
CREATE INDEX idx_wardrobe_embedding ON wardrobe_items USING hnsw (embedding vector_cosine_ops);
