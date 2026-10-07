-- Which kinds of push a user wants. No row means everything on. Notifications still land in the
-- in-app inbox when a kind is off; only the push is skipped.
CREATE TABLE notification_settings (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    live BOOLEAN NOT NULL DEFAULT TRUE,
    results BOOLEAN NOT NULL DEFAULT TRUE,
    news BOOLEAN NOT NULL DEFAULT TRUE,
    announcements BOOLEAN NOT NULL DEFAULT TRUE,
    reminders BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_type_created ON notifications (user_id, type, created_at DESC);
