-- Photo credits for news. image_credit is the photographer or owner; image_license and
-- image_credit_url are set for photos the agent adds itself (Wikimedia Commons, Unsplash),
-- where the license asks for a credit with a link.
ALTER TABLE news_items ADD COLUMN image_credit VARCHAR(200);
ALTER TABLE news_items ADD COLUMN image_license VARCHAR(64);
ALTER TABLE news_items ADD COLUMN image_credit_url VARCHAR(1024);

-- The agent's cache of freely licensed fighter photos. A row with a null image_url means
-- "looked, found none"; the agent checks again after checked_at gets old.
CREATE TABLE fighter_photos (
    fighter_id UUID PRIMARY KEY REFERENCES fighters (id) ON DELETE CASCADE,
    image_url VARCHAR(1024),
    credit VARCHAR(200),
    license VARCHAR(64),
    credit_url VARCHAR(1024),
    checked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
