CREATE TABLE fighters (
    id UUID PRIMARY KEY,
    slug VARCHAR NOT NULL UNIQUE,
    name VARCHAR NOT NULL,
    nickname VARCHAR,
    record_wins INTEGER,
    record_losses INTEGER,
    record_draws INTEGER,
    weight_class VARCHAR,
    height_inches VARCHAR,
    reach_inches VARCHAR,
    stance VARCHAR,
    country VARCHAR,
    source VARCHAR NOT NULL,
    source_id VARCHAR NOT NULL,
    raw_payload JSONB,
    manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fighters_source UNIQUE (source, source_id)
);
CREATE INDEX idx_fighters_slug ON fighters (slug);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    slug VARCHAR NOT NULL UNIQUE,
    name VARCHAR NOT NULL,
    starts_at TIMESTAMPTZ,
    venue VARCHAR,
    city VARCHAR,
    country VARCHAR,
    status VARCHAR NOT NULL,
    source VARCHAR NOT NULL,
    source_id VARCHAR NOT NULL,
    raw_payload JSONB,
    manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_events_source UNIQUE (source, source_id)
);
CREATE INDEX idx_events_slug ON events (slug);
CREATE INDEX idx_events_starts_at ON events (starts_at);
CREATE INDEX idx_events_status ON events (status);

CREATE TABLE fights (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events (id),
    red_fighter_id UUID REFERENCES fighters (id),
    blue_fighter_id UUID REFERENCES fighters (id),
    weight_class VARCHAR,
    card_section VARCHAR,
    bout_order INTEGER,
    is_title_fight BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR NOT NULL,
    winner_fighter_id UUID REFERENCES fighters (id),
    method VARCHAR,
    result_round INTEGER,
    result_time VARCHAR,
    starts_at TIMESTAMPTZ,
    locked_at TIMESTAMPTZ,
    source VARCHAR NOT NULL,
    source_id VARCHAR NOT NULL,
    raw_payload JSONB,
    manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fights_source UNIQUE (source, source_id)
);
CREATE INDEX idx_fights_event_id ON fights (event_id);
CREATE INDEX idx_fights_status ON fights (status);

CREATE TABLE rankings (
    id UUID PRIMARY KEY,
    division VARCHAR NOT NULL,
    rank INTEGER,
    fighter_id UUID NOT NULL REFERENCES fighters (id),
    is_champion BOOLEAN NOT NULL DEFAULT FALSE,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rankings_division ON rankings (division);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR NOT NULL UNIQUE,
    username VARCHAR NOT NULL UNIQUE,
    password_hash VARCHAR,
    google_sub VARCHAR UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    token_hash VARCHAR NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE picks (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    fight_id UUID NOT NULL REFERENCES fights (id),
    picked_fighter_id UUID NOT NULL REFERENCES fighters (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    is_correct BOOLEAN,
    CONSTRAINT uq_picks_user_fight UNIQUE (user_id, fight_id)
);
CREATE INDEX idx_picks_user_id ON picks (user_id);
CREATE INDEX idx_picks_fight_id ON picks (fight_id);

CREATE TABLE follows (
    follower_id UUID NOT NULL REFERENCES users (id),
    followee_id UUID NOT NULL REFERENCES users (id),
    PRIMARY KEY (follower_id, followee_id)
);

CREATE TABLE api_usage (
    id UUID PRIMARY KEY,
    source VARCHAR NOT NULL,
    month VARCHAR NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_api_usage_source_month UNIQUE (source, month)
);
