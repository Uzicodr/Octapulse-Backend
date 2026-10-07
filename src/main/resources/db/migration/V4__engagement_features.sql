-- Profiles and roles
ALTER TABLE users ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'user';
ALTER TABLE users ADD COLUMN display_name VARCHAR(64);
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(512);
ALTER TABLE users ADD COLUMN bio VARCHAR(280);

-- The agent publishes its own picks as this user so it shows up on leaderboards.
INSERT INTO users (id, email, username, role, display_name, created_at)
VALUES ('00000000-0000-0000-0000-00000000a1a1', 'ai@octapulse.local', 'octapulse_ai', 'agent', 'Octapulse AI', now());

-- Richer picks and settlement bookkeeping
ALTER TABLE picks ADD COLUMN method VARCHAR(16);
ALTER TABLE picks ADD COLUMN round INTEGER;
ALTER TABLE picks ADD COLUMN confidence INTEGER NOT NULL DEFAULT 1;
ALTER TABLE picks ADD COLUMN points INTEGER;
ALTER TABLE picks ADD COLUMN settled_at TIMESTAMPTZ;
ALTER TABLE picks ADD CONSTRAINT ck_picks_confidence CHECK (confidence BETWEEN 1 AND 3);
CREATE INDEX idx_picks_unsettled ON picks (fight_id) WHERE settled_at IS NULL;

-- Existing settled picks predate points; give them the base score.
UPDATE picks SET settled_at = now(), points = CASE WHEN is_correct THEN 1 ELSE 0 END
WHERE is_correct IS NOT NULL;

-- User-to-user follows already exist; record when they happened.
ALTER TABLE follows ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX idx_follows_followee_id ON follows (followee_id);

CREATE TABLE fighter_follows (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    fighter_id UUID NOT NULL REFERENCES fighters (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, fighter_id)
);
CREATE INDEX idx_fighter_follows_fighter_id ON fighter_follows (fighter_id);

-- Private leagues
CREATE TABLE leagues (
    id UUID PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    invite_code VARCHAR(16) NOT NULL UNIQUE,
    owner_id UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE league_members (
    league_id UUID NOT NULL REFERENCES leagues (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (league_id, user_id)
);
CREATE INDEX idx_league_members_user_id ON league_members (user_id);

-- Fight comments
CREATE TABLE comments (
    id UUID PRIMARY KEY,
    fight_id UUID NOT NULL REFERENCES fights (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_comments_fight_id ON comments (fight_id, created_at DESC);

-- In-app notifications; dedupe_key stops a job from notifying twice about the same thing.
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(1000),
    data JSONB,
    dedupe_key VARCHAR(128) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_user_dedupe UNIQUE (user_id, dedupe_key)
);
CREATE INDEX idx_notifications_user_id ON notifications (user_id, created_at DESC);

CREATE TABLE device_tokens (
    token VARCHAR(512) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_tokens_user_id ON device_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

-- Written by the agent, served read-only here.
CREATE TABLE fight_previews (
    fight_id UUID PRIMARY KEY REFERENCES fights (id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    model VARCHAR,
    run_id UUID REFERENCES agent_runs (id) ON DELETE SET NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
