-- Two shared caches. Neither carries a user_id, and that is deliberate: both hold
-- public catalogue facts, not personal data. A product's ingredient list and what
-- "Niacinamide" means are the same for everyone, so storing them per user would
-- copy identical rows and, for the explanations, pay the model again each time.
--
-- What stays private is unchanged: which products someone owns lives in
-- beauty_items, scoped by user_id as every other user table is.

-- Products seen from an external catalogue (currently Open Beauty Facts), kept so
-- a repeat search is answered from here. The upstream allows only a handful of
-- searches a minute per IP, and every user of this app shares one IP, so without
-- this the whole app runs on that single budget.
CREATE TABLE beauty_catalogue (
    barcode      VARCHAR(64) PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL,
    brand        VARCHAR(255),
    category     VARCHAR(32),
    ingredients  TEXT,
    image_url    VARCHAR(1024),
    source       VARCHAR(32) NOT NULL,
    fetched_at   TIMESTAMPTZ NOT NULL
);

-- Search is a case-insensitive contains over name and brand, which is what the
-- lookup does today against the upstream.
CREATE INDEX idx_beauty_catalogue_name ON beauty_catalogue (lower(product_name));
CREATE INDEX idx_beauty_catalogue_brand ON beauty_catalogue (lower(brand));
CREATE INDEX idx_beauty_catalogue_fetched ON beauty_catalogue (fetched_at);

-- One explanation per ingredient per language. The language is part of the key
-- because the prose is translated while the ingredient name is not.
CREATE TABLE ingredient_explanations (
    inci_name   VARCHAR(255) NOT NULL,
    lang        VARCHAR(8)   NOT NULL,
    explanation TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (inci_name, lang)
);
