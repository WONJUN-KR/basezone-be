package kr.pf.ballpark.place;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// 캐치볼 장소 핀 등록 남용 방지 (11/7, 숫자는 가정)
// - 근처 50m 안에 장소가 있거나 지번이 같으면 등록 대신 '이 장소 맞나요?'
// - 한 계정당 24시간에 3곳, 가입 후 24시간 안엔 1곳
// - 현장 확인 안 된 장소는 신고 2건, 확인된 장소는 3건이면 숨김
public final class PlaceRegisterGuard {

    static final double NEARBY_METERS = 50;
    static final Duration DAILY_WINDOW = Duration.ofHours(24);
    static final int DAILY_LIMIT = 3;
    static final Duration NEW_ACCOUNT_WINDOW = Duration.ofHours(24);
    static final int NEW_ACCOUNT_LIMIT = 1;
    static final int HIDE_REPORTS_UNVERIFIED = 2;
    static final int HIDE_REPORTS_VERIFIED = 3;

    private static final double EARTH_RADIUS_M = 6_371_000;

    private PlaceRegisterGuard() {
    }

    public enum Decision {
        ALLOWED, DUPLICATE, DAILY_LIMIT, NEW_ACCOUNT_LIMIT
    }

    // lotKey: 지번 비교용 (lotKey() 로 만듦, 본번이 없으면 null)
    public record Spot(Long placeId, double lat, double lng, String lotKey) {
    }

    // duplicates: '이 장소 맞나요?' 에 보여줄 장소 (가까운 순)
    public record Result(Decision decision, List<Long> duplicates) {
    }

    // myRegistrations: 이 사용자가 등록한 장소들의 등록 시각. nearby: 서버가 범위 조회로 뽑은 근처 장소
    public static Result check(Spot newSpot, List<Spot> nearby, Instant userCreatedAt, List<Instant> myRegistrations, Instant now) {
        // 중복을 먼저 보여줌 (한도에 걸린 사람도 기존 장소로 제보할 수 있게)
        List<Long> dups = nearby.stream()
                .filter(s -> distanceMeters(newSpot, s) <= NEARBY_METERS
                        || (newSpot.lotKey() != null && newSpot.lotKey().equals(s.lotKey())))
                .sorted((a, b) -> Double.compare(distanceMeters(newSpot, a), distanceMeters(newSpot, b)))
                .map(Spot::placeId)
                .toList();
        if (!dups.isEmpty()) {
            return new Result(Decision.DUPLICATE, dups);
        }

        long recent = myRegistrations.stream().filter(t -> t.isAfter(now.minus(DAILY_WINDOW))).count();
        if (userCreatedAt.isAfter(now.minus(NEW_ACCOUNT_WINDOW)) && recent >= NEW_ACCOUNT_LIMIT) {
            return new Result(Decision.NEW_ACCOUNT_LIMIT, List.of());
        }
        if (recent >= DAILY_LIMIT) {
            return new Result(Decision.DAILY_LIMIT, List.of());
        }
        return new Result(Decision.ALLOWED, List.of());
    }

    // 받은 신고(처리 안 된 것 + 받아들인 것) 수로 숨길지
    public static boolean shouldHide(boolean verified, int reportCount) {
        return reportCount >= (verified ? HIDE_REPORTS_VERIFIED : HIDE_REPORTS_UNVERIFIED);
    }

    // 지번 키: 시도 · 구 · 동 · 본번 · 부번. 본번이 없으면 비교 안 함
    public static String lotKey(String region1, String region2, String region3, String mainNo, String subNo) {
        if (mainNo == null || mainNo.isBlank()) {
            return null;
        }
        return String.join("|", region1, region2, region3, mainNo, subNo == null ? "" : subNo);
    }

    static double distanceMeters(Spot a, Spot b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }
}
