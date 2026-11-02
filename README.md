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
