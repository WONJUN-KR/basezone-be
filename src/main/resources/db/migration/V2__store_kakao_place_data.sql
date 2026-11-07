-- 카카오 데이터 저장 허용 반영 (11/7 사용자 결정, 카카오 답변 원문 미첨부 - 수익화할 때 다시 확인)
--  · 배팅장: 카카오 키워드 검색 결과(이름 · 주소 · 전화 · 좌표 · 카테고리)를 저장
--  · 캐치볼 장소: 지도 가운데 핀 좌표 + 서버가 카카오 좌표 -> 주소로 받은 주소 · 지역을 저장 (현장 GPS 필수 아님)
--  · V1 에서 대체되는 곳 (V1 은 체크섬 때문에 그대로 둠)
--      - V1 헤더 '카카오 검색 결과 저장하지 않음, kakao_place_id 만' -> 배팅장은 batting_places 에 저장
--      - places.kakao_place_id 주석 '저장해도 되는 건 장소 ID 뿐' -> 장소 ID 는 연결 키로 계속 씀
--      - catchball_places 주석 '지도 위에서 찍은 좌표는 받지 않음, 주소는 저장하지 않음' -> 핀 좌표 · 주소 저장

-- ---------- 배팅장 정보 ----------
-- place_id 는 kind = 'batting' 인 places 만 (DB 로는 못 막아서 서비스 코드에서 보장, catchball_places 와 같은 방식)
-- region1~3 은 키워드 검색 응답에 없어서 좌표 -> 지역 코드 변환을 한 번 더 불러 채움
CREATE TABLE batting_places (
    place_id     bigint       PRIMARY KEY REFERENCES places (id) ON DELETE CASCADE,
    name         varchar(100) NOT NULL,
    category     varchar(100),
    phone        varchar(30),
    address      varchar(200),
    road_address varchar(200),
    lat          numeric(9,6) NOT NULL,
    lng          numeric(9,6) NOT NULL,
    region1      varchar(50),
    region2      varchar(50),
    region3      varchar(100),
    place_url    varchar(200),
    -- 카카오에서 마지막으로 받은 시각. 다시 검색할 때 바뀐 값이 있으면 덮어씀
    synced_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_batting_places_latlng ON batting_places (lat, lng);

-- ---------- 캐치볼 장소: 핀 좌표 + 주소 ----------
-- gps_accuracy_m 은 현장 GPS 로 등록했을 때만 채움
ALTER TABLE catchball_places ALTER COLUMN gps_accuracy_m DROP NOT NULL;
ALTER TABLE catchball_places
    ADD COLUMN location_source varchar(10) NOT NULL DEFAULT 'pin' CHECK (location_source IN ('pin', 'gps')),
    ADD COLUMN address         varchar(200),
    ADD COLUMN region1         varchar(50),
    ADD COLUMN region2         varchar(50),
    ADD COLUMN region3         varchar(100),
    -- 지번 본번 · 부번 (GOTCHA 와 같음, 같은 주소 중복 확인용)
    ADD COLUMN main_address_no varchar(10),
    ADD COLUMN sub_address_no  varchar(10);
-- V1 에서 들어간 행은 전부 현장 GPS
UPDATE catchball_places SET location_source = 'gps' WHERE gps_accuracy_m IS NOT NULL;
-- 현장 GPS 로 등록했으면 정확도는 필수
ALTER TABLE catchball_places
    ADD CONSTRAINT chk_catchball_places_gps_accuracy CHECK (location_source = 'pin' OR gps_accuracy_m IS NOT NULL);
