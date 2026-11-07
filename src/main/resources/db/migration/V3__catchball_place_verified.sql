-- 핀 등록 남용 방지 (11/7): 현장 확인 시각
--  · 핀으로 등록한 장소는 '새 장소'로 바로 보이고, 누가 현장(200m 안)에서 제보하면 확인됨
--  · 확인 안 된 장소는 신고 2건, 확인된 장소는 3건이면 숨김 (PlaceRegisterGuard)
ALTER TABLE catchball_places ADD COLUMN verified_at timestamptz;
-- 현장 GPS 로 등록한 장소는 등록 순간 확인된 것으로 봄
UPDATE catchball_places SET verified_at = created_at WHERE location_source = 'gps';
