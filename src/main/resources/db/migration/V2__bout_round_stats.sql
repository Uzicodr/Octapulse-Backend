CREATE TABLE bout_round_stats (
    id UUID PRIMARY KEY,
    fight_id UUID NOT NULL REFERENCES fights (id),
    fighter_id UUID NOT NULL REFERENCES fighters (id),
    round INTEGER NOT NULL,
    strikes_landed INTEGER,
    strikes_attempted INTEGER,
    sig_strikes_landed INTEGER,
    sig_strikes_attempted INTEGER,
    takedowns_landed INTEGER,
    takedowns_attempted INTEGER,
    control_time_seconds INTEGER,
    source VARCHAR NOT NULL,
    source_id VARCHAR NOT NULL,
    raw_payload JSONB,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_bout_round_stats_fight_fighter_round UNIQUE (fight_id, fighter_id, round),
    CONSTRAINT uq_bout_round_stats_source UNIQUE (source, source_id)
);
CREATE INDEX idx_bout_round_stats_fight_id ON bout_round_stats (fight_id);
