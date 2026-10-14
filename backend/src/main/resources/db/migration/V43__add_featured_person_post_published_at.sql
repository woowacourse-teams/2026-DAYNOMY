ALTER TABLE featured_person_posts
    ADD COLUMN published_at TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX idx_featured_person_posts_person_published
    ON featured_person_posts (person_id, published_at DESC, created_at DESC, id DESC);
