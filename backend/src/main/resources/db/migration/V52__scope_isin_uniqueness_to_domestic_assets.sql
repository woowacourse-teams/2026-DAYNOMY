-- EODHD can assign one ISIN to multiple US ticker rows (including symbol changes).
-- US securities are identified by (category, asset_code); retain their ISIN as source metadata.
DROP INDEX uk_assets_isin_code;

CREATE UNIQUE INDEX uk_assets_isin_code
    ON assets (isin_code)
    WHERE isin_code IS NOT NULL
      AND (market IS NULL OR market IN ('KOSPI', 'KOSDAQ'));
