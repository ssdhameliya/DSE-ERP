-- DSE ERP 10.0.0 notification hardening.
-- Notification rows remain immutable business events; preferences/read/dismissed state are per user.

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS recipient_user_id BIGINT;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_notifications_recipient_user'
    ) THEN
        ALTER TABLE notifications
            ADD CONSTRAINT fk_notifications_recipient_user
            FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON notifications(recipient_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS notification_preference (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    preference_key VARCHAR(80) NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, preference_key)
);

CREATE INDEX IF NOT EXISTS idx_notification_preference_user
    ON notification_preference(user_id, preference_key);

CREATE TABLE IF NOT EXISTS notification_user_state (
    notification_id BIGINT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_read INTEGER NOT NULL DEFAULT 0,
    is_dismissed INTEGER NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (notification_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_notification_user_state_user
    ON notification_user_state(user_id, is_dismissed, is_read, notification_id);

-- Preserve the pre-10.0.0 global read state at upgrade time. After this migration,
-- each user owns their own read/dismissed state and users no longer affect one another.
INSERT INTO notification_user_state(notification_id, user_id, is_read, is_dismissed, updated_at)
SELECT n.id, u.id, 1, 0, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
FROM notifications n
CROSS JOIN users u
WHERE COALESCE(n.is_read::text, '0') IN ('1','true','t')
ON CONFLICT (notification_id, user_id) DO NOTHING;
