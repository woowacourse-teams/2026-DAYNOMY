WITH duplicate_shares AS (
    SELECT id,
           FIRST_VALUE(id) OVER (
               PARTITION BY owner_member_id
               ORDER BY created_at DESC, id DESC
           ) AS retained_id
    FROM portfolio_analysis_shares
    WHERE analysis_id IS NULL
)
UPDATE portfolio_analysis_comments AS comment
SET share_id = duplicate.retained_id
FROM duplicate_shares AS duplicate
WHERE comment.share_id = duplicate.id
  AND duplicate.id <> duplicate.retained_id;

WITH duplicate_shares AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY owner_member_id
               ORDER BY created_at DESC, id DESC
           ) AS position
    FROM portfolio_analysis_shares
    WHERE analysis_id IS NULL
)
DELETE FROM portfolio_analysis_shares AS share
USING duplicate_shares AS duplicate
WHERE share.id = duplicate.id
  AND duplicate.position > 1;

CREATE UNIQUE INDEX uq_portfolio_analysis_shares_owner_without_analysis
    ON portfolio_analysis_shares(owner_member_id)
    WHERE analysis_id IS NULL;
