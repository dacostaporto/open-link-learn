CREATE TABLE app_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name TEXT NOT NULL DEFAULT 'Owner',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Etc/UTC',
    default_reminder_minutes_before INTEGER NOT NULL DEFAULT 10
        CHECK (default_reminder_minutes_before BETWEEN 0 AND 1440),
    default_assignment_cutoff_minutes_before INTEGER NOT NULL DEFAULT 30
        CHECK (default_assignment_cutoff_minutes_before BETWEEN 0 AND 10080),
    recurrence_horizon_days INTEGER NOT NULL DEFAULT 90
        CHECK (recurrence_horizon_days BETWEEN 7 AND 365),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE content_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_profile(id),
    url TEXT NOT NULL,
    normalized_url TEXT NOT NULL,
    title TEXT NOT NULL,
    content_type VARCHAR(16) NOT NULL
        CHECK (content_type IN ('YOUTUBE', 'ARTICLE')),
    thumbnail_url TEXT,
    source_name TEXT,
    estimated_duration_minutes INTEGER
        CHECK (estimated_duration_minutes IS NULL OR estimated_duration_minutes > 0),
    duration_seconds INTEGER
        CHECK (duration_seconds IS NULL OR duration_seconds > 0),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_content_owner_url UNIQUE (owner_id, normalized_url),
    CONSTRAINT ck_content_completed_time CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status = 'PENDING' AND completed_at IS NULL)
    )
);

CREATE INDEX idx_content_owner_status_created
    ON content_item(owner_id, status, created_at DESC);

CREATE INDEX idx_content_history_owner_completed
    ON content_item(owner_id, completed_at DESC, id DESC)
    WHERE status = 'COMPLETED';

CREATE TABLE learning_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_profile(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    content_item_id UUID REFERENCES content_item(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED'
        CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED', 'CANCELLED')),
    reminder_minutes_before INTEGER NOT NULL
        CHECK (reminder_minutes_before BETWEEN 0 AND 1440),
    assignment_cutoff_minutes_before INTEGER NOT NULL
        CHECK (assignment_cutoff_minutes_before BETWEEN 0 AND 10080),
    series_id UUID,
    series_occurrence_date DATE,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_learning_session_time_range CHECK (ends_at > starts_at),
    CONSTRAINT ck_learning_session_completed_time CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_learning_session_occurrence_identity CHECK (
        (series_id IS NULL AND series_occurrence_date IS NULL)
        OR (series_id IS NOT NULL AND series_occurrence_date IS NOT NULL)
    ),
    CONSTRAINT uq_learning_session_series_occurrence UNIQUE (series_id, series_occurrence_date)
);

CREATE UNIQUE INDEX uq_learning_session_active_content
    ON learning_session(content_item_id)
    WHERE content_item_id IS NOT NULL AND status IN ('PLANNED', 'IN_PROGRESS');

CREATE INDEX idx_learning_session_owner_start
    ON learning_session(owner_id, starts_at);

CREATE INDEX idx_learning_session_series_status_start
    ON learning_session(series_id, status, starts_at);

CREATE TABLE session_series (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_profile(id),
    start_date DATE NOT NULL,
    end_date DATE,
    start_time TIME NOT NULL,
    duration_minutes INTEGER NOT NULL
        CHECK (duration_minutes BETWEEN 1 AND 720),
    timezone VARCHAR(64) NOT NULL,
    days_of_week SMALLINT[] NOT NULL,
    reminder_minutes_before INTEGER
        CHECK (reminder_minutes_before IS NULL OR reminder_minutes_before BETWEEN 0 AND 1440),
    assignment_cutoff_minutes_before INTEGER
        CHECK (assignment_cutoff_minutes_before IS NULL OR assignment_cutoff_minutes_before BETWEEN 0 AND 10080),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_session_series_date_range CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_session_series_days_of_week CHECK (
        cardinality(days_of_week) BETWEEN 1 AND 7
        AND days_of_week <@ ARRAY[1, 2, 3, 4, 5, 6, 7]::SMALLINT[]
        AND cardinality(array_positions(days_of_week, 1::SMALLINT)) <= 1
        AND cardinality(array_positions(days_of_week, 2::SMALLINT)) <= 1
        AND cardinality(array_positions(days_of_week, 3::SMALLINT)) <= 1
        AND cardinality(array_positions(days_of_week, 4::SMALLINT)) <= 1
        AND cardinality(array_positions(days_of_week, 5::SMALLINT)) <= 1
        AND cardinality(array_positions(days_of_week, 6::SMALLINT)) <= 1
        AND cardinality(array_positions(days_of_week, 7::SMALLINT)) <= 1
    ),
    CONSTRAINT uq_session_series_id_owner UNIQUE (id, owner_id)
);

ALTER TABLE learning_session
    ADD CONSTRAINT fk_learning_session_series_owner
        FOREIGN KEY (series_id, owner_id) REFERENCES session_series(id, owner_id) ON DELETE RESTRICT;

CREATE TABLE reminder (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_profile(id),
    session_id UUID NOT NULL REFERENCES learning_session(id),
    kind VARCHAR(24) NOT NULL
        CHECK (kind IN ('REMINDER', 'ASSIGNMENT_CUTOFF')),
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'CANCELLED')),
    attempt_count INTEGER NOT NULL DEFAULT 0
        CHECK (attempt_count BETWEEN 0 AND 10),
    next_attempt_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    processing_started_at TIMESTAMPTZ,
    claim_token UUID,
    outcome VARCHAR(32),
    last_error VARCHAR(500),
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_reminder_processing_lease
        CHECK (
            (status = 'PROCESSING' AND processing_started_at IS NOT NULL AND claim_token IS NOT NULL)
            OR (status <> 'PROCESSING' AND processing_started_at IS NULL AND claim_token IS NULL)
        ),
    CONSTRAINT ck_reminder_assignment_outcome
        CHECK (outcome IS NULL OR outcome IN ('ASSIGNED', 'NO_ELIGIBLE_CONTENT'))
);

CREATE INDEX idx_reminder_due_pending
    ON reminder(scheduled_at, id)
    WHERE status = 'PENDING';

CREATE INDEX idx_reminder_due_retry
    ON reminder(next_attempt_at, id)
    WHERE status = 'FAILED';

CREATE UNIQUE INDEX uq_reminder_pending_assignment_cutoff
    ON reminder(session_id)
    WHERE kind = 'ASSIGNMENT_CUTOFF' AND status = 'PENDING';

CREATE INDEX idx_reminder_stale_processing_by_kind
    ON reminder(kind, processing_started_at, id)
    WHERE status = 'PROCESSING';

CREATE TABLE push_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES app_profile(id),
    endpoint TEXT NOT NULL UNIQUE,
    p256dh_key TEXT NOT NULL,
    auth_key TEXT NOT NULL,
    user_agent VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_push_subscription_owner_active_last_seen
    ON push_subscription(owner_id, active, last_seen_at DESC);

-- V1 Global default user

INSERT INTO app_profile (id, display_name, timezone)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Owner',
    'Etc/UTC'
)
ON CONFLICT (id) DO NOTHING;