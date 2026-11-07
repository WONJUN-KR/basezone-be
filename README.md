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

## 로그인 (구글 · 카카오 · 애플)
- 앱이 각 SDK 로 받은 토큰을 `POST /api/auth/login` {provider, token} 으로 보냄. 카카오 = 액세스 토큰(앱 ID 확인), 구글 · 애플 = id_token(공개키 서명 · 발급자 · 클라이언트 ID 확인)
- 가입한 사람이면 우리 토큰, 처음이면 `signup_required` + 가입 토큰(10분) -> `POST /api/auth/signup` {signupToken, nickname, termsAgreed, locationTermsAgreed, marketingOptIn}
- 접근 토큰 1시간(`Authorization: Bearer`), 갱신 토큰 30일(`/api/auth/refresh` 할 때마다 새것으로, DB 에는 해시만), `/api/auth/logout` 은 갱신 토큰 삭제
- 환경 변수: `JWT_SECRET`(32바이트 이상) · `KAKAO_APP_ID` · `GOOGLE_CLIENT_IDS` · `APPLE_CLIENT_IDS`. 비어 있는 로그인은 503
- 로컬 전용 `AUTH_FAKE_SOCIAL=true`: 소셜 토큰 확인 없이 토큰 문자열을 식별값으로 씀. 운영에서 켜지 말 것

## 데이터 원칙
- 카카오 데이터 저장 허용(11/7, V2): 배팅장은 카카오 검색 결과(이름 · 주소 · 전화 · 좌표 · 카테고리)를 `batting_places` 에 저장, 캐치볼 장소는 지도 핀 좌표 + 카카오 좌표 -> 주소 결과(주소 · 시도 · 구 · 동)를 저장. 카카오 답변 원문은 없어서 수익화할 때 다시 확인
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
| `refresh_tokens` | 만료(30일) 뒤 매일 04:30 | 삭제 (`RefreshTokenCleanup`) |
