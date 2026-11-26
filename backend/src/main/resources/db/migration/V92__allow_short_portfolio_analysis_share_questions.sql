ALTER TABLE portfolio_analysis_shares
    DROP CONSTRAINT IF EXISTS portfolio_analysis_shares_question_check;

ALTER TABLE portfolio_analysis_shares
    ADD CONSTRAINT portfolio_analysis_shares_question_check
    CHECK (char_length(question) BETWEEN 1 AND 200);
