CREATE TABLE league_review_reminders (
    profile_id BIGINT PRIMARY KEY REFERENCES public_investor_profiles(id) ON DELETE CASCADE,
    participation_id BIGINT NOT NULL REFERENCES league_participations(id) ON DELETE CASCADE,
    request_key UUID,
    last_notified_at TIMESTAMP(6) WITH TIME ZONE,
    next_notification_at TIMESTAMP(6) WITH TIME ZONE,
    snoozed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
