CREATE INDEX CONCURRENTLY idx_investment_reviews_transaction
    ON investment_reviews (transaction_id);

CREATE INDEX CONCURRENTLY idx_investor_profiles_nonpublic
    ON public_investor_profiles (id)
    WHERE NOT profile_public OR NOT league_enabled;
