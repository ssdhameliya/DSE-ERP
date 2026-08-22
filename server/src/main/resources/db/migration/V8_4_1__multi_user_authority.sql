CREATE TABLE IF NOT EXISTS reference_counter (
    counter_key VARCHAR(160) PRIMARY KEY,
    next_value BIGINT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS server_resource (
    resource_type VARCHAR(40) NOT NULL,
    resource_key VARCHAR(240) NOT NULL,
    file_name VARCHAR(255),
    content_type VARCHAR(160),
    content BYTEA NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(120),
    PRIMARY KEY (resource_type, resource_key)
);

CREATE TABLE IF NOT EXISTS server_backup_policy (
    policy_id INTEGER PRIMARY KEY CHECK (policy_id = 1),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    interval_hours INTEGER NOT NULL DEFAULT 24 CHECK (interval_hours BETWEEN 1 AND 168),
    retention_count INTEGER NOT NULL DEFAULT 14 CHECK (retention_count BETWEEN 1 AND 365),
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO server_backup_policy(policy_id) VALUES (1) ON CONFLICT (policy_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS deployment_promotion (
    promotion_id UUID PRIMARY KEY,
    source_fingerprint VARCHAR(128) NOT NULL,
    status VARCHAR(30) NOT NULL,
    manifest_json TEXT NOT NULL,
    started_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TEXT,
    started_by VARCHAR(120),
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_server_resource_type ON server_resource(resource_type);
CREATE INDEX IF NOT EXISTS idx_deployment_promotion_status ON deployment_promotion(status);
