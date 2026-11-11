package kr.pf.ballpark.place;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

// 지도 조회용 읽기 쿼리. 화면에 필요한 칸만 골라 읽어서 엔티티 대신 JdbcClient 로
@Repository
@RequiredArgsConstructor
public class PlaceQueryRepository {

    private final JdbcClient jdbc;

    public record CatchballRow(long placeId, String name, BigDecimal lat, BigDecimal lng) {
    }

    public record ReportRow(long reportId, long placeId, Long userId, String verdict, Integer crowd, BigDecimal weight,
            Instant createdAt) {
    }

    public record CheckinPeopleRow(long placeId, int people) {
    }

    public record BattingRow(long placeId, String kakaoPlaceId, int knownBoxes, int leftyBoxes) {
    }

    public List<CatchballRow> findCatchballInBounds(double swLat, double swLng, double neLat, double neLng, int limit) {
        return jdbc.sql("""
                SELECT place_id, name, lat, lng
                FROM catchball_places
                WHERE status = 'active'
                  AND lat BETWEEN :swLat AND :neLat
                  AND lng BETWEEN :swLng AND :neLng
                ORDER BY place_id
                LIMIT :limit
                """)
                .param("swLat", swLat).param("neLat", neLat)
                .param("swLng", swLng).param("neLng", neLng)
                .param("limit", limit)
                .query(CatchballRow.class)
                .list();
    }

    // 판정(최근 30일)과 혼잡도(최근 90분) 계산에 쓰는 제보. 숨김 · 삭제된 제보는 빼고
    public List<ReportRow> findVisibleReportsSince(Collection<Long> placeIds, Instant since) {
        if (placeIds.isEmpty()) return List.of();
        return jdbc.sql("""
                SELECT id AS report_id, place_id, user_id, verdict, crowd, weight, created_at
                FROM reports
                WHERE place_id IN (:placeIds)
                  AND status = 'visible'
                  AND created_at > :since
                """)
                .param("placeIds", placeIds)
                .param("since", Timestamp.from(since))
                .query((rs, i) -> new ReportRow(
                        rs.getLong("report_id"),
                        rs.getLong("place_id"),
                        rs.getObject("user_id", Long.class),
                        rs.getString("verdict"),
                        rs.getObject("crowd", Integer.class),
                        rs.getBigDecimal("weight"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    // 지금 이용 중 인원 합 (party_size 없으면 1명)
    public List<CheckinPeopleRow> sumActiveCheckinPeople(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) return List.of();
        return jdbc.sql("""
                SELECT place_id, SUM(COALESCE(party_size, 1))::int AS people
                FROM checkins
                WHERE place_id IN (:placeIds)
                  AND ended_at IS NULL
                  AND expires_at > now()
                GROUP BY place_id
                """)
                .param("placeIds", placeIds)
                .query(CheckinPeopleRow.class)
                .list();
    }

    // 기기가 카카오에서 찾은 배팅장 ID 로 우리 데이터만 돌려줌 (이름 · 주소 · 좌표는 서버에 없음)
    // 칸마다 최근 90일 'hand' 제보 중 가장 많은 값을 그 칸의 값으로 봄. left · both 면 왼손 타석
    // 값은 left · right · both 만 셈 (그 밖의 값이 이기면 알려진 칸으로 잘못 세어 왼손 0 이 되므로)
    public List<BattingRow> findBattingByKakaoIds(Collection<String> kakaoPlaceIds) {
        if (kakaoPlaceIds.isEmpty()) return List.of();
        return jdbc.sql("""
                WITH votes AS (
                    SELECT b.place_id, f.box_id, f.value ->> 'hand' AS hand, COUNT(*) AS cnt, MAX(f.created_at) AS last_at
                    FROM box_facts f
                    JOIN batting_boxes b ON b.id = f.box_id
                    JOIN places p ON p.id = b.place_id
                    WHERE p.kakao_place_id IN (:ids)
                      AND f.field = 'hand'
                      AND f.value ->> 'hand' IN ('left', 'right', 'both')
                      AND f.created_at > now() - interval '90 days'
                    GROUP BY b.place_id, f.box_id, f.value ->> 'hand'
                ),
                top AS (
                    SELECT DISTINCT ON (box_id) place_id, box_id, hand
                    FROM votes
                    ORDER BY box_id, cnt DESC, last_at DESC
                )
                SELECT p.id AS place_id, p.kakao_place_id,
                       COUNT(t.box_id)::int AS known_boxes,
                       (COUNT(t.box_id) FILTER (WHERE t.hand IN ('left', 'both')))::int AS lefty_boxes
                FROM places p
                LEFT JOIN top t ON t.place_id = p.id
                WHERE p.kakao_place_id IN (:ids)
                GROUP BY p.id, p.kakao_place_id
                """)
                .param("ids", kakaoPlaceIds)
                .query(BattingRow.class)
                .list();
    }
}
