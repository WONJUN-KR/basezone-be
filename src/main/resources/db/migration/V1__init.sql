-- 첫 스키마 (데이터 모델 v3 + 운영자 접근 기록)
-- 원칙
--  · 카카오 검색 결과(배팅장 이름 · 주소 · 전화 · 좌표)는 저장하지 않음. 배팅장은 kakao_place_id 만
--  · 제보 · 체크인 좌표는 서버가 200m 확인 후 버림. 확인 시각만 저장
--  · 시각은 전부 timestamptz(UTC)
--  · 상태값은 PostgreSQL enum 대신 varchar + CHECK (값 추가가 쉬움)

-- ---------- 사용자 ----------
CREATE TABLE users (
    id                         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auth_provider              varchar(10)  NOT NULL CHECK (auth_provider IN ('kakao', 'apple')),
    auth_subject               varchar(255) NOT NULL,
    nickname                   varchar(12)  NOT NULL UNIQUE,
    interests                  text[]       NOT NULL DEFAULT '{}',
    bat_side                   varchar(5)   CHECK (bat_side IN ('left', 'right', 'both')),
    team                       varchar(20),
    -- 제보에 닉네임 보이기 동의 시각. 철회하면 null. 동의 뒤 올린 제보만 닉네임 표시
    nickname_public_at         timestamptz,
    -- 공개 알림: 바로(기본) 또는 10일마다 모아서(따로 동의, 위치정보법 시행령 제24조)
    disclosure_notice          varchar(15)  NOT NULL DEFAULT 'instant' CHECK (disclosure_notice IN ('instant', 'every_10_days')),
    disclosure_batch_agreed_at timestamptz,
    -- 위치기반서비스 이용약관 동의 시각. 철회하면 확인자료를 지움
    location_terms_agreed_at   timestamptz,
    -- 혜택 · 광고 알림 동의 시각 (날씨 알림과 별개 동의, 2년마다 확인 고지)
    marketing_opt_in_at        timestamptz,
    created_at                 timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (auth_provider, auth_subject),
    CHECK (disclosure_notice = 'instant' OR disclosure_batch_agreed_at IS NOT NULL)
);

CREATE TABLE user_devices (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform    varchar(10) NOT NULL CHECK (platform IN ('ios', 'android')),
    push_token  varchar(512),
    app_version varchar(20),
    os_version  varchar(20),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_devices_user ON user_devices (user_id);

-- 숨긴 사람
CREATE TABLE user_blocks (
    user_id         bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_user_id bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, blocked_user_id),
    CHECK (user_id <> blocked_user_id)
);

-- 날씨 알림
CREATE TABLE alert_settings (
    user_id             bigint     PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    enabled             boolean    NOT NULL DEFAULT false,
    days                smallint[] NOT NULL DEFAULT '{}',
    time_from           time,
    time_to             time,
    conditions          jsonb      NOT NULL DEFAULT '{}',
    radius_km           smallint   NOT NULL DEFAULT 3,
    -- 기기에서 geohash 6자리(약 1km)로 바꿔 보낸 칸. 정확한 좌표는 받지 않음. 알림 끄면 null
    area_cell           varchar(6)
);

-- ---------- 장소 ----------
CREATE TABLE places (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind           varchar(10) NOT NULL CHECK (kind IN ('catchball', 'batting')),
    -- 배팅장만. 카카오 데이터 중 저장해도 되는 건 장소 ID 뿐
    kakao_place_id varchar(20) UNIQUE,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CHECK ((kind = 'batting') = (kakao_place_id IS NOT NULL))
);

CREATE TABLE catchball_places (
    place_id       bigint       PRIMARY KEY REFERENCES places (id) ON DELETE CASCADE,
    name           varchar(40)  NOT NULL,
    -- 현장 휴대폰 GPS. 지도 위에서 찍은 좌표는 받지 않음. 주소는 저장하지 않고 화면에서 변환
    lat            numeric(9,6) NOT NULL,
    lng            numeric(9,6) NOT NULL,
    gps_accuracy_m smallint     NOT NULL,
    surface        varchar(10)  CHECK (surface IN ('grass', 'dirt', 'court', 'etc')),
    -- 신고 처리 기간(30일) 지나면 배치로 null
    created_by     bigint       REFERENCES users (id) ON DELETE SET NULL,
    status         varchar(10)  NOT NULL DEFAULT 'active' CHECK (status IN ('pending', 'active', 'hidden')),
    created_at     timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_catchball_places_latlng ON catchball_places (lat, lng);

-- 배팅장 칸
CREATE TABLE batting_boxes (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id   bigint      NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    label      varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (place_id, label)
);

-- 칸 정보: 값을 덮어쓰지 않고 '누가 언제 무엇을 봤다'를 쌓음. 화면 값 = 최근 90일 최다 값 + 확인한 사람 수
CREATE TABLE box_facts (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    box_id     bigint      NOT NULL REFERENCES batting_boxes (id) ON DELETE CASCADE,
    field      varchar(20) NOT NULL CHECK (field IN ('hand', 'speed_kmh', 'price', 'balls_per_play', 'machine_type')),
    value      jsonb       NOT NULL,
    user_id    bigint      REFERENCES users (id) ON DELETE SET NULL,
    on_site    boolean     NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_box_facts_box_field ON box_facts (box_id, field, created_at DESC);

CREATE TABLE place_photos (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id    bigint       NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    box_id      bigint       REFERENCES batting_boxes (id) ON DELETE CASCADE,
    user_id     bigint       REFERENCES users (id) ON DELETE SET NULL,
    -- 오브젝트 스토리지 경로. EXIF(촬영 위치 등)는 지우고 저장
    storage_key varchar(255) NOT NULL,
    status      varchar(10)  NOT NULL DEFAULT 'visible' CHECK (status IN ('visible', 'hidden')),
    created_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_place_photos_place ON place_photos (place_id, created_at DESC);

-- 장소 등록 · 정보 수정 제안. 새 장소 제안(GPS 포함)은 30일 뒤 user_id 를 null
CREATE TABLE place_edit_suggestions (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id    bigint      REFERENCES places (id) ON DELETE CASCADE,
    user_id     bigint      REFERENCES users (id) ON DELETE SET NULL,
    payload     jsonb       NOT NULL,
    status      varchar(10) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'accepted', 'rejected')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz
);

-- ---------- 제보 · 체크인 ----------
-- 같은 (user_id, place_id) 30분에 한 번은 서비스에서 확인
CREATE TABLE reports (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id             bigint       NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    user_id              bigint       REFERENCES users (id) ON DELETE SET NULL,
    verdict              varchar(10)  CHECK (verdict IN ('safe', 'review', 'out')),
    crowd                smallint     CHECK (crowd BETWEEN 1 AND 3),
    note                 varchar(200),
    -- 200m 확인 시각. 좌표 칸 없음
    location_verified_at timestamptz  NOT NULL,
    -- 사용자 보정 무게(첫 3건 0.5 등). 시간 감쇠(7일마다 절반)는 조회할 때 계산
    weight               numeric(3,2) NOT NULL DEFAULT 1.00,
    status               varchar(10)  NOT NULL DEFAULT 'visible' CHECK (status IN ('visible', 'hidden', 'deleted')),
    created_at           timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_reports_place_created ON reports (place_id, created_at DESC);
CREATE INDEX idx_reports_user_place ON reports (user_id, place_id, created_at DESC);

-- 지금 이용 중. 계정당 살아 있는 1개, 2시간 뒤 만료 배치가 ended_at 채움. 끝나면 user_id 를 null
CREATE TABLE checkins (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    place_id             bigint      NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    user_id              bigint      REFERENCES users (id) ON DELETE SET NULL,
    party_size           smallint    CHECK (party_size > 0),
    location_verified_at timestamptz NOT NULL,
    expires_at           timestamptz NOT NULL,
    ended_at             timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_checkins_active_user ON checkins (user_id) WHERE ended_at IS NULL;
CREATE INDEX idx_checkins_place_active ON checkins (place_id) WHERE ended_at IS NULL;

-- 닉네임 공개 통보 기록. 6개월 뒤 삭제, 탈퇴 · 위치 동의 철회 시 바로 삭제
CREATE TABLE disclosure_notices (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mode         varchar(15) NOT NULL CHECK (mode IN ('instant', 'every_10_days')),
    report_id    bigint      REFERENCES reports (id) ON DELETE SET NULL,
    period_from  timestamptz,
    period_to    timestamptz,
    report_count int         NOT NULL,
    sent_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_disclosure_notices_sent ON disclosure_notices (sent_at);

-- 위치정보 이용 · 제공 사실 확인자료(위치정보 취급대장). 좌표 없음
-- 6개월 뒤 삭제(위치정보법 제16조 제2항), 탈퇴 · 위치 동의 철회 시 바로 삭제(제24조)
CREATE TABLE location_use_logs (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    varchar(20) NOT NULL CHECK (purpose IN ('report_verify', 'checkin_verify', 'place_register', 'alert_area', 'provide_nickname')),
    place_id   bigint      REFERENCES places (id) ON DELETE SET NULL,
    result     varchar(10) NOT NULL CHECK (result IN ('ok', 'too_far')),
    created_at timestamptz NOT NULL DEFAULT now(),
    -- 거리 확인을 하는 목적만 too_far 가 있을 수 있음
    CHECK (result = 'ok' OR purpose IN ('report_verify', 'checkin_verify'))
);
CREATE INDEX idx_location_use_logs_user ON location_use_logs (user_id, created_at DESC);
CREATE INDEX idx_location_use_logs_created ON location_use_logs (created_at);

-- ---------- 즐겨찾기 · 신고 ----------
CREATE TABLE favorites (
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    place_id   bigint      NOT NULL REFERENCES places (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, place_id)
);

-- 신고. 24시간 안에 검토
CREATE TABLE content_reports (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id bigint       REFERENCES users (id) ON DELETE SET NULL,
    target_type varchar(10)  NOT NULL CHECK (target_type IN ('report', 'photo', 'place', 'nickname')),
    target_id   bigint       NOT NULL,
    reason      varchar(15)  NOT NULL CHECK (reason IN ('false_info', 'abuse', 'ad_spam', 'privacy', 'etc')),
    detail      varchar(500),
    status      varchar(10)  NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'accepted', 'rejected')),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    resolved_at timestamptz
);
CREATE INDEX idx_content_reports_open ON content_reports (created_at) WHERE status = 'open';

-- 이용 제한. 탈퇴 후 재가입 막기용이라 식별값은 해시로. 끝난 뒤 1년 지나면 삭제
CREATE TABLE user_sanctions (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auth_provider     varchar(10)  NOT NULL,
    auth_subject_hash varchar(64)  NOT NULL,
    reason            varchar(200) NOT NULL,
    starts_at         timestamptz  NOT NULL DEFAULT now(),
    ends_at           timestamptz
);
CREATE INDEX idx_user_sanctions_subject ON user_sanctions (auth_provider, auth_subject_hash);

-- 탈퇴 사유(통계용, 사용자와 연결 없음, 날짜까지만)
CREATE TABLE withdrawal_reasons (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reason     varchar(20) NOT NULL,
    created_on date        NOT NULL DEFAULT current_date
);

-- ---------- 운영 ----------
-- 운영자가 위치 관련 데이터 · 관리자 화면에 접근한 기록. 1년 이상 보관(위치정보 보호조치 기준 제10조 제2항)
-- 운영자 기록이라 사용자 탈퇴와 상관없이 남김
CREATE TABLE admin_access_logs (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_id   varchar(50)  NOT NULL,
    action     varchar(30)  NOT NULL,
    target     varchar(100) NOT NULL,
    ip         inet,
    created_at timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_admin_access_logs_created ON admin_access_logs (created_at);
