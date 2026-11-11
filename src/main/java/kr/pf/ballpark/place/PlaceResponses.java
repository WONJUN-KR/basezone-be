package kr.pf.ballpark.place;

import java.time.Instant;
import java.util.List;

public final class PlaceResponses {

    private PlaceResponses() {
    }

    // 홈 지도 · 목록의 캐치볼 장소 한 개 (디자인 '화면별 데이터' 홈 지도 F10 · 목록 F32)
    public record CatchballSummary(
            long id,
            String name,
            double lat,
            double lng,
            Verdict verdict,
            int judgeCount,
            Instant lastReportedAt,
            boolean needsRefresh,
            Crowd crowd) {
    }

    // 지도 범위 조회 결과. 장소가 최대 개수를 넘으면 잘라서 주고 truncated = true
    public record CatchballPage(List<CatchballSummary> places, boolean truncated) {
    }

    public record Crowd(int level, CrowdCalculator.Source source) {
    }

    // 배팅장은 우리 데이터만. leftyBoxes 는 왼손 여부가 알려진 칸이 하나도 없으면 null
    public record BattingSummary(String kakaoPlaceId, long placeId, Integer leftyBoxes, int knownBoxes) {
    }
}
