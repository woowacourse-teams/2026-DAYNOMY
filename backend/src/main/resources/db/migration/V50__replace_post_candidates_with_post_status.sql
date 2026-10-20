ALTER TABLE featured_person_posts
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN reviewed_at TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX idx_featured_person_posts_status_created
    ON featured_person_posts (status, created_at DESC, id DESC);

DROP TABLE featured_person_post_candidates;
