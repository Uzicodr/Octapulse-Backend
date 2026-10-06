CREATE TABLE agent_runs (
    id UUID PRIMARY KEY,
    job VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    input JSONB,
    summary JSONB,
    error TEXT,
    provider VARCHAR,
    model VARCHAR,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);
CREATE INDEX idx_agent_runs_job_started_at ON agent_runs (job, started_at DESC);

CREATE TABLE agent_steps (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_runs (id) ON DELETE CASCADE,
    step INTEGER NOT NULL,
    kind VARCHAR NOT NULL,
    name VARCHAR,
    args JSONB,
    result JSONB,
    is_error BOOLEAN NOT NULL DEFAULT FALSE,
    provider VARCHAR,
    model VARCHAR,
    input_tokens INTEGER,
    output_tokens INTEGER,
    duration_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_agent_steps_run_id ON agent_steps (run_id, step);

CREATE TABLE review_queue (
    id UUID PRIMARY KEY,
    run_id UUID REFERENCES agent_runs (id) ON DELETE SET NULL,
    entity_type VARCHAR NOT NULL,
    entity_id UUID,
    reason TEXT NOT NULL,
    payload JSONB,
    status VARCHAR NOT NULL DEFAULT 'open',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_review_queue_status ON review_queue (status, created_at);
