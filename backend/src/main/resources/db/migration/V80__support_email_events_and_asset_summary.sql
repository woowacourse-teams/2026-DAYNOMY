ALTER TABLE email_deliveries ADD COLUMN supplement_digest JSONB;
ALTER TABLE email_deliveries DROP CONSTRAINT email_deliveries_analysis_ids_check;
ALTER TABLE email_deliveries ADD CONSTRAINT email_deliveries_analysis_ids_check
    CHECK (jsonb_typeof(analysis_ids) = 'array' AND jsonb_array_length(analysis_ids) BETWEEN 0 AND 3);
