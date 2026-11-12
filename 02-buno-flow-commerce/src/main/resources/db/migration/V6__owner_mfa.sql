-- OWNER second-factor authentication. Kept behind COMMERCE_OWNER_MFA_REQUIRED until QA approved.
-- Existing sessions are deliberately NOT MFA verified; enabling MFA rejects their OWNER access.
ALTER TABLE flow_commerce.user_sessions ADD COLUMN IF NOT EXISTS mfa_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS flow_commerce.owner_mfa (
  user_id UUID PRIMARY KEY REFERENCES flow_commerce.users(id) ON DELETE CASCADE,
  secret_ciphertext TEXT NOT NULL,
  enabled_at TIMESTAMPTZ,
  last_used_step BIGINT NOT NULL DEFAULT -1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS flow_commerce.owner_mfa_challenges (
  token_hash CHAR(64) PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES flow_commerce.users(id) ON DELETE CASCADE,
  store_id UUID NOT NULL REFERENCES flow_commerce.stores(id) ON DELETE CASCADE,
  purpose VARCHAR(12) NOT NULL CHECK(purpose IN ('ENROLL','LOGIN')),
  expires_at TIMESTAMPTZ NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 5),
  consumed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS ix_owner_mfa_challenges_user ON flow_commerce.owner_mfa_challenges(user_id,expires_at);

CREATE TABLE IF NOT EXISTS flow_commerce.owner_mfa_recovery_codes (
  user_id UUID NOT NULL REFERENCES flow_commerce.users(id) ON DELETE CASCADE,
  code_hash CHAR(64) NOT NULL,
  used_at TIMESTAMPTZ,
  PRIMARY KEY(user_id,code_hash)
);
