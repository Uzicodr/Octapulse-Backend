-- Headlines pulled from publisher RSS feeds by the agent's sync_news job.
-- Only what the feed itself provides is stored, unchanged; the app links out to url for the full story.
CREATE TABLE news_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source VARCHAR(32) NOT NULL,
    source_name VARCHAR(64) NOT NULL,
    url VARCHAR(1024) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    summary TEXT,
    image_url VARCHAR(1024),
    kind VARCHAR(16) NOT NULL DEFAULT 'news',
    published_at TIMESTAMPTZ NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_news_items_kind CHECK (kind IN ('announcement', 'result', 'injury', 'rumor', 'news'))
);
CREATE INDEX idx_news_items_published_at ON news_items (published_at DESC);

-- Fighters named in a story, so fighter pages can show their own news.
CREATE TABLE news_item_fighters (
    news_id UUID NOT NULL REFERENCES news_items (id) ON DELETE CASCADE,
    fighter_id UUID NOT NULL REFERENCES fighters (id) ON DELETE CASCADE,
    PRIMARY KEY (news_id, fighter_id)
);
CREATE INDEX idx_news_item_fighters_fighter_id ON news_item_fighters (fighter_id);
