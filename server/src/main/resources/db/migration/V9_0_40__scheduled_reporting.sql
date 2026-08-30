CREATE TABLE IF NOT EXISTS report_schedule (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    schedule_name TEXT NOT NULL,
    saved_report_name TEXT NOT NULL,
    frequency TEXT NOT NULL DEFAULT 'DAILY',
    day_of_week INTEGER,
    day_of_month INTEGER,
    month_of_year INTEGER,
    run_time TEXT NOT NULL DEFAULT '08:00',
    output_format TEXT NOT NULL DEFAULT 'PDF',
    delivery_mode TEXT NOT NULL DEFAULT 'EMAIL',
    recipients TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    next_run_at TEXT NOT NULL,
    last_run_at TEXT,
    last_status TEXT,
    last_error TEXT,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, schedule_name),
    CONSTRAINT report_schedule_frequency_ck CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','QUARTERLY','YEARLY')),
    CONSTRAINT report_schedule_format_ck CHECK (output_format IN ('PDF','XLSX','PDF_XLSX','CSV')),
    CONSTRAINT report_schedule_delivery_ck CHECK (delivery_mode IN ('EMAIL','ARCHIVE','EMAIL_ARCHIVE')),
    CONSTRAINT report_schedule_status_ck CHECK (status IN ('ACTIVE','PAUSED')),
    CONSTRAINT report_schedule_day_week_ck CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7),
    CONSTRAINT report_schedule_day_month_ck CHECK (day_of_month IS NULL OR day_of_month BETWEEN 1 AND 31),
    CONSTRAINT report_schedule_month_ck CHECK (month_of_year IS NULL OR month_of_year BETWEEN 1 AND 12)
);

CREATE INDEX IF NOT EXISTS idx_report_schedule_due
    ON report_schedule(status, next_run_at);
CREATE INDEX IF NOT EXISTS idx_report_schedule_user
    ON report_schedule(user_id, schedule_name);

CREATE TABLE IF NOT EXISTS report_schedule_run (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES report_schedule(id) ON DELETE CASCADE,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    status TEXT NOT NULL DEFAULT 'RUNNING',
    report_title TEXT,
    output_format TEXT,
    delivery_mode TEXT,
    row_count BIGINT NOT NULL DEFAULT 0,
    artifacts TEXT,
    error_message TEXT,
    triggered_by TEXT NOT NULL DEFAULT 'SCHEDULED'
);

CREATE INDEX IF NOT EXISTS idx_report_schedule_run_schedule
    ON report_schedule_run(schedule_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_report_schedule_run_status
    ON report_schedule_run(status, started_at DESC);
