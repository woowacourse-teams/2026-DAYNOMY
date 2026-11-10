ALTER TABLE feedbacks
    ADD COLUMN category VARCHAR(30),
    ADD COLUMN author_member_id BIGINT,
    ADD COLUMN author_nickname VARCHAR(20);

ALTER TABLE feedbacks
    ADD CONSTRAINT fk_feedbacks_author_member
        FOREIGN KEY (author_member_id) REFERENCES members (id) ON DELETE SET NULL;
