ALTER TABLE members ADD COLUMN google_name VARCHAR(255);

COMMENT ON COLUMN members.google_name IS 'Google 계정 이름. 본인 계정 응답에서만 제공하며 공개 닉네임으로 사용하지 않는다.';
