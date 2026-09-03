-- Server-owned MFA policy. REQUIRED preserves the pre-9.0.58 non-Admin MFA behavior.
INSERT INTO application_setting(setting_key,setting_value,updated_at)
VALUES('security.auth.mfa.policy','REQUIRED',CURRENT_TIMESTAMP::text)
ON CONFLICT(setting_key) DO NOTHING;
