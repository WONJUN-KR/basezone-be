# ballpark-be

캐치볼 장소 · 배팅장 지도 앱의 API 서버.

## 스택
- Java 21 · Spring Boot 4.1 (Web MVC · Data JPA · Validation · Actuator)
- PostgreSQL 17 · Flyway (스키마는 `src/main/resources/db/migration`)

## 로컬 실행
```
cp .env.example .env   # DB_PASSWORD 채우기
docker compose up -d
set -a; . ./.env; set +a; ./gradlew bootRun
curl localhost:8090/api/ping
```

## 데이터 원칙
- 카카오 로컬 API 결과(이름 · 주소 · 전화 · 좌표)는 저장하지 않음. 배팅장은 카카오 장소 ID 를 키로 우리 데이터만 저장
- 제보 · 체크인 좌표는 200m 확인 후 버리고 확인 결과와 시각만 저장
- 시각은 전부 UTC(`timestamptz`)

## 탈퇴 · 위치 동의 철회 처리 (서비스에서 한 함수로)
탈퇴
1. 살아 있는 체크인 `ended_at = now()` (2시간 동안 혼잡도에 남지 않게)
2. `users` 행 삭제 -> FK 가 처리: 제보 · 칸 정보 · 사진 · 체크인 · 등록한 장소 · 제안은 연결만 끊김(SET NULL), 즐겨찾기 · 기기 · 숨김 · 알림 설정 · 위치 확인자료 · 공개 통보 기록은 삭제(CASCADE)
3. 이용 제한 중이면 `user_sanctions` 에 식별값 해시만 남김

위치정보 이용 동의 철회 (위치정보법 제24조: 개인위치정보 · 확인자료 지체 없이 파기)
1. 그 사용자의 `location_use_logs` · `disclosure_notices` 삭제
2. `users.location_terms_agreed_at = null`, `nickname_public_at = null` (위치 동의 없이는 닉네임 공개 불가)
3. `alert_settings.area_cell = null`
4. `reports` · `checkins` · `box_facts` · `catchball_places.created_by` · 새 장소 제안의 사용자 연결 끊기 ('언제 어디 있었다'도 위치정보로 볼 수 있어서, 위치정보센터 확인 전까지 보수적으로)
5. 철회 안내 화면에 '내 제보 목록도 비워져요' 넣기
6. `location_terms_agreed_at` 이 null 이면 제보 · 체크인 · 장소 등록 · 알림 위치 저장 API 를 막음

## 보관 기간 배치 (약관 대조용)
| 대상 | 기간 | 처리 |
|---|---|---|
| `location_use_logs` | 6개월 | 삭제 |
| `disclosure_notices` | 6개월 | 삭제 |
| `admin_access_logs` | 1년 | 삭제 |
| `user_sanctions` | `ends_at` + 1년 | 삭제 |
| `checkins` | 2시간 만료 -> 끝나면 하루 안에 | `ended_at` 채움, `user_id = null` |
| `catchball_places.created_by` | 30일 | null |
| `place_edit_suggestions` (새 장소, `place_id IS NULL`) | 30일 | `user_id = null` |
| `content_reports` | 처리 후 1년 (약관 처리방침 3) | 삭제 |
| 접속 기록 (서버 로그) | 3개월 | 삭제 |
