-- 로그인 (11/7): 구글 · 카카오 · 애플
--  · 앱이 각 SDK 로 받은 토큰을 서버가 직접 확인하고 우리 토큰을 발급
--  · 갱신 토큰은 원문 대신 SHA-256 해시만 저장. 로그아웃 · 탈퇴하면 삭제
ALTER TABLE users DROP CONSTRAINT users_auth_provider_check;
ALTER TABLE users ADD CONSTRAINT users_auth_provider_check CHECK (auth_provider IN ('kakao', 'apple', 'google'));

-- 서비스 이용약관 · 개인정보 처리방침 동의 시각 (가입할 때 필수)
ALTER TABLE users ADD COLUMN terms_agreed_at timestamptz;

CREATE TABLE refresh_tokens (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash char(64)    NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);
